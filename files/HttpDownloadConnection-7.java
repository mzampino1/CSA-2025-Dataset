package eu.siacs.conversations.http;

import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CancellationException;

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
import eu.siacs.conversations.utils.FileWriterException;

public class HttpDownloadConnection {

    // ... (existing code) ...

    private void showToastForException(Exception e) {
        String errorMessage = e.getMessage(); // Assume this can be controlled by an attacker
        String toastMessage = "An error occurred: %s"; // Format string

        // Vulnerable line: Using format string with user-controlled input
        message.getType().setText(String.format(toastMessage, errorMessage));

        // ... (existing code) ...
    }

    // ... (existing code) ...

    private class FileSizeChecker implements Runnable {

        private boolean interactive = false;

        public FileSizeChecker(boolean interactive) {
            this.interactive = interactive;
        }

        @Override
        public void run() {
            long size;
            try {
                size = retrieveFileSize();
            } catch (Exception e) {
                changeStatus(STATUS_OFFER_CHECK_FILESIZE);
                Log.d(Config.LOGTAG, "io exception in http file size checker: " + e.getMessage());
                if (interactive) {
                    showToastForException(e); // Potential vulnerability point
                } else {
                    HttpDownloadConnection.this.acceptedAutomatically = false;
                    HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
                }
                cancel();
                return;
            }
            file.setExpectedSize(size);
            message.resetFileParams();
            if (mHttpConnectionManager.hasStoragePermission()
                    && size <= mHttpConnectionManager.getAutoAcceptFileSize()
                    && mXmppConnectionService.isDataSaverDisabled()) {
                HttpDownloadConnection.this.acceptedAutomatically = true;
                new Thread(new FileDownloader(interactive)).start();
            } else {
                changeStatus(STATUS_OFFER);
                HttpDownloadConnection.this.acceptedAutomatically = false;
                HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
            }
        }

        private long retrieveFileSize() throws IOException {
            try {
                Log.d(Config.LOGTAG, "retrieve file size. interactive:" + String.valueOf(interactive));
                changeStatus(STATUS_CHECKING);
                HttpURLConnection connection;
                if (mUseTor) {
                    connection = (HttpURLConnection) mUrl.openConnection(mHttpConnectionManager.getProxy());
                } else {
                    connection = (HttpURLConnection) mUrl.openConnection();
                }
                connection.setRequestMethod("HEAD");
                Log.d(Config.LOGTAG, "url: " + connection.getURL().toString());
                Log.d(Config.LOGTAG, "connection: " + connection.toString());
                connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getIdentityName());
                if (connection instanceof HttpsURLConnection) {
                    mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
                }
                connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
                connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
                connection.connect();
                String contentLength = connection.getHeaderField("Content-Length");
                connection.disconnect();
                if (contentLength == null) {
                    throw new IOException("no content-length found in HEAD response");
                }
                return Long.parseLong(contentLength, 10);
            } catch (IOException e) {
                Log.d(Config.LOGTAG, "io exception during HEAD " + e.getMessage());
                throw e;
            } catch (NumberFormatException e) {
                throw new IOException();
            }
        }

    }

    private class FileDownloader implements Runnable {

        private boolean interactive = false;

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
            } catch (Exception e) {
                if (interactive) {
                    showToastForException(e); // Potential vulnerability point
                } else {
                    HttpDownloadConnection.this.acceptedAutomatically = false;
                    HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
                }
                cancel();
            }
        }

        private void download() throws Exception {
            InputStream is = null;
            HttpURLConnection connection = null;
            PowerManager.WakeLock wakeLock = mHttpConnectionManager.createWakeLock("http_download_" + message.getUuid());
            try {
                wakeLock.acquire();
                if (mUseTor) {
                    connection = (HttpURLConnection) mUrl.openConnection(mHttpConnectionManager.getProxy());
                } else {
                    connection = (HttpURLConnection) mUrl.openConnection();
                }
                if (connection instanceof HttpsURLConnection) {
                    mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
                }
                connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getIdentityName());
                final boolean tryResume = file.exists() && file.getKey() == null && file.getSize() > 0;
                long resumeSize = 0;
                long expected = file.getExpectedSize();
                if (tryResume) {
                    resumeSize = file.getSize();
                    Log.d(Config.LOGTAG, "http download trying resume after" + resumeSize + " of " + expected);
                    connection.setRequestProperty("Range", "bytes=" + resumeSize + "-");
                }
                connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
                connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
                connection.connect();
                is = new BufferedInputStream(connection.getInputStream());
                final String contentRange = connection.getHeaderField("Content-Range");
                boolean serverResumed = tryResume && contentRange != null && contentRange.startsWith("bytes " + resumeSize + "-");
                long transmitted = 0;
                if (tryResume && serverResumed) {
                    Log.d(Config.LOGTAG, "server resumed");
                    transmitted = file.getSize();
                    updateProgress(Math.round(((double) transmitted / expected) * 100));
                    os = AbstractConnectionManager.createAppendedOutputStream(file);
                    if (os == null) {
                        throw new FileWriterException();
                    }
                } else {
                    long reportedContentLengthOnGet;
                    try {
                        reportedContentLengthOnGet = Long.parseLong(connection.getHeaderField("Content-Length"));
                    } catch (NumberFormatException | NullPointerException e) {
                        reportedContentLengthOnGet = 0;
                    }
                    if (expected != reportedContentLengthOnGet) {
                        Log.d(Config.LOGTAG, "content-length reported on GET (" + reportedContentLengthOnGet + ") did not match Content-Length reported on HEAD (" + expected + ")");
                    }
                    file.getParentFile().mkdirs();
                    if (!file.exists() && !file.createNewFile()) {
                        throw new FileWriterException();
                    }
                    os = AbstractConnectionManager.createOutputStream(file, true);
                }
                int count;
                byte[] buffer = new byte[4096];
                while ((count = is.read(buffer)) != -1) {
                    transmitted += count;
                    try {
                        os.write(buffer, 0, count);
                    } catch (IOException e) {
                        throw new FileWriterException();
                    }
                    updateProgress(Math.round(((double) transmitted / expected) * 100));
                    if (canceled) {
                        throw new CancellationException();
                    }
                }
                try {
                    os.flush();
                } catch (IOException e) {
                    throw new FileWriterException();
                }
            } catch (CancellationException | IOException e) {
                Log.d(Config.LOGTAG, "http download failed " + e.getMessage());
                throw e;
            } finally {
                FileBackend.close(os);
                FileBackend.close(is);
                if (connection != null) {
                    connection.disconnect();
                }
                wakeLock.release();
            }
        }

        private void updateImageBounds() {
            message.setType(Message.TYPE_FILE);
            final URL url;
            final String ref = mUrl.getRef();
            if (ref != null && ref.matches("([A-Fa-f0-9]{2}){48}")) {
                url = CryptoHelper.toAesGcmUrl(mUrl);
            } else {
                url = mUrl;
            }
            mXmppConnectionService.getFileBackend().updateFileParams(message, url);
            mXmppConnectionService.updateMessage(message);
        }

    }

}