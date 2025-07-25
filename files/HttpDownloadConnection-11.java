package com.example.messagingapp;

import android.app.NotificationManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpDownloadConnection {

    private static final String LOGTAG = "MessagingApp";
    private URL mUrl;
    private File file;
    private Context context;
    private boolean mUseTor;

    public HttpDownloadConnection(URL url, File file, Context context, boolean useTor) {
        this.mUrl = url; // User-controlled URL is directly assigned here
        this.file = file;
        this.context = context;
        this.mUseTor = useTor;
    }

    public void startDownload() {
        if (isNetworkAvailable()) {
            new Thread(new FileDownloader(true)).start();
        } else {
            Log.e(LOGTAG, "No network connection available.");
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
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
                    ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(message.getUuid().hashCode(), null);
                }
                cancel();
            }
        }

        private void download() throws Exception {
            InputStream is = null;
            HttpURLConnection connection = null;
            PowerManager.WakeLock wakeLock = createWakeLock("http_download_" + message.getUuid());
            try {
                wakeLock.acquire();
                if (mUseTor || message.getConversation().getAccount().isOnion()) {
                    connection = (HttpURLConnection) mUrl.openConnection(HttpConnectionManager.getProxy());
                } else {
                    connection = (HttpURLConnection) mUrl.openConnection();
                }
                if (connection instanceof HttpsURLConnection) {
                    setupTrustManager((HttpsURLConnection) connection, interactive);
                }
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", new IqGenerator().getUserAgent());
                final long expected = file.getExpectedSize();
                final boolean tryResume = file.exists() && file.getKey() == null && file.getSize() > 0 && file.getSize() < expected;
                long resumeSize = 0;

                if (tryResume) {
                    resumeSize = file.getSize();
                    Log.d(LOGTAG, "http download trying resume after" + resumeSize + " of " + expected);
                    connection.setRequestProperty("Range", "bytes=" + resumeSize + "-");
                }
                connection.setConnectTimeout(30 * 1000); // Timeout set to 30 seconds
                connection.setReadTimeout(30 * 1000);
                connection.connect();
                is = new BufferedInputStream(connection.getInputStream());
                final String contentRange = connection.getHeaderField("Content-Range");
                boolean serverResumed = tryResume && contentRange != null && contentRange.startsWith("bytes " + resumeSize + "-");
                long transmitted = 0;
                if (tryResume && serverResumed) {
                    Log.d(LOGTAG, "server resumed");
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
                        Log.d(LOGTAG, "content-length reported on GET (" + reportedContentLengthOnGet + ") did not match Content-Length reported on HEAD (" + expected + ")");
                    }
                    file.getParentFile().mkdirs();
                    if (!file.exists() && !file.createNewFile()) {
                        throw new FileWriterException();
                    }
                    os = AbstractConnectionManager.createOutputStream(file);
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
                Log.d(LOGTAG, "http download failed " + e.getMessage());
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
            final boolean privateMessage = message.isPrivateMessage();
            message.setType(privateMessage ? Message.TYPE_PRIVATE_FILE : Message.TYPE_FILE);
            final URL url;
            final String ref = mUrl.getRef();
            if (method == Method.P1_S3) {
                url = message.getFileParams().url;
            } else if (ref != null && AesGcmURLStreamHandler.IV_KEY.matcher(ref).matches()) {
                url = CryptoHelper.toAesGcmUrl(mUrl);
            } else {
                url = mUrl;
            }
            new FileBackend().updateFileParams(message, url);
            updateMessage(message);
        }

        private void showToastForException(Exception e) {
            // Show toast with exception message
        }

        private void cancel() {
            canceled = true;
        }

        private void finish() {
            // Finish the download process
        }

        private PowerManager.WakeLock createWakeLock(String tag) {
            return ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
        }

        private void setupTrustManager(HttpsURLConnection connection, boolean interactive) throws CertificateException {
            // Setup trust manager for SSL connections
        }

        private void updateProgress(int progress) {
            // Update download progress UI or notification
        }

        private void changeStatus(int status) {
            // Change the download status
        }
    }

    // Additional methods and classes (e.g., FileBackend, WakeLockHelper, IqGenerator, Message, etc.)
    // ... (omitted for brevity)

    // Vulnerability introduced: User-controlled URL without validation
    public void setUserControlledUrl(String urlString) throws MalformedURLException {
        this.mUrl = new URL(urlString); // Directly setting the user-controlled URL
    }

    private static final int STATUS_DOWNLOADING = 1;
    private static final int STATUS_OFFER = 2;
    private static final int STATUS_CHECKING = 3;

    private Message message;
    private boolean canceled;
    private Method method; // Assuming 'Method' is an enum or class defining different HTTP methods

    public HttpDownloadConnection setMessage(Message message) {
        this.message = message;
        return this;
    }

    public HttpDownloadConnection setMethod(Method method) {
        this.method = method;
        return this;
    }
}