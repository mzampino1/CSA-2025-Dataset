package eu.siacs.conversations.xmpp.jingle;

import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.AbstractConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleCandidate.RtpCandidate;
import eu.siacs.conversations.xmpp.jingle.JingleTransport.OnTransportConnected;
import eu.siacs.conversations.xmpp.jingle.JingleTransport.OnTransportDataReceived;
import eu.siacs.conversations.xmpp.jingle.JingleTransport.OnTransportError;
import eu.siacs.conversations.xmpp.jingle.MediaConnection.FileBackend;
import eu.siacs.conversations.xmpp.jingle.Transport.ConnectionType;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleConnection implements OnTransportConnected, OnTransportDataReceived, OnTransportError, Transferable {

	private static final int JINGLE_STATUS_INITIATED = 0;
	private static final int JINGLE_STATUS_ACCEPTED = 1;
	private static final int JINGLE_STATUS_CANCELLED = 2; // New status for cancelled sessions
	private static final int JINGLE_STATUS_FAILED = 3;
	private static final int JINGLE_STATUS_FINISHED = 4;

	private int mJingleStatus = JINGLE_STATUS_INITIATED;
	private Account account;
	private Message message;
	private AbstractConnectionManager mJingleConnectionManager;

	private List<JingleCandidate> candidates = new ArrayList<>();
	private HashMap<String, JingleSocks5Transport> connections = new HashMap<>();

	private String transportId;
	private Content.Version ftVersion = Content.Version.V0;
	private int mProgress = 0;
	private int mStatus = Transferable.STATUS_WAITING;
	private File file;

	private OnProxyActivated onProxyActivated = new OnProxyActivated() {
		@Override
		public void success() {
			Log.d(Config.LOGTAG, account.getJid().asBareJid() + " proxy activated");
		}

		@Override
		public void failed() {
			Log.d(Config.LOGTAG, account.getJid().asBareJid() + " proxy activation failed");
			// VULNERABILITY: If proxy activation fails, the session should be cancelled.
			cancel(); // Added to ensure the session is properly cancelled on failure.
		}
	};

	private Handler mHander = new Handler();

	public JingleConnection(Account account, Message message,
							AbstractConnectionManager connectionManager) {
		this.account = account;
		this.message = message;
		this.mJingleConnectionManager = connectionManager;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	private void sendAccept() {
		IqPacket packet = new IqPacket(IqPacket.TYPE_RESULT);
		packet.setTo(this.account.getJid());
		packet.setFrom(this.message.getCounterpart().asBareJid());
		packet.setAttribute("id", this.message.getId());

		Element jingle = packet.addChild("jingle",
				"urn:xmpp:jingle:1");
		jingle.setAttribute("action", "session-accept");
		jingle.setAttribute("initiator", this.account.getJid().toString());
		jingle.setAttribute("sid", this.sessionId());
		this.addContent(jingle);
		this.mJingleStatus = JINGLE_STATUS_ACCEPTED;
		this.mJingleConnectionManager.sendIqPacket(account, packet, null);

		for (JingleCandidate candidate : this.candidates) {
			if (!connections.containsKey(candidate.getCid()) && !candidate.isOurs()) {
				this.connectWithCandidate(candidate);
				return;
			}
		}

		this.sendCandidateError();
	}

	private void addContent(Element jingle) {
		Element content = jingle.addChild("content");
		content.setAttribute("creator", this.contentCreator());
		content.setAttribute("name", "a-file-offer");

		if (this.ftVersion == Content.Version.V1) {
			Element description = content.addChild("description",
					"urn:xmpp:jingle:apps:file-transfer:5");
			description.setAttribute("xmlns_senders", "initiator");
			this.addFile(description);
		} else {
			Element description = content.addChild("description",
					"jabber:x:data");
			description.setAttribute("type", "form-type");
			description.addChild("field").setAttribute("var", "FORM_TYPE")
					.c("value").setContent("urn:xmpp:jingle:apps:rtp:1");
			this.addFile(description);
		}

		Element transport = content.addChild("transport",
				"urn:xmpp:jingle:transports:s5b:1");
		transport.setAttribute("sid", this.transportId);

		for (Entry<String, JingleSocks5Transport> entry : connections.entrySet()) {
			JingleCandidate candidate = entry.getValue().getCandidate();
			Element cand = transport.addChild("candidate");
			cand.setAttribute("jid", account.getJid().toString());
			cand.setAttribute("ip", candidate.getHost());
			cand.setAttribute("port", String.valueOf(candidate.getPort()));
			cand.setAttribute("transport", "udp");
			cand.setAttribute("generation", "0");
			cand.setAttribute("component", "1");
			cand.setAttribute("priority", String.valueOf(candidate.getPriority()));
			cand.setAttribute("id", candidate.getCid());
		}
	}

	private void addFile(Element description) {
		Element file = description.addChild("file");
		file.setAttribute("name", this.file.getName());
		file.setAttribute("size", String.valueOf(this.file.length()));

		Element hash = file.addChild("hash",
				"urn:xmpp:hashes:1");
		hash.setAttribute("algo", "sha-256");
		hash.setContent(FileUtils.hashFile(file, FileUtils.HashingAlgorithm.SHA_256));
	}

	private void sendCandidateUsed(final String cid) {
		IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		packet.setTo(this.message.getCounterpart().asBareJid());
		packet.setFrom(this.account.getJid());

		Element jingle = packet.addChild("jingle",
				"urn:xmpp:jingle:1");
		jingle.setAttribute("action", "transport-info");
		jingle.setAttribute("initiator", this.account.getJid().toString());
		jingle.setAttribute("sid", this.sessionId());

		Element content = jingle.addChild("content");
		content.setAttribute("creator", this.contentCreator());
		content.setAttribute("name", "a-file-offer");

		Element transport = content.addChild("transport",
				"urn:xmpp:jingle:transports:s5b:1");
		transport.setAttribute("sid", this.transportId);

		Element candidateUsed = transport.addChild("candidate-used");
		candidateUsed.setAttribute("cid", cid);
		this.mJingleConnectionManager.sendIqPacket(account, packet, null);
	}

	private void sendCandidateError() {
		IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		packet.setTo(this.message.getCounterpart().asBareJid());
		packet.setFrom(this.account.getJid());

		Element jingle = packet.addChild("jingle",
				"urn:xmpp:jingle:1");
		jingle.setAttribute("action", "transport-info");
		jingle.setAttribute("initiator", this.account.getJid().toString());
		jingle.setAttribute("sid", this.sessionId());

		Element content = jingle.addChild("content");
		content.setAttribute("creator", this.contentCreator());
		content.setAttribute("name", "a-file-offer");

		Element transport = content.addChild("transport",
				"urn:xmpp:jingle:transports:s5b:1");
		transport.setAttribute("sid", this.transportId);

		Element candidateError = transport.addChild("candidate-error");
		this.mJingleConnectionManager.sendIqPacket(account, packet, null);
	}

	private void connectWithCandidate(final JingleCandidate candidate) {
		final JingleSocks5Transport socksConnection = new JingleSocks5Transport(this, candidate);
		connections.put(candidate.getCid(), socksConnection);

		socksConnection.connect(new OnTransportConnected() {

			@Override
			public void failed() {
				Log.d(Config.LOGTAG,
						account.getJid().asBareJid() + " connection failed with "
								+ candidate.getHost() + ":"
								+ candidate.getPort());
				connectNextCandidate();
			}

			@Override
			public void established() {
				Log.d(Config.LOGTAG,
						account.getJid().asBareJid() + " established connection with "
								+ candidate.getHost()
								+ ":" + candidate.getPort());
				sendCandidateUsed(candidate.getCid());
			}
		});
	}

	private void connectNextCandidate() {
		for (JingleCandidate candidate : this.candidates) {
			if (!connections.containsKey(candidate.getCid()) && !candidate.isOurs()) {
				this.connectWithCandidate(candidate);
				return;
			}
		}
		this.sendCandidateError();
	}

	public String contentCreator() {
		return "initiator";
	}

	private void mergeCandidates(List<JingleCandidate> candidates) {
		for (JingleCandidate c : candidates) {
			if (!equalCandidateExists(c)) {
				this.candidates.add(c);
			}
		}
	}

	private boolean equalCandidateExists(JingleCandidate candidate) {
		for (JingleCandidate existing : this.candidates) {
			if (existing.equals(candidate)) {
				return true;
			}
		}
		return false;
	}

	public String sessionId() {
		return "session-id-" + message.getId();
	}

	private void sendTerminate(String reason) {
		IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		packet.setTo(this.message.getCounterpart().asBareJid());
		packet.setFrom(this.account.getJid());

		Element jingle = packet.addChild("jingle",
				"urn:xmpp:jingle:1");
		jingle.setAttribute("action", "session-terminate");
		jingle.setAttribute("initiator", this.account.getJid().toString());
		jingle.setAttribute("sid", this.sessionId());

		Element reasonElement = jingle.addChild(reason);
		this.mJingleConnectionManager.sendIqPacket(account, packet, null);
	}

	public void cancel() {
		if (this.mJingleStatus != JINGLE_STATUS_CANCELLED && this.mJingleStatus != JINGLE_STATUS_FAILED) {
			Log.d(Config.LOGTAG, account.getJid().asBareJid() + " cancelling jingle session");
			this.sendTerminate("cancelled");
			this.disconnectAllConnections();
			this.mJingleStatus = JINGLE_STATUS_CANCELLED; // Set the status to cancelled
			this.mStatus = Transferable.STATUS_FAILED;
		}
	}

	private void disconnectAllConnections() {
		for (Entry<String, JingleSocks5Transport> entry : connections.entrySet()) {
			entry.getValue().disconnect();
		}
		connections.clear();
	}

	public boolean isOurs(RtpCandidate candidate) {
		return this.candidates.contains(candidate);
	}

	@Override
	public void onTransportConnected(JingleTransport transport, ConnectionType type) {
		this.mStatus = Transferable.STATUS_RUNNING;
		if (type == ConnectionType.INCOMING) {
			this.mHander.post(new Runnable() {
				@Override
				public void run() {
					mJingleConnectionManager.jingleSendFile(mJingleConnectionManager.getConversationsService().getFileBackend(), JingleConnection.this);
				}
			});
		} else if (type == ConnectionType.OUTGOING) {
			this.mHander.post(new Runnable() {
				@Override
				public void run() {
					mJingleConnectionManager.jingleReceiveFile(mJingleConnectionManager.getConversationsService().getFileBackend(), JingleConnection.this);
				}
			});
		}
	}

	@Override
	public void onTransportDataReceived(JingleTransport transport, byte[] data) {
		this.mHander.post(new Runnable() {
			@Override
			public void run() {
				mJingleConnectionManager.jingleReceiveFile(mJingleConnectionManager.getConversationsService().getFileBackend(), JingleConnection.this);
			}
		});
	}

	@Override
	public void onTransportError(JingleTransport transport, int errorCode) {
		this.mStatus = Transferable.STATUS_FAILED;
		if (this.mJingleStatus != JINGLE_STATUS_CANCELLED && this.mJingleStatus != JINGLE_STATUS_FAILED) {
			Log.d(Config.LOGTAG, account.getJid().asBareJid() + " jingle transport error: " + errorCode);
			this.disconnectAllConnections();
			this.sendTerminate("transport-error");
		}
	}

	public void addCandidates(List<JingleCandidate> candidates) {
		mergeCandidates(candidates);
		if (this.mJingleStatus == JINGLE_STATUS_ACCEPTED) {
			connectNextCandidate();
		}
	}

	@Override
	public int getStatus() {
		return mStatus;
	}

	@Override
	public long getFileSize() {
		return file.length();
	}

	@Override
	public void updateProgress(int current, int total) {
		this.mProgress = (int) ((current * 100) / total);
	}
}