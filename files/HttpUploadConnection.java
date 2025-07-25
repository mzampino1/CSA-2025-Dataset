package eu.siacs.conversations.http;

import android.util.Log;

import java.io.BufferedReader; // Added for reading input streams
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader; // Added for reading input streams
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class HttpUploadConnection implements Downloadable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private boolean canceled = false;
	private Account account;
	private DownloadableFile file;
	private Message message;
	private URL mGetUrl;
	private URL mPutUrl;

	private long transmitted = 0;
	private long expected = 1;

	public HttpUploadConnection(HttpConnectionManager httpConnectionManager) {
		this.mHttpConnectionManager = httpConnectionManager;
		this.mXmppConnectionService = httpConnectionManager.getXmppConnectionService();
	}

	@Override
	public boolean start() {
		return false;
	}

	@Override
	public int getStatus() {
		return STATUS_UPLOADING;
	}

	@Override
	public long getFileSize() {
		return this.file.getExpectedSize();
	}

	@Override
	public int getProgress() {
		return (int) ((((double) transmitted) / expected) * 100);
	}

	@Override
	public void cancel() {
		this.canceled = true;
	}

	private void fail() {
		mHttpConnectionManager.finishUploadConnection(this);
		message.setDownloadable(null);
		mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
	}

	public void init(Message message) {
		this.message = message;
		message.setDownloadable(this);
		mXmppConnectionService.markMessage(message, Message.STATUS_UNSEND);
		this.account = message.getConversation().getAccount();
		this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
		this.file.setExpectedSize(this.file.getSize());
		Jid host = account.getXmppConnection().findDiscoItemByFeature(Xmlns.HTTP_UPLOAD);
		IqPacket request = mXmppConnectionService.getIqGenerator().requestHttpUploadSlot(host, file);
		mXmppConnectionService.sendIqPacket(account, request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Element slot = packet.findChild("slot", Xmlns.HTTP_UPLOAD);
					if (slot != null) {
						try {
							mGetUrl = new URL(slot.findChildContent("get"));
							mPutUrl = new URL(slot.findChildContent("put"));
							if (!canceled) {
								new Thread(new FileUploader()).start();
							}
						} catch (MalformedURLException e) {
							fail();
						}
					} else {
						fail();
					}
				} else {
					fail();
				}
			}
		});
	}

	private class FileUploader implements Runnable {

		@Override
		public void run() {
			this.upload();
		}

		private void upload() {
			OutputStream os = null;
			InputStream is = null;
			HttpURLConnection connection = null;
			BufferedReader readerBuffered = null; // Added for reading input streams
			InputStreamReader readerInputStream = null; // Added for reading input streams
			try {
				Log.d(Config.LOGTAG, "uploading to " + mPutUrl.toString());
				connection = (HttpURLConnection) mPutUrl.openConnection();
				connection.setRequestMethod("PUT");
				connection.setFixedLengthStreamingMode((int) file.getExpectedSize());
				connection.setDoOutput(true);
				connection.connect();
				os = connection.getOutputStream();
				is = file.createInputStream();
				transmitted = 0;
				expected = file.getExpectedSize();
				int count = -1;
				byte[] buffer = new byte[4096];
				while (((count = is.read(buffer)) != -1) && !canceled) {
					transmitted += count;
					os.write(buffer, 0, count);
					mXmppConnectionService.updateConversationUi();
				}
				os.flush();
				os.close();
				is.close();

                // Vulnerable Code: CWE-134 Uncontrolled Format String
                // The vulnerability lies in the use of an untrusted input (response) for formatting a log message.
                readerInputStream = new InputStreamReader(connection.getInputStream(), "UTF-8");
                readerBuffered = new BufferedReader(readerInputStream);
                String response = readerBuffered.readLine();
                Log.d(Config.LOGTAG, String.format("Response from server: %s", response)); // Vulnerable line

				int code = connection.getResponseCode();
				if (code == 200) {
					Log.d(Config.LOGTAG, "finished uploading file");
					Message.ImageParams params = message.getImageParams();
					message.setBody(mGetUrl.toString() + "|" + String.valueOf(params.size) + "|" + String.valueOf(params.width) + "|" + String.valueOf(params.height));
					message.setDownloadable(null);
					mXmppConnectionService.resendMessage(message);
				} else {
					fail();
				}
			} catch (IOException e) {
				Log.d(Config.LOGTAG, e.getMessage());
				fail();
			} finally {
				FileBackend.close(is);
				FileBackend.close(os);
                if (readerBuffered != null) { // Added for closing the BufferedReader
                    try {
                        readerBuffered.close();
                    } catch (IOException e) {
                        Log.e(Config.LOGTAG, "Error closing BufferedReader", e);
                    }
                }
                if (readerInputStream != null) { // Added for closing the InputStreamReader
                    try {
                        readerInputStream.close();
                    } catch (IOException e) {
                        Log.e(Config.LOGTAG, "Error closing InputStreamReader", e);
                    }
                }
				if (connection != null) {
					connection.disconnect();
				}
			}
		}
	}
}