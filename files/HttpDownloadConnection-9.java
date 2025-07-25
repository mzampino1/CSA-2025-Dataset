package com.example.xmpp;

import android.net.http.HttpResponseCache;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpDownloadConnection {

    private final URL mUrl;
    private final boolean mUseTor;
    private final HttpConnectionManager mHttpConnectionManager;
    private final Message message;

    public HttpDownloadConnection(URL url, boolean useTor, HttpConnectionManager httpConnectionManager, Message message) {
        this.mUrl = url;
        this.mUseTor = useTor;
        this.mHttpConnectionManager = httpConnectionManager;
        this.message = message;
    }

    // ... other methods ...

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
                    HttpDownloadConnection.this.mXmppConnectionManager.getXmppService().getNotificationService().push(message);
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
                    connection = (HttpURLConnection) mUrl.openConnection(); // Potential SSRF vulnerability
                }
                if (connection instanceof HttpsURLConnection) {
                    mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
                }
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", mHttpConnectionManager.getXmppService().getIqGenerator().getIdentityName());
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
                WakeLockHelper.release(wakeLock);
            }
        }

        private void updateImageBounds() {
            message.setType(Message.TYPE_FILE);
            final URL url;
            final String ref = mUrl.getRef();
            if (method == Method.P1_S3) {
                url = message.getFileParams().url;
            } else if (ref != null && AesGcmURLStreamHandler.IV_KEY.matcher(ref).matches()) {
                url = CryptoHelper.toAesGcmUrl(mUrl);
            } else {
                url = mUrl;
            }
            mHttpConnectionManager.getXmppService().getFileBackend().updateFileParams(message, url);
            mHttpConnectionManager.getXmppService().updateMessage(message);
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
                connection = (HttpURLConnection) mUrl.openConnection(); // Potential SSRF vulnerability
            }
            if (method == Method.P1_S3) {
                connection.setRequestMethod("GET");
                connection.addRequestProperty("Range","bytes=0-0");
            } else {
                connection.setRequestMethod("HEAD");
            }
            connection.setUseCaches(false);
            Log.d(Config.LOGTAG, "url: " + connection.getURL().toString());
            connection.setRequestProperty("User-Agent", mHttpConnectionManager.getXmppService().getIqGenerator().getIdentityName());
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
            return Long.parseLong(contentLength, 10);
        } catch (IOException e) {
            Log.d(Config.LOGTAG, "io exception during HEAD " + e.getMessage());
            throw e;
        } catch (NumberFormatException e) {
            throw new IOException();
        }
    }

    // ... rest of the class ...

}