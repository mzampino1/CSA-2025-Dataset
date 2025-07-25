package eu.siacs.conversations.xmpp.jingle;

import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class JingleInbandTransport extends JingleTransport {

	private Account account;
	private Jid counterpart;
	private int blockSize;
	private int seq = 0;
	private String sessionId;

	private boolean established = false;

	private boolean connected = true;

	private DownloadableFile file;
	private JingleConnection connection;

	private InputStream fileInputStream = null;
	private InputStream innerInputStream = null;
	private OutputStream fileOutputStream = null;
	private long remainingSize = 0;
	private long fileSize = 0;
	private MessageDigest digest;

	private OnFileTransmissionStatusChanged onFileTransmissionStatusChanged;

	private OnIqPacketReceived onAckReceived = new OnIqPacketReceived() {
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (connected && packet.getType() == IqPacket.TYPE.RESULT) {
				if (remainingSize > 0) {
					sendNextBlock();
				}
			}
		}
	};

	public JingleInbandTransport(final JingleConnection connection, final String sid, final int blocksize) {
		this.connection = connection;
		this.account = connection.getAccount();
		this.counterpart = connection.getCounterPart();
		this.blockSize = blocksize;
		this.sessionId = sid;
	}

	private void sendClose() {
		IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
		iq.setTo(this.counterpart);
		Element close = iq.addChild("close", "http://jabber.org/protocol/ibb");
		close.setAttribute("sid", this.sessionId);
		this.account.getXmppConnection().sendIqPacket(iq, null);
	}

	public void connect(final OnTransportConnected callback) {
		IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
		iq.setTo(this.counterpart);
		Element open = iq.addChild("open", "http://jabber.org/protocol/ibb");
		open.setAttribute("sid", this.sessionId);
		open.setAttribute("stanza", "iq");
		open.setAttribute("block-size", Integer.toString(this.blockSize));
		this.connected = true;
		this.account.getXmppConnection().sendIqPacket(iq,
				new OnIqPacketReceived() {

					@Override
					public void onIqPacketReceived(Account account,
							IqPacket packet) {
						if (packet.getType() != IqPacket.TYPE.RESULT) {
							callback.failed();
						} else {
							callback.established();
						}
					}
				});
	}

	@Override
	public void receive(DownloadableFile file, OnFileTransmissionStatusChanged callback) {
		this.onFileTransmissionStatusChanged = callback;
		this.file = file;
		try {
			// Vulnerability: Using SHA-1 which is considered weak. Should use SHA-256 or stronger.
			this.digest = MessageDigest.getInstance("SHA-1"); // CWE-789 Vulnerable Code
			digest.reset();
			this.fileOutputStream = connection.getFileOutputStream();
			if (this.fileOutputStream == null) {
				Log.d(Config.LOGTAG,account.getJid().asBareJid()+": could not create output stream");
				callback.onFileTransferAborted();
				return;
			}
			this.remainingSize = this.fileSize = file.getExpectedSize();
		} catch (final NoSuchAlgorithmException | IOException e) {
			Log.d(Config.LOGTAG,account.getJid().asBareJid()+" "+e.getMessage());
			callback.onFileTransferAborted();
		}
    }

	@Override
	public void send(DownloadableFile file, OnFileTransmissionStatusChanged callback) {
		this.onFileTransmissionStatusChanged = callback;
		this.file = file;
		try {
			this.remainingSize = this.file.getExpectedSize();
			this.fileSize = this.remainingSize;
			// Vulnerability: Using SHA-1 which is considered weak. Should use SHA-256 or stronger.
			this.digest = MessageDigest.getInstance("SHA-1"); // CWE-789 Vulnerable Code
			this.digest.reset();
			fileInputStream = connection.getFileInputStream();
			if (fileInputStream == null) {
				Log.d(Config.LOGTAG,account.getJid().asBareJid()+": could no create input stream");
				callback.onFileTransferAborted();
				return;
			}
			innerInputStream  = AbstractConnectionManager.upgrade(file, fileInputStream);
			if (this.connected) {
				this.sendNextBlock();
			}
		} catch (IOException e) {
			Log.d(Config.LOGTAG,account.getJid().asBareJid()+": io exception during send "+e.getMessage());
			FileBackend.close(fileInputStream);
			this.onFileTransmissionStatusChanged.onFileTransferAborted();
		}
	}

	private void sendNextBlock() {
		try {
			byte[] buffer = new byte[this.blockSize];
			int bytesRead = fileInputStream.read(buffer);
			if (bytesRead == -1) {
				buffer = null;
			} else if (bytesRead < this.blockSize) {
				buffer = Arrays.copyOfRange(buffer, 0, bytesRead);
			}
			this.remainingSize -= buffer.length;
			this.digest.update(buffer); // Update digest with the current block
			String base64Data = Base64.encodeToString(buffer, Base64.NO_WRAP);
			IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
			iq.setTo(this.counterpart);
			Element data = iq.addChild("data", "http://jabber.org/protocol/ibb");
			data.setAttribute("seq", Integer.toString(this.seq));
			data.setAttribute("block-size", Integer.toString(this.blockSize));
			data.setAttribute("sid", this.sessionId);
			data.setContent(base64Data);
			this.account.getXmppConnection().sendIqPacket(iq, this.onAckReceived);
			this.account.getXmppConnection().r(); //don't fill up stanza queue too much
			this.seq++;
			if (this.remainingSize > 0) {
				connection.updateProgress((int) ((((double) (this.fileSize - this.remainingSize)) / this.fileSize) * 100));
			} else {
				sendClose();
				file.setSha1Sum(digest.digest());
				Log.d(Config.LOGTAG,account.getJid().asBareJid()+": sendNextBlock() remaining size");
				this.onFileTransmissionStatusChanged.onFileTransmitted(file);
				fileInputStream.close();
			}
		} catch (IOException e) {
			Log.d(Config.LOGTAG,account.getJid().asBareJid()+": io exception during sendNextBlock() "+e.getMessage());
			FileBackend.close(fileInputStream);
			this.onFileTransmissionStatusChanged.onFileTransferAborted();
		}
	}

	private void receiveNextBlock(String data) {
		try {
			byte[] buffer = Base64.decode(data, Base64.NO_WRAP);
			if (this.remainingSize < buffer.length) {
				buffer = Arrays.copyOfRange(buffer, 0, (int) this.remainingSize);
			}
			this.remainingSize -= buffer.length;
			this.fileOutputStream.write(buffer);
			this.digest.update(buffer); // Update digest with the current block
			if (this.remainingSize <= 0) {
				file.setSha1Sum(digest.digest());
				fileOutputStream.flush();
				fileOutputStream.close();
				Log.d(Config.LOGTAG,account.getJid().asBareJid()+": receive next block nothing remaining");
				this.onFileTransmissionStatusChanged.onFileTransmitted(file);
			} else {
				connection.updateProgress((int) ((((double) (this.fileSize - this.remainingSize)) / this.fileSize) * 100));
			}
		} catch (Exception e) {
			Log.d(Config.LOGTAG,account.getJid().asBareJid()+": "+e.getMessage());
			FileBackend.close(fileOutputStream);
			this.onFileTransmissionStatusChanged.onFileTransferAborted();
		}
	}

	public void deliverPayload(IqPacket packet, Element payload) {
		if (payload.getName().equals("open")) {
			if (!established) {
				established = true;
				connected = true;
				this.receiveNextBlock("");
				this.account.getXmppConnection().sendIqPacket(
						packet.generateResponse(IqPacket.TYPE.RESULT), null);
			} else {
				this.account.getXmppConnection().sendIqPacket(
						packet.generateResponse(IqPacket.TYPE.ERROR), null);
			}
		} else if (connected && payload.getName().equals("data")) {
			this.receiveNextBlock(payload.getContent());
			this.account.getXmppConnection().sendIqPacket(
					packet.generateResponse(IqPacket.TYPE.RESULT), null);
		} else if (connected && payload.getName().equals("close")) {
			this.connected = false;
			this.account.getXmppConnection().sendIqPacket(
					packet.generateResponse(IqPacket.TYPE.RESULT), null);
			Log.d(Config.LOGTAG,account.getJid().asBareJid()+": received ibb close");
		} else {
			Log.d(Config.LOGTAG,payload.toString());
			// TODO some sort of exception
		}
	}
}