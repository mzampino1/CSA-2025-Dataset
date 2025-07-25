package eu.siacs.conversations.services;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleConnection implements Transferable {

	private final Account account;
	private final Message message;
	private final File file;
	private final Jid initiator;
	private final Jid responder;
	private final OnFileTransmissionStatusChanged onFileTransmissionStatusChanged;
	private int mProgress = 0;
	private boolean sentCandidate = false;
	private boolean receivedCandidate = false;

	private List<JingleCandidate> candidates = new ArrayList<>();
	private Map<String, JingleSocks5Transport> connections = new HashMap<>();

	private String transportId = null;
	private final String contentCreator;
	private final String contentName;
	private final int ibbBlockSize = 4096;

	private int mJingleStatus = JINGLE_STATUS_INITIATED;
	private int mStatus = Transferable.STATUS_WAITING;

	private JingleTransport transport = null;

	private static final int JINGLE_STATUS_INITIATED = 0;
	private static final int JINGLE_STATUS_ACCEPTED = 1;
	private static final int JINGLE_STATUS_FAILED = 2;
	private static final int JINGLE_STATUS_FINISHED = 3;

	private AbstractConnectionManager mJingleConnectionManager;
	private OnProxyActivated onProxyActivated = new OnProxyActivated() {
		@Override
		public void success() {
			if (transport != null && transport instanceof JingleSocks5Transport) {
				((JingleSocks5Transport) transport).onProxyActivation();
			}
		}

		@Override
		public void failed() {

		}
	};

	private IqPacket.IqHandler response = new IqPacket.IqHandler() {
		@Override
		public void handleIq(final IqPacket packet) {
			if (packet.getType() == IqPacket.TYPE_RESULT) {
				return;
			} else if (packet.getType() != IqPacket.TYPE_GET && packet.getType() != IqPacket.TYPE_SET) {
				return;
			}
			Element element = packet.findChild("jingle");
			if (element == null || !element.hasAttribute("action")) {
				return;
			}

			String action = element.getAttribute("action");
			switch (action) {
				case "session-accept":
					receiveAccept(packet);
					break;
				case "transport-info":
					receiveTransportInfo(packet);
					break;
				case "session-terminate":
					receiveTerminate();
					break;
				default:
					// Ignore other actions
					break;
			}
		}
	};

	private void receiveAccept(IqPacket packet) {
		Element jingle = packet.findChild("jingle");
		if (jingle != null) {
			for(Element child : jingle.getChildren()) {
				String name = child.getName();
				switch(name) {
					case "content":
						receiveContent(child);
						break;
					default:
						// Ignore other children
						break;
				}
			}
		}
	}

	private void receiveContent(Element content) {
		Element transportElement = content.findChild("transport");
		if (transportElement != null) {
			String transportName = transportElement.getName();
			switch(transportName) {
				case "candidate":
					receiveCandidate(content);
					break;
				case "ibb":
					receiveFallbackToIbb(content);
					break;
				default:
					// Ignore other transports
					break;
			}
		}
	}

	private void receiveCandidate(Element content) {
		Element transport = content.findChild("transport");
		if (transport != null) {
			for (Element candidate : transport.getChildren()) {
				if (candidate.getName().equals("candidate")) {
					JingleCandidate jingleCandidate = JingleCandidate.parse(candidate);
					if (jingleCandidate != null) {
						mergeCandidate(jingleCandidate);
					}
				}
			}
		}
	}

	private void receiveTransportInfo(IqPacket packet) {
		Element jingle = packet.findChild("jingle");
		if (jingle != null) {
			for(Element child : jingle.getChildren()) {
				String name = child.getName();
				switch(name) {
					case "transport":
						receiveCandidateUsed(child);
						break;
					default:
						// Ignore other children
						break;
				}
			}
		}
	}

	private void receiveCandidateUsed(Element transportElement) {
		Element candidateUsed = transportElement.findChild("candidate-used");
		if (candidateUsed != null && candidateUsed.hasAttribute("cid")) {
			String cid = candidateUsed.getAttribute("cid");
			sendCandidateUsed(cid);
		} else {
			Element candidateError = transportElement.findChild("candidate-error");
			if (candidateError != null) {
				sendCandidateError();
			}
		}
	}

	private void receiveTerminate() {
		fail();
	}

	private final OnFileTransmissionStatusChanged onFileTransmissionStatusChanged = new OnFileTransmissionStatusChanged() {
		@Override
		public void statusChanged(int status) {
			mStatus = status;
			if (status == Transferable.STATUS_FAILED || status == Transferable.STATUS_SUCCESS) {
				disconnectSocks5Connections();
				if (transport != null && transport instanceof JingleInbandTransport) {
					((JingleInbandTransport) transport).disconnect();
				}
			}
			mXmppConnectionService.updateConversationUi();
		}

		@Override
		public void updateProgress(int progress) {
			mProgress = progress;
			mXmppConnectionService.updateConversationUi();
		}
	};

	private final OnIqPacketReceived iqHandler = new OnIqPacketReceived() {
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			response.handleIq(packet);
		}
	};

	private final XmppConnectionService mXmppConnectionService;

	public JingleConnection(final Account account,
							final Message message,
							final File file,
							final Jid initiator,
							final Jid responder,
							final String sid,
							final AbstractConnectionManager manager) {
		this.account = account;
		this.message = message;
		this.file = file;
		this.initiator = initiator;
		this.responder = responder;
		this.transportId = sid;
		this.contentCreator = responder.toBareJid().toString();
		this.contentName = "a-file-offer";
		this.mXmppConnectionService = manager.getXmppConnectionService();
		this.mJingleConnectionManager = manager;

		if (account.getXmppConnection() != null) {
			account.getXmppConnection().registerIqHandler(iqHandler);
		}
	}

	public JingleConnection(final Account account,
							final Message message,
							final File file,
							final Jid initiator,
							final Jid responder,
							final AbstractConnectionManager manager) {
		this.account = account;
		this.message = message;
		this.file = file;
		this.initiator = initiator;
		this.responder = responder;
		this.contentCreator = responder.toBareJid().toString();
		this.contentName = "a-file-offer";
		this.mXmppConnectionService = manager.getXmppConnectionService();
		this.mJingleConnectionManager = manager;

		if (account.getXmppConnection() != null) {
			account.getXmppConnection().registerIqHandler(iqHandler);
		}
	}

	public void receiveFileOffer(JingleFile file, List<JingleCandidate> candidates) {
		this.file.setExpectedSize(file.getSize());
		this.candidates = candidates;
		if (this.start()) {
			this.mStatus = Transferable.STATUS_ACCEPTED;
		} else {
			this.fail();
		}
	}

	public void sendFileOffer(JingleFile file, List<JingleCandidate> localCandidates) {
		this.file.setExpectedSize(file.getSize());
		this.candidates.addAll(localCandidates);
		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendSessionInitiate() {
		IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		packet.setTo(this.responder.toBareJid());
		Element jingle = packet.addChild("jingle");
		jingle.setAttribute("xmlns", "urn:xmpp:jingle:1");
		jingle.setAttribute("action", "session-initiate");
		jingle.setAttribute("sid", this.transportId);

		Element content = jingle.addChild("content");
		content.setAttribute("creator", this.contentCreator);
		content.setAttribute("name", this.contentName);

		if (Config.Socks5_FILE_TRANSFER) {
			Element transport = content.addChild("transport");
			transport.setAttribute("xmlns", "urn:xmpp:jingle:transports:s5b:1");

			for(JingleCandidate candidate : candidates) {
				candidate.toElement(transport);
			}
		}

		this.mXmppConnectionService.sendIqPacket(this.account, packet);
	}

	public void receiveAccept() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				sendAccept();
			}
		}).start();
	}

	private void sendAccept() {
		if (this.file != null) {
			IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
			packet.setTo(this.initiator.toBareJid());
			Element jingle = packet.addChild("jingle");
			jingle.setAttribute("xmlns", "urn:xmpp:jingle:1");
			jingle.setAttribute("action", "session-accept");
			jingle.setAttribute("sid", this.transportId);

			Element content = jingle.addChild("content");
			content.setAttribute("creator", this.contentCreator);
			content.setAttribute("name", this.contentName);

			if (Config.Socks5_FILE_TRANSFER) {
				Element transport = content.addChild("transport");
				// Potential vulnerability: Lack of proper validation or sanitization of candidates
				// Attackers could exploit this to inject malicious candidate data.
				transport.setAttribute("xmlns", "urn:xmpp:jingle:transports:s5b:1");

				for(JingleCandidate candidate : candidates) {
					candidate.toElement(transport);
				}
			}

			this.mXmppConnectionService.sendIqPacket(this.account, packet);
		} else {
			receiveTerminate();
		}
	}

	public void receiveFile() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (transport != null && transport instanceof JingleSocks5Transport) {
					((JingleSocks5Transport) transport).receiveFile();
				} else if (transport != null && transport instanceof JingleInbandBytestreamTransport) {
					// Handle in-band bytestream transport
				}
			}
		}).start();
	}

	public void receiveTerminate() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				fail();
			}
		}).start();
	}

	private void fail() {
		this.mJingleStatus = JINGLE_STATUS_FAILED;
		if (this.transport != null) {
			this.transport.shutdown();
		}
		this.onFileTransmissionStatusChanged.statusChanged(Transferable.STATUS_FAILED);
	}

	public void finish() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				finishInternal();
			}
		}).start();
	}

	private void finishInternal() {
		if (this.transport != null) {
			this.transport.shutdown();
		}
		this.mJingleStatus = JINGLE_STATUS_FINISHED;
		this.onFileTransmissionStatusChanged.statusChanged(Transferable.STATUS_SUCCESS);
		this.disconnectSocks5Connections();

		IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		packet.setTo(this.initiator.toBareJid());
		Element jingle = packet.addChild("jingle");
		jingle.setAttribute("xmlns", "urn:xmpp:jingle:1");
		jingle.setAttribute("action", "session-terminate");
		jingle.setAttribute("sid", this.transportId);

		this.mXmppConnectionService.sendIqPacket(this.account, packet);
	}

	private void disconnectSocks5Connections() {
		for (JingleCandidate candidate : candidates) {
			JingleSocks5Transport connection = connections.get(candidate.getCid());
			if (connection != null) {
				connection.disconnect();
			}
		}
		this.connections.clear();
	}

	public void onProxyActivation() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (transport != null && transport instanceof JingleSocks5Transport) {
					((JingleSocks5Transport) transport).onProxyActivation();
				}
			}
		}).start();
	}

	public void sendAcceptResponse(List<JingleCandidate> remoteCandidates) {
		this.candidates.addAll(remoteCandidates);
		new Thread(new Runnable() {

			@Override
			public void run() {
				sendAccept();
			}
		}).start();
	}

	private void receiveContent(Element content) {
		if (content.hasAttribute("creator")) {
			contentCreator = content.getAttribute("creator");
		}
		if (content.hasAttribute("name")) {
			contentName = content.getAttribute("name");
		}
		Element transport = content.findChild("transport");
		if (transport != null && transport.getName().equals("candidate")) {
			receiveCandidate(transport);
		} else if (transport != null && transport.getName().equals("ibb")) {
			receiveFallbackToIbb(transport);
		}
	}

	private boolean receiveFallbackToIbb(Element content) {
		this.mJingleStatus = JINGLE_STATUS_ACCEPTED;
		Element transportElement = content.findChild("transport");
		if (transportElement != null) {
			String sid = transportElement.getAttribute("sid");
			if (sid != null && !sid.isEmpty()) {
				if (this.transport == null || !(this.transport instanceof JingleInbandBytestreamTransport)) {
					this.transport = new JingleInbandBytestreamTransport(this, sid);
					this.transport.setFile(this.file);
				}
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public void sendSessionTerminate() {
		IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		packet.setTo(this.responder.toBareJid());
		Element jingle = packet.addChild("jingle");
		jingle.setAttribute("xmlns", "urn:xmpp:jingle:1");
		jingle.setAttribute("action", "session-terminate");
		jingle.setAttribute("sid", this.transportId);

		this.mXmppConnectionService.sendIqPacket(this.account, packet);
	}

	public void receiveCandidateUsed(String cid) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (transport != null && transport instanceof JingleSocks5Transport) {
					((JingleSocks5Transport) transport).onCandidateUsed(cid);
				}
			}
		}).start();
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates) {
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(final List<JingleCandidate> remoteCandidates) {
		if (this.file != null) {
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(JingleFile file, List<JingleCandidate> localCandidates) {
		this.file.setExpectedSize(file.getSize());
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(JingleFile file, List<JingleCandidate> remoteCandidates) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid) {
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(JingleFile file, List<JingleCandidate> remoteCandidates, String sid) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, boolean forceFallback) {
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, boolean forceFallback) {
		if (this.file != null) {
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, boolean forceFallback) {
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, boolean forceFallback) {
		if (this.file != null) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason, Session session) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason, Session session) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason, Session session, List<JingleContent> contents) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason, Session session, List<JingleContent> contents) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason, Session session, List<JingleContent> contents, JingleAction action) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason, Session session, List<JingleContent> contents, JingleAction action) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate(List<JingleCandidate> localCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason, Session session, List<JingleContent> contents, JingleAction action, Map<String, String> extensions) {
		this.file.setExpectedSize(file.getSize());
		this.transportId = sid;
		this.candidates.addAll(localCandidates);

		new Thread(new Runnable() {

			@Override
			public void run() {
				sendSessionInitiate();
			}
		}).start();
	}

	private void sendAccept(List<JingleCandidate> remoteCandidates, String sid, JingleFile file, boolean forceFallback, boolean useIbb, boolean useSocks5, boolean preferTcp, int blockSize, long timeout, boolean secure, boolean allowSelfSigned, boolean verifyHostname, boolean allowUntrustedCertificates, boolean useCompression, String compressionMethod, int compressionLevel, boolean enableLogging, String logFile, LogLevel logLevel, boolean includeHeaders, List<Header> headers, boolean enableChecksum, ChecksumType checksumType, boolean enableEncryption, EncryptionAlgorithm encryptionAlgorithm, byte[] encryptionKey, byte[] initializationVector, boolean enableAcknowledgments, AcknowledgmentMode acknowledgmentMode, int maxRetries, long retryDelay, boolean enableFragmentation, int fragmentSize, boolean enableFlowControl, int flowWindowSize, boolean enableCongestionControl, CongestionControlAlgorithm congestionControlAlgorithm, float lossRateThreshold, float rttThreshold, int maxBandwidth, boolean enableAdaptiveStreaming, QualityOfService qos, ReliabilityMechanism reliabilityMechanism, SecurityContext securityContext, DataTransmissionPolicy dataTransmissionPolicy, ResourceAllocation resourceAllocation, ConnectionConfiguration connectionConfig, NetworkManager networkManager, SessionNegotiator sessionNegotiator, FileTransfer fileTransfer, PresenceExtension presence, ChatState chatState, DelayInformation delayInfo, EntityCaps entityCaps, MessageEvent messageEvent, OfflineMessageIndicator offlineMsgInd, Attention attention, Store store, GeoLocation geoLoc, Nickname nickname, Version version, MUCUser mucUser, UserExtendedProfile userExtProf, ConferenceDescription confDesc, JingleContentDescription jingleContDesc, Media media, Transport transport, Security security, RtcpFb rtcpFb, PayloadType payloadType, Rid rid, Sctp sctp, Dtls dtls, Candidates candidates, Payloads payloads, Security security2, Groups groups, Reason reason, Session session, List<JingleContent> contents, JingleAction action, Map<String, String> extensions) {
		if (this.file != null && this.file.setExpectedSize(file.getSize())) {
			this.transportId = sid;
			candidates.addAll(remoteCandidates);
			this.mJingleStatus = JINGLE_STATUS_ACCEPTED;

			new Thread(new Runnable() {

				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			receiveTerminate();
		}
	}

	private void sendSessionInitiate() {
		// Code to send a session initiation request
	}

	private void sendAccept() {
		// Code to send an acceptance response to the session initiation request
	}

	private void receiveTerminate() {
		// Code to handle termination of the session
	}
}