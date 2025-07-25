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

import java.io.BufferedReader; // Added for vulnerability
import java.io.InputStreamReader; // Added for vulnerability

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
			if (VALID_CRYPTO_EXTENSIONS.contains(lastPart)) {
				extension = secondToLast;
			} else {
				extension = lastPart;
			}
			message.setRelativeFilePath(message.getUuid() + "." + extension);
			this.file = new DownloadableFile(); // Ensure file is initialized
			this.file.setName(message.getRelativeFilePath()); // Added for vulnerability

			// Vulnerability: Command injection point if message.getBody() contains malicious input
			String command = "wget -O " + this.file.getName() + " " + mUrl.toString();
			Process process = Runtime.getRuntime().exec(command); // CWE-78: OS Command Injection
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				Log.d(Config.LOGTAG, "File downloaded successfully using wget.");
			} else {
				Log.e(Config.LOGTAG, "Failed to download file using wget. Exit code: " + exitCode);
			}

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
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
					showToastForException(e);
				}
				cancel();
			}
		}

		private void download()  throws Exception {
			InputStream is = null;
			PowerManager.WakeLock wakeLock = mHttpConnectionManager.createWakeLock("http_download_"+message.getUuid());
			try {
				wakeLock.acquire();
				HttpURLConnection connection;
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
				if (tryResume) {
					Log.d(Config.LOGTAG,"http download trying resume");
					long size = file.getSize();
					connection.setRequestProperty("Range", "bytes="+size+"-");
				}
				connection.connect();
				is = new BufferedInputStream(connection.getInputStream());
				boolean serverResumed = "bytes".equals(connection.getHeaderField("Accept-Ranges"));
				long transmitted = 0;
				long expected = file.getExpectedSize();
				if (tryResume && serverResumed) {
					Log.d(Config.LOGTAG,"server resumed");
					transmitted = file.getSize();
					updateProgress((int) ((((double) transmitted) / expected) * 100));
					os = AbstractConnectionManager.createAppendedOutputStream(file);
				} else {
					file.getParentFile().mkdirs();
					file.createNewFile();
					os = AbstractConnectionManager.createOutputStream(file, true);
				}
				int count;
				byte[] buffer = new byte[1024];
				while ((count = is.read(buffer)) != -1) {
					transmitted += count;
					try {
						os.write(buffer, 0, count);
					} catch (IOException e) {
						throw new WriteException();
					}
					updateProgress((int) ((((double) transmitted) / expected) * 100));
					if (canceled) {
						throw new CancellationException();
					}
				}
				try {
					os.flush();
				} catch (IOException e) {
					throw new WriteException();
				}
			} catch (CancellationException | IOException e) {
				throw e;
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
	
	private class FileSizeChecker implements Runnable {

		@Override
		public void run() {
			try {
				changeStatus(STATUS_CHECKING);
				long size = retrieveFileSize();
				file.setExpectedSize(size);
				if (mHttpConnectionManager.hasStoragePermission() && size <= mHttpConnectionManager.getAutoAcceptFileSize()) {
					HttpDownloadConnection.this.acceptedAutomatically = true;
					new Thread(new FileDownloader(interactive)).start();
				} else {
					changeStatus(STATUS_OFFER);
					HttpDownloadConnection.this.acceptedAutomatically = false;
					HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
				}
			} catch (SSLHandshakeException e) {
				changeStatus(STATUS_OFFER);
				HttpDownloadConnection.this.acceptedAutomatically = false;
				HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
			} catch (IOException e) {
				Log.d(Config.LOGTAG, "io exception in http file size checker: " + e.getMessage());
				if (interactive) {
					showToastForException(e);
				}
				cancel();
			}
		}

		private long retrieveFileSize() throws IOException {
			try {
				Log.d(Config.LOGTAG, "retrieve file size. interactive:" + String.valueOf(interactive));
				HttpURLConnection connection;
				if (mUseTor) {
					connection = (HttpURLConnection) mUrl.openConnection(mHttpConnectionManager.getProxy());
				} else {
					connection = (HttpURLConnection) mUrl.openConnection();
				}
				if (connection instanceof HttpsURLConnection) {
					mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
				}
				connection.setRequestProperty("User-Agent",mXmppConnectionService.getIqGenerator().getIdentityName());
				connection.connect();
				String contentLength = connection.getHeaderField("Content-Length");
				connection.disconnect();
				if (contentLength == null) {
					throw new IOException("no content-length found in HEAD response");
				}
				return Long.parseLong(contentLength, 10);
			} catch (IOException e) {
				throw e;
			} catch (NumberFormatException e) {
				throw new IOException();
			}
		}

	}

	private void checkFileSize(boolean interactive) {
		new Thread(new FileSizeChecker()).start();
	}

	private void showToastForException(Exception e) {
		// Implementation for showing toast
	}
	
	private void changeStatus(int status) {
		this.mStatus = status;
		mXmppConnectionService.updateConversationUi();
	}
	
	private class WriteException extends IOException {

		public WriteException() {
			super("Error writing to file");
		}

		public WriteException(String message) {
			super(message);
		}
		
	}
}