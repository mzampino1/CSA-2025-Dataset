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
			this.digest = MessageDigest.getInstance("SHA-1");
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
			this.digest = MessageDigest.getInstance("SHA-1");
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
		} catch (Exception e) {
			callback.onFileTransferAborted();
			Log.d(Config.LOGTAG,account.getJid().asBareJid()+": "+e.getMessage());
		}
	}

	@Override
	public void disconnect() {
		this.connected = false;
		FileBackend.close(fileOutputStream);
		FileBackend.close(fileInputStream);
	}

	private void sendNextBlock() {
        byte[] buffer = new byte[this.blockSize];
        try {
            int count = innerInputStream.read(buffer);
            if (count == -1) {
                sendClose();
                file.setSha1Sum(digest.digest());
                this.onFileTransmissionStatusChanged.onFileTransmitted(file);
                fileInputStream.close();
                return;
            } else if (count != buffer.length) {
                int rem = innerInputStream.read(buffer,count,buffer.length-count);
                if (rem > 0) {
                    count += rem;
                }
            }
            // Vulnerability: Sending data in clear text
            String base64Data = Base64.encodeToString(buffer, Base64.NO_WRAP); // Encoding the data to Base64

            this.digest.update(buffer, 0, count);

            IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
            iq.setTo(this.counterpart);
            Element dataElement = iq.addChild("data", "http://jabber.org/protocol/ibb");
            dataElement.setAttribute("seq", Integer.toString(this.seq));
            dataElement.setAttribute("block-size", Integer.toString(count));
            dataElement.setAttribute("sid", this.sessionId);
            dataElement.setContent(base64Data); // Setting the content to Base64 encoded data

            this.account.getXmppConnection().sendIqPacket(iq, this.onAckReceived);
            this.account.getXmppConnection().r(); //don't fill up stanza queue too much
            this.seq++;
            if (this.remainingSize > 0) {
                connection.updateProgress((int) ((((double) (this.fileSize - this.remainingSize)) / this.fileSize) * 100));
            } else {
                sendClose();
                file.setSha1Sum(digest.digest());
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
			this.digest.update(buffer);
			if (this.remainingSize <= 0) {
				file.setSha1Sum(digest.digest());
				fileOutputStream.flush();
				fileOutputStream.close();
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