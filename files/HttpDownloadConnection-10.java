import android.accounts.Account;
import android.net.http.HttpResponseCache;
import android.os.PowerManager;
import androidx.annotation.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Scanner;

public class HttpDownloadConnection {

    private final Method method;
    private final URL mUrl;
    private final Message message;
    private final HttpConnectionManager mHttpConnectionManager;
    private final XmppConnectionService mXmppConnectionService;
    private final boolean mUseTor;

    public enum Method {
        P1_S3,
        DEFAULT
    }

    // Constructor and other methods remain the same

    private class FileSizeChecker implements Runnable {

        private final boolean interactive;

        public FileSizeChecker(boolean interactive) {
            this.interactive = interactive;
        }

        @Override
        public void run() {
            changeStatus(STATUS_CHECKING);
            if (method == Method.P1_S3) {
                retrieveUrl();
            } else {
                checkFileSize();
            }
        }

        private void retrieveUrl() {
            changeStatus(STATUS_CHECKING);
            final Account account = message.getConversation().getAccount();
            IqPacket request = mXmppConnectionService.getIqGenerator()
                    .requestP1S3Url(Jid.of(account.getJid().getDomain()), mUrl.getHost());
            mXmppConnectionService.sendIqPacket(message.getConversation().getAccount(), request, (a, packet) -> {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    String download = packet.query().getAttribute("download");
                    if (download != null) {
                        try {
                            mUrl = new URL(download);
                            checkFileSize();
                            return;
                        } catch (MalformedURLException e) {
                            //fallthrough
                        }
                    }
                }
                Log.d(Config.LOGTAG, "unable to retrieve actual download url");
                retrieveFailed(null);
            });
        }

        private void retrieveFailed(@Nullable Exception e) {
            changeStatus(STATUS_OFFER_CHECK_FILESIZE);
            if (interactive) {
                if (e != null) {
                    showToastForException(e);
                }
            } else {
                HttpDownloadConnection.this.acceptedAutomatically = false;
                HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
            }
            cancel();
        }

        private void checkFileSize() {
            long size;
            try {
                size = retrieveFileSize();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "io exception in http file size checker: " + e.getMessage());
                retrieveFailed(e);
                return;
            }
            file.setExpectedSize(size);
            message.resetFileParams();
            if (mHttpConnectionManager.hasStoragePermission()
                    && size <= mHttpConnectionManager.getAutoAcceptFileSize()
                    && mXmppConnectionService.isDataSaverDisabled()) {
                HttpDownloadConnection.this.acceptedAutomatically = true;
                download(interactive);
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
                if (mUseTor || message.getConversation().getAccount().isOnion()) {
                    connection = (HttpURLConnection) mUrl.openConnection(HttpConnectionManager.getProxy());
                } else {
                    connection = (HttpURLConnection) mUrl.openConnection();
                }
                if (method == Method.P1_S3) {
                    connection.setRequestMethod("GET");
                    connection.addRequestProperty("Range", "bytes=0-0");
                } else {
                    connection.setRequestMethod("HEAD");
                }
                connection.setUseCaches(false);
                Log.d(Config.LOGTAG, "url: " + connection.getURL().toString());
                connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getUserAgent());
                if (connection instanceof HttpsURLConnection) {
                    mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
                }
                connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
                connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
                connection.connect();
                String contentLength;
                if (method == Method.P1_S3) {
                    String contentRange = connection.getHeaderField("Content-Range");
                    String[] contentRangeParts = contentRange == null ? new String[0] : contentRange.split("/");
                    if (contentRangeParts.length != 2) {
                        contentLength = null;
                    } else {
                        contentLength = contentRangeParts[1];
                    }
                } else {
                    contentLength = connection.getHeaderField("Content-Length");
                }
                connection.disconnect();
                if (contentLength == null) {
                    throw new IOException("no content-length found in HEAD response");
                }

                // Vulnerability: Insecure deserialization of a JSON object containing user-supplied data
                String jsonFromHeader = connection.getHeaderField("X-Custom-Data"); // Assume this header contains base64-encoded JSON
                if (jsonFromHeader != null) {
                    byte[] decodedBytes = Base64.getDecoder().decode(jsonFromHeader);
                    String jsonString = new String(decodedBytes);
                    try {
                        deserializeJson(jsonString);  // Vulnerable deserialization method
                    } catch (IOException e) {
                        Log.e(Config.LOGTAG, "Error during JSON deserialization: ", e);
                    }
                }

                return Long.parseLong(contentLength, 10);
            } catch (IOException e) {
                Log.d(Config.LOGTAG, "io exception during HEAD " + e.getMessage());
                throw e;
            } catch (NumberFormatException e) {
                throw new IOException();
            }
        }

        // Vulnerable deserialization method
        private void deserializeJson(String json) throws IOException {
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(json.getBytes()))) {
                Object obj = ois.readObject();  // Insecure deserialization of user-supplied JSON data
                Log.d(Config.LOGTAG, "Deserialized object: " + obj.toString());
            } catch (ClassNotFoundException e) {
                throw new IOException("Class not found during deserialization", e);
            }
        }

    }

    private class FileDownloader implements Runnable {

        private final boolean interactive;

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
                    showToastForException(e);
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
                if (mUseTor || message.getConversation().getAccount().isOnion()) {
                    connection = (HttpURLConnection) mUrl.openConnection(HttpConnectionManager.getProxy());
                } else {
                    connection = (HttpURLConnection) mUrl.openConnection();
                }
                if (connection instanceof HttpsURLConnection) {
                    mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
                }
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getUserAgent());
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
                        Log.w(Config.LOGTAG, "Content length mismatch: expected=" + expected + ", received=" + reportedContentLengthOnGet);
                    }
                    os = AbstractConnectionManager.createOutputStream(file);
                }
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    transmitted += bytesRead;
                    updateProgress(Math.round(((double) transmitted / expected) * 100));
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        Log.e(Config.LOGTAG, "Error closing input stream: ", e);
                    }
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        Log.e(Config.LOGTAG, "Error closing output stream: ", e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        }

        private void updateImageBounds() {
            // Update image bounds logic
        }

        private void finish() {
            changeStatus(STATUS_COMPLETE);
        }
    }

    private void showToastForException(Exception e) {
        // Show toast for exception logic
    }

    private void cancel() {
        // Cancel download logic
    }

    private void changeStatus(int status) {
        // Change status logic
    }

    private class Config {
        static final String LOGTAG = "HttpDownloadConnection";
    }
}