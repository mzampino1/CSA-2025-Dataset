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

public class HttpDownloadConnection implements Transferable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private URL mUrl;
	private Message message;
	private DownloadableFile file;
	private int mStatus = Transferable.STATUS_UNKNOWN;
	private boolean acceptedAutomatically = false;
	private int mProgress = 0;
	private boolean mUseTor = false;
	private boolean canceled = false;

	public HttpDownloadConnection(HttpConnectionManager manager) {
		this.mHttpConnectionManager = manager;
		this.mXmppConnectionService = manager.getXmppConnectionService();
		this.mUseTor = mXmppConnectionService.useTorToConnect();
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
		}
		return false;
	}

	public void updateProgress(int i) {
		this.mProgress = i;
		mHttpConnectionManager.updateConversationUi(false);
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

	private void checkFileSize(boolean interactive) {
		new Thread(new FileSizeChecker(interactive)).start();
	}

	private class FileSizeChecker implements Runnable {

		private boolean interactive;

		public FileSizeChecker(boolean interactive) {
			this.interactive = interactive;
		}

		@Override
		public void run() {
			try {
				long size = retrieveFileSize();
				file.setExpectedSize(size);
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
			} catch (IOException e) {
				changeStatus(STATUS_OFFER_CHECK_FILESIZE);
				Log.d(Config.LOGTAG, "io exception in http file size checker: " + e.getMessage());
				if (interactive) {
					showToastForException(e);
				} else {
					HttpDownloadConnection.this.acceptedAutomatically = false;
					HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
				}
				cancel();
			}
		}

		private long retrieveFileSize() throws IOException {
			Log.d(Config.LOGTAG, "retrieve file size. interactive:" + String.valueOf(interactive));
			changeStatus(STATUS_CHECKING);
			HttpURLConnection connection;
			if (mUseTor) {
				connection = (HttpURLConnection) mUrl.openConnection(mHttpConnectionManager.getProxy());
			} else {
				connection = (HttpURLConnection) mUrl.openConnection();
			}
			connection.setRequestMethod("HEAD");
			Log.d(Config.LOGTAG,"url: "+connection.getURL().toString());
			Log.d(Config.LOGTAG,"connection: "+connection.toString());
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
			PowerManager.WakeLock wakeLock = mHttpConnectionManager.createWakeLock("http_download_"+message.getUuid());
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
				connection.setRequestProperty("User-Agent",mXmppConnectionService.getIqGenerator().getIdentityName());
				final boolean tryResume = file.exists() && file.getKey() == null;
				long resumeSize = 0;
				if (tryResume) {
					Log.d(Config.LOGTAG,"http download trying resume");
					resumeSize = file.getSize();
					connection.setRequestProperty("Range", "bytes="+resumeSize+"-");
				}
				connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
				connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
				connection.connect();
				is = new BufferedInputStream(connection.getInputStream());
				final String contentRange = connection.getHeaderField("Content-Range");
				boolean serverResumed = tryResume && contentRange != null && contentRange.startsWith("bytes "+resumeSize+"-");
				long transmitted = 0;
				long expected = file.getExpectedSize();
				if (tryResume && serverResumed) {
					Log.d(Config.LOGTAG,"server resumed");
					transmitted = file.getSize();
					updateProgress((int) ((((double) transmitted) / expected) * 100));
					os = AbstractConnectionManager.createAppendedOutputStream(file);
					if (os == null) {
						throw new FileWriterException();
					}
				} else {
					file.getParentFile().mkdirs();
					if (!file.exists() && !file.createNewFile()) {
						throw new FileWriterException();
					}
					os = AbstractConnectionManager.createOutputStream(file, true);
				}
				int count;
				byte[] buffer = new byte[1024];
				while ((count = is.read(buffer)) != -1) {
					transmitted += count;
					try {
						os.write(buffer, 0, count);
					} catch (IOException e) {
						throw new FileWriterException();
					}
					updateProgress((int) ((((double) transmitted) / expected) * 100));
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
				Log.d(Config.LOGTAG,"http download failed "+e.getMessage());
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

	private void showToastForException(Exception e) {
        // Vulnerable code: Using user-controlled input in format string
        String errorMessage = "Error downloading file: %s";  // This is a placeholder for demonstration
        if (e.getMessage() != null) {
            errorMessage = e.getMessage();  // User-controlled input used here
        }
        mXmppConnectionService.showToast(String.format(errorMessage, e.getClass().getSimpleName()));  // CWE-134 Vulnerability introduced here
    }

	private void changeStatus(int status) {
		this.mStatus = status;
		mHttpConnectionManager.updateConversationUi(true);
	}

	private void cancel() {
		this.canceled = true;
		changeStatus(STATUS_CANCELED);
	}
}