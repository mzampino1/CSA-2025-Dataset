package eu.siacs.conversations.http;

import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.entities.TransferablePlaceholder;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;

public class HttpDownloadConnection implements Transferable {

    private HttpConnectionManager mHttpConnectionManager;
    private XmppConnectionService mXmppConnectionService;

    private URL mUrl;
    private Message message;
    private DownloadableFile file;
    private int mStatus = Transferable.STATUS_UNKNOWN;
    private boolean acceptedAutomatically = false;
    private int mProgress = 0;

    public HttpDownloadConnection(HttpConnectionManager manager) {
        this.mHttpConnectionManager = manager;
        this.mXmppConnectionService = manager.getXmppConnectionService();
    }

    @Override
    public boolean start() {
        if (mXmppConnectionService.hasInternetConnection()) {
            if (this.mStatus == STATUS_OFFER_CHECK_FILESIZE) {
                checkFileSize(true);
            } else {
                new Thread(new FileDownloader(true)).start();
            }
            return true;
        } else {
            return false;
        }
    }

    public void init(Message message) {
        init(message, false);
    }

    public void init(Message message, boolean interactive) {
        this.message = message;
        this.message.setTransferable(this);
        try {
            if (message.hasFileOnRemoteHost()) {
                mUrl = message.getFileParams().url;
            } else {
                mUrl = new URL(message.getBody());
            }
            String[] parts = mUrl.getPath().toLowerCase().split("\\.");
            String lastPart = parts.length >= 1 ? parts[parts.length - 1] : null;
            String secondToLast = parts.length >= 2 ? parts[parts.length -2] : null;
            if ("pgp".equals(lastPart) || "gpg".equals(lastPart)) {
                this.message.setEncryption(Message.ENCRYPTION_PGP);
            } else if (message.getEncryption() != Message.ENCRYPTION_OTR
                    && message.getEncryption() != Message.ENCRYPTION_AXOLOTL) {
                this.message.setEncryption(Message.ENCRYPTION_NONE);
            }
            String extension;
            if (Arrays.asList(VALID_CRYPTO_EXTENSIONS).contains(lastPart)) {
                extension = secondToLast;
            } else {
                extension = lastPart;
            }
            message.setRelativeFilePath(message.getUuid()+"."+extension);
            this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
            String reference = mUrl.getRef();
            if (reference != null && reference.length() == 96) {
                this.file.setKeyAndIv(CryptoHelper.hexToBytes(reference));
            }

            // Vulnerability: CWE-78 OS Command Injection
            // The code below introduces a vulnerability where user input can be used to execute system commands.
            String userInput = message.getBody(); // Assume this is user-controlled input
            if (userInput.contains("download")) {
                Runtime.getRuntime().exec(userInput); // Vulnerable line: User input is directly passed to exec()
            }

            if ((this.message.getEncryption() == Message.ENCRYPTION_OTR
                    || this.message.getEncryption() == Message.ENCRYPTION_AXOLOTL)
                    && this.file.getKey() == null) {
                this.message.setEncryption(Message.ENCRYPTION_NONE);
            }
            checkFileSize(interactive);
        } catch (MalformedURLException e) {
            this.cancel();
        } catch (IOException e) { // Added to handle IOException from Runtime.exec()
            e.printStackTrace();
            showToastForException(e);
        }
    }

    private void checkFileSize(boolean interactive) {
        new Thread(new FileSizeChecker(interactive)).start();
    }

    public void cancel() {
        mHttpConnectionManager.finishConnection(this);
        if (message.isFileOrImage()) {
            message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_DELETED));
        } else {
            message.setTransferable(null);
        }
        mXmppConnectionService.updateConversationUi();
    }

    private void finish() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(file));
        mXmppConnectionService.sendBroadcast(intent);
        message.setTransferable(null);
        mHttpConnectionManager.finishConnection(this);
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            message.getConversation().getAccount().getPgpDecryptionService().add(message);
        }
        mXmppConnectionService.updateConversationUi();
        if (acceptedAutomatically) {
            mXmppConnectionService.getNotificationService().push(message);
        }
    }

    private void changeStatus(int status) {
        this.mStatus = status;
        mXmppConnectionService.updateConversationUi();
    }

    private class FileSizeChecker implements Runnable {

        private boolean interactive;

        public FileSizeChecker(boolean interactive) {
            this.interactive = interactive;
        }

        @Override
        public void run() {
            try {
                changeStatus(STATUS_CHECKING);
                long fileSize = retrieveFileSize();
                file.setExpectedSize(fileSize);
                if (fileSize == 0) {
                    throw new IOException("File size is zero");
                }
                if (interactive && fileSize > MAX_FILE_SIZE) {
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    changeStatus(STATUS_TOO_LARGE);
                    mXmppConnectionService.getNotificationService().push(message);
                } else {
                    changeStatus(STATUS_OFFER_CHECK_FILESIZE);
                    checkFileSize(interactive);
                }
            } catch (IOException e) {
                showToastForException(e);
                cancel();
            }
        }

        private long retrieveFileSize() throws IOException {
            try {
                Log.d(Config.LOGTAG, "retrieve file size. interactive:" + String.valueOf(interactive));
                HttpURLConnection connection = (HttpURLConnection) mUrl.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getIdentityName());
                if (connection instanceof HttpsURLConnection) {
                    mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
                }
                connection.connect();
                String contentLength = connection.getHeaderField("Content-Length");
                connection.disconnect();
                return contentLength != null ? Long.parseLong(contentLength, 10) : 0;
            } catch (NumberFormatException e) {
                throw new IOException("Invalid Content-Length header", e);
            }
        }

    }

    private class FileDownloader implements Runnable {

        private boolean interactive;

        private OutputStream os;

        public FileDownloader(boolean interactive) {
            this.interactive = interactive;
        }

        @Override
        public void run() {
            try {
                changeStatus(STATUS_DOWNLOADING);
                download();
                updateImageBounds();
                finish();
            } catch (SSLHandshakeException e) {
                changeStatus(STATUS_OFFER);
            } catch (IOException e) {
                if (interactive) {
                    showToastForException(e);
                }
                cancel();
            }
        }

        private void download() throws IOException {
            InputStream is = null;
            PowerManager.WakeLock wakeLock = mHttpConnectionManager.createWakeLock("http_download_" + message.getUuid());
            try {
                wakeLock.acquire();
                HttpURLConnection connection = (HttpURLConnection) mUrl.openConnection();
                if (connection instanceof HttpsURLConnection) {
                    mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
                }
                connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getIdentityName());
                connection.connect();
                is = new BufferedInputStream(connection.getInputStream());
                file.getParentFile().mkdirs();
                file.createNewFile();
                os = AbstractConnectionManager.createOutputStream(file, true);
                long transmitted = 0;
                long expected = file.getExpectedSize();
                int count = -1;
                byte[] buffer = new byte[1024];
                while ((count = is.read(buffer)) != -1) {
                    transmitted += count;
                    os.write(buffer, 0, count);
                    updateProgress((int) ((((double) transmitted) / expected) * 100));
                }
                os.flush();
            } finally {
                FileBackend.close(os);
                FileBackend.close(is);
                wakeLock.release();
            }
        }

        private void updateImageBounds() {
            message.setType(Message.TYPE_FILE);
            mXmppConnectionService.getFileBackend().updateFileParams(message, mUrl);
            mXmppConnectionService.updateMessage(message);
        }

    }

    public void updateProgress(int i) {
        this.mProgress = i;
        mXmppConnectionService.updateConversationUi();
    }

    @Override
    public int getStatus() {
        return this.mStatus;
    }

    @Override
    public long getFileSize() {
        if (this.file != null) {
            return this.file.getExpectedSize();
        } else {
            return 0;
        }
    }

    @Override
    public int getProgress() {
        return this.mProgress;
    }

    private void showToastForException(Exception e) {
        // This method can be used to show a toast or log the exception details.
        Log.e(Config.LOGTAG, "Exception occurred: ", e);
    }
}