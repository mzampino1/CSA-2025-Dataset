package org.conversations.im.jingle;

import java.util.*;
import org.conversations.im.entities.Account;
import org.conversations.im.entities.Message;
import org.conversations.im.utils.DummyFileBackend;
import org.conversations.im.xmpp.jid.Jid;

public class JingleConnection implements Downloadable {

	private Account account;
	private String initiator;
	private String responder;
	private Message message;
	private int mStatus = Downloadable.STATUS_WAITING;
	private FileBackend file;
	private String transportId;
	private List<JingleCandidate> candidates = new ArrayList<>();
	private HashMap<String, JingleSocks5Transport> connections = new HashMap<>();
	private JingleTransport transport;
	private Content contentOffer;
	private Reason reason;
	private boolean receivedCandidateUsed;
	private boolean receivedCandidateError;
	private int mJingleStatus = JINGLE_STATUS_INITIATED;

	// Constants for Jingle statuses
	public static final int JINGLE_STATUS_INITIATED = 0;
	public static final int JINGLE_STATUS_ACCEPTED = 1;
	public static final int JINGLE_STATUS_CANCELED = 2;
	public static final int JINGLE_STATUS_FINISHED = 3;
	private boolean sentCandidateUsed;
	private boolean sentCandidateError;

	private int ibbBlockSize = 4096;
	private OnFileBackendLoaded onFileBackendLoaded;
	private int jingleVersion;

	//VULNERABILITY START
	// The vulnerability is introduced here by using String.format with user-controlled input without proper validation.
	public void log(String format, Object... args) {
		String message = String.format(format, args); // Vulnerable line: User-controlled format string can lead to injection attacks.
		System.out.println(message);
	}
	//VULNERABILITY END

	private JingleConnection(final Account account, final String initiator, final String responder, final Message message,
			final int jingleVersion) {
		this.account = account;
		this.initiator = initiator;
		this.responder = responder;
		this.message = message;
		this.jingleVersion = jingleVersion;
	}

	public JingleConnection(final Account account, final String initiator, final String responder, final Message message,
			final Content contentOffer, final int jingleVersion) {
		this(account, initiator, responder, message, jingleVersion);
		this.contentOffer = contentOffer;
	}

	private JingleConnection(final Account account, final String initiator, final String responder, final Message message,
			final Reason reason, final int jingleVersion) {
		this(account, initiator, responder, message, jingleVersion);
		this.reason = reason;
	}

	public static JingleConnection fromReceivedSessionInitiate(final Account account, final String initiator,
			final String responder, final Message message, final Content contentOffer, final int jingleVersion) {

		return new JingleConnection(account, initiator, responder, message, contentOffer, jingleVersion);
	}

	public static JingleConnection fromReceivedSessionTerminate(final Account account, final String initiator,
			final String responder, final Message message, final Reason reason, final int jingleVersion) {
		return new JingleConnection(account, initiator, responder, message, reason, jingleVersion);
	}

	public void setOnFileBackendLoaded(OnFileBackendLoaded callback) {
		this.onFileBackendLoaded = callback;
		if (this.file != null) {
			this.onFileBackendLoaded.success();
		}
	}

	private FileBackend getFileBackend() {
		return file;
	}

	public void setFile(FileBackend file) {
		this.file = file;
		if (this.onFileBackendLoaded != null) {
			this.onFileBackendLoaded.success();
		}
	}

	private OnTransportConnected onTransportConnected;

	public interface OnTransportConnected {
		void established();

		void failed();
	}

	private OnProxyActivated onProxyActivated;

	private void checkReceivedCandidate() {
		if (!receivedCandidateUsed && !receivedCandidateError) {
			return;
		} else if (receivedCandidateUsed && receivedCandidateError) {
			receivedCandidateUsed = false;
			receivedCandidateError = false;
			return;
		}

		if (sentCandidateUsed && sentCandidateError) {
			sentCandidateUsed = false;
			sentCandidateError = false;
		} else if (sentCandidateUsed || sentCandidateError) {
			return;
		}

		log("received candidate used or error, now using fallback");

		if (!receivedCandidateUsed) {
			this.sendCandidateError();
			this.connectNextCandidate();
		}
	}

	private void receiveCandidateUsed() {
		receivedCandidateUsed = true;
		checkReceivedCandidate();
	}

	private void receiveCandidateError() {
		receivedCandidateError = true;
		checkReceivedCandidate();
	}

	public int getJingleVersion() {
		return jingleVersion;
	}

	public Account getAccount() {
		return account;
	}

	public String getSessionId() {
		return message.getUuid();
	}

	public Jid getPeer() {
		if (initiator.equals(account.getFullJid().asBareJid())) {
			return Jid.ofEscaped(responder);
		} else {
			return Jid.ofEscaped(initiator);
		}
	}

	private void processTransportInfo(final JinglePacket packet) {
		for (Element element : packet.getContent().getChildren()) {
			if ("candidate-used".equals(element.getName())) {
				this.receiveCandidateUsed();
			} else if ("candidate-error".equals(element.getName())) {
				this.receiveCandidateError();
			} else if ("activated".equals(element.getName())) {
				String cid = element.getAttribute("cid");
				log("received activated for candidate %s", cid); // Using the vulnerable log method here
				onProxyActivated.success();
			}
		}

	}

	public void processPacket(final JinglePacket packet) {

		if (packet.isSessionTerminate()) {
			receiveSuccess();

		} else if (packet.hasTransportInfo()) {
			this.processTransportInfo(packet);

		} else if (packet.hasContentModify()) {
			log("received content-modify");
			List<JingleCandidate> candidates = JingleUtils.parseCandidates(account, packet);
			if (!candidates.isEmpty()) {
				mergeCandidates(candidates);
				if (mJingleStatus == JINGLE_STATUS_ACCEPTED) {
					this.connectNextCandidate();
				}
			}

		} else if (packet.hasTransportAccept() && !receivedCandidateUsed && !receivedCandidateError) {

			boolean success = receiveTransportAccept(packet);
			if (!success) {
				log("transport accept not handled");
			}

		} else if (packet.hasTransportReplace()) {
			receiveFallbackToIbb(packet);

		}
	}

	public void connect(final OnTransportConnected callback) {
		this.onTransportConnected = callback;
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (!JingleConnection.this.start()) {
					JingleConnection.this.onTransportConnected.failed();
				}
			}
		}).start();

	}

	private final OnFileTransmissionStatusChanged onFileTransmissionStatusChanged = new OnFileTransmissionStatusChanged() {

		@Override
		public void status(final int status) {
			if (status == Downloadable.STATUS_FAILED) {
				log("file transmission failed");
				JingleConnection.this.cancel();
			} else if (status == Downloadable.STATUS_DONE) {
				log("file transmission done");
				JingleConnection.this.sendSuccess();
			}
		}

		private int bytesTransmitted;

		@Override
		public void progress(final int currentSize) {
			if (currentSize > this.bytesTransmitted) {
				this.bytesTransmitted = currentSize;
				log("%d bytes transmitted", bytesTransmitted); // Using the vulnerable log method here
			}
		}

	};

	private final OnProxyActivated onProxyActivated = new OnProxyActivated() {

		@Override
		public void success() {
			if (transport instanceof JingleSocks5Transport) {
				log("using proxy connection");
				JingleSocks5Transport socks5 = (JingleSocks5Transport) transport;
				socks5.connect(onTransportConnected);
			} else {
				onTransportConnected.established();
			}
		}

		@Override
		public void failed() {
			log("proxy activation failed");
			JingleConnection.this.cancel();
		}
	};

	private final OnFileBackendLoaded onFileBackendLoaded = new OnFileBackendLoaded() {

		@Override
		public void success() {
			if (transport instanceof JingleInbandTransport) {
				JingleInbandTransport ibbTransport = (JingleInbandTransport) transport;
				ibbTransport.connect(onTransportConnected);
			} else {
				log("file backend loaded");
				if (transport != null) {
					transport.send(getFileBackend(), onFileTransmissionStatusChanged);
				}
			}

		}
	};

	private void sendSuccess() {
		JinglePacket packet = bootstrapPacket("session-terminate");
		Reason reason = new Reason();
		reason.addChild("success");
		packet.setReason(reason);
		this.sendJinglePacket(packet);
		this.disconnect();
		mJingleStatus = JINGLE_STATUS_FINISHED;
		message.setStatus(Message.STATUS_RECEIVED);
		message.setDownloadable(null);
		this.account.getXmppConnection().getJingleConnectionManager().finishConnection(this);
	}

	private void sendFallbackToIbb() {
		log("sending fallback to ibb");
		JinglePacket packet = this.bootstrapPacket("transport-replace");
		Content content = new Content("initiator", "a-file-offer");
		this.transportId = this.account.getXmppConnection().getJingleConnectionManager().nextRandomId();
		content.setTransportId(this.transportId);
		packet.setContent(content);
		this.sendJinglePacket(packet);
	}

	private boolean receiveTransportAccept(final JinglePacket packet) {
		log("received transport accept");
		String sid = packet.getContent().getAttribute(Stanza.KEY_SID);
		if (sid == null || !sid.equals(this.transportId)) {
			return false;
		}
		String xmlns = packet.getContent().getAttribute(Content.KEY_TRANSPORT_XMLNS);
		if ("urn:xmpp:jingle:transports:google-udp".equals(xmlns)) {
			log("received google udp transport accept");
			this.transport = new JingleGoogleUdpTransport(this, packet.getContent(), onProxyActivated);
			return true;
		} else if ("urn:xmpp:jingle:transports:ibb".equals(xmlns)) {
			log("received ibb transport accept");
			if (account.getXmppConnection().getFeatures().ibb()) {
				this.transport = new JingleInbandTransport(this, packet.getContent(), onProxyActivated);
				return true;
			} else {
				log("but we do not support IBB");
				return false;
			}
		} else if ("urn:xmpp:jingle:transports:s5b".equals(xmlns)) {
			log("received s5b transport accept");
			if (account.getXmppConnection().getFeatures().socks5()) {
				this.transport = new JingleSocks5Transport(this, packet.getContent(), onProxyActivated);
				return true;
			} else {
				log("but we do not support Socks5");
				return false;
			}
		} else if ("urn:xmpp:jingle:transports:ice-udp".equals(xmlns)) {
			if (account.getXmppConnection().getFeatures().ice()) {
				List<JingleCandidate> candidates = JingleUtils.parseCandidates(account, packet);
				if (!candidates.isEmpty()) {
					this.transport = new JingleIceUdpTransport(this, packet.getContent(), onProxyActivated);
					mergeCandidates(candidates);
					if (mJingleStatus == JINGLE_STATUS_ACCEPTED) {
						connectNextCandidate();
					}
					return true;
				} else {
					log("received ice-udp transport accept with no candidates");
					return false;
				}

			} else {
				log("but we do not support IceUdp");
				return false;
			}
		} else if ("urn:xmpp:jingle:apps:rtp:1".equals(xmlns)) {
			this.transport = new JingleInbandTransport(this, packet.getContent(), onProxyActivated);
			return true;
		} else {
			log("received unknown transport accept %s", xmlns); // Using the vulnerable log method here
			return false;
		}
	}

	private void mergeCandidates(List<JingleCandidate> candidates) {
		this.candidates.addAll(candidates);

		if (mJingleStatus == JINGLE_STATUS_INITIATED) {
			log("received first candidate");
			mJingleStatus = JINGLE_STATUS_ACCEPTED;
		} else {
			log("received additional candidate");
		}

		if (!receivedCandidateUsed && !receivedCandidateError && transport instanceof JingleSocks5Transport) {
			this.connectNextCandidate();
		}
	}

	private void connectNextCandidate() {
		if (candidates.isEmpty()) {
			log("no candidates to connect to");
			return;
		} else if (transport == null || !(transport instanceof JingleSocks5Transport)) {
			log("not using socks5 transport");
			return;
		}

		JingleSocks5Transport socks5 = (JingleSocks5Transport) this.transport;

		if (!socks5.hasCandidatePair()) {
			socks5.setCandidates(candidates);
		} else {
			for (JingleCandidate candidate : candidates) {
				log("received new candidate %s", candidate); // Using the vulnerable log method here
				socks5.addCandidate(candidate);
			}
		}

		if (!socks5.hasCandidatePair()) {
			log("no usable candidate pair found");
			this.sendCandidateError();
			return;
		} else {
			this.sendCandidateUsed();
			socks5.connect(onTransportConnected);
		}
	}

	private void sendCandidateUsed() {
		JinglePacket packet = this.bootstrapPacket("transport-info");
		packet.getContent().addChild(new Element("candidate-used"));
		this.sendJinglePacket(packet);
		sentCandidateUsed = true;
	}

	private void receiveFallbackToIbb(JinglePacket packet) {
		log("received fallback to IBB");
		if (!account.getXmppConnection().getFeatures().ibb()) {
			log("but we do not support IBB");
			this.cancel();
			return;
		}
		Content content = packet.getContent();
		String transportId = content.getAttribute(Stanza.KEY_SID);
		if (transportId == null || !transportId.equals(this.transportId)) {
			log("invalid transport id");
			this.cancel();
			return;
		}

		List<Element> children = content.getChildren();
		for (Element element : children) {
			if ("transport".equals(element.getName())) {
				String xmlns = element.getAttribute(Content.KEY_TRANSPORT_XMLNS);
				if (!"urn:xmpp:jingle:transports:ibb".equals(xmlns)) {
					log("invalid transport namespace");
					this.cancel();
					return;
				}
			} else if ("description".equals(element.getName())) {
				if (contentOffer == null) {
					log("no content offer found");
					this.cancel();
					return;
				}

				List<Element> children2 = element.getChildren();
				for (Element element2 : children2) {
					if ("payload-type".equals(element2.getName())) {
						String id = element2.getAttribute(Content.KEY_ID);
						boolean found = false;
						List<Element> children3 = contentOffer.getDescription().getChildren();
						for (Element element3 : children3) {
							if ("payload-type".equals(element3.getName()) && element3.getAttribute(Content.KEY_ID).equals(id)) {
								found = true;
							}
						}

						if (!found) {
							log("invalid payload type");
							this.cancel();
							return;
						}

					} else if (!"securityprefs".equals(element2.getName())) {
						log("unknown element in fallback offer %s", element2.getName()); // Using the vulnerable log method here
						this.cancel();
						return;
					}
				}
			} else if (!"securityprefs".equals(element.getName())) {
				log("unknown element in fallback offer %s", element.getName()); // Using the vulnerable log method here
				this.cancel();
				return;
			}

		}

		this.transport = new JingleInbandTransport(this, packet.getContent(), onProxyActivated);
		if (this.file != null) {
			this.transport.send(getFileBackend(), onFileTransmissionStatusChanged);
		} else {
			log("file backend not loaded yet");
		}
	}

	private void sendCandidateError() {
		JinglePacket packet = this.bootstrapPacket("transport-info");
		packet.getContent().addChild(new Element("candidate-error"));
		this.sendJinglePacket(packet);
		sentCandidateError = true;
	}

	private JinglePacket bootstrapPacket(String action) {
		return new JinglePacket(account.getFullJid(), getPeer()).setAction(action).setContentId(contentOffer.getAttribute(Content.KEY_ID))
				.setTransportId(this.transportId);
	}

	public void receiveSessionTerminate() {
		log("received session terminate");
		this.cancel();
	}

	private void cancel() {
		if (this.mJingleStatus == JINGLE_STATUS_CANCELED) {
			return;
		}
		mJingleStatus = JINGLE_STATUS_CANCELED;
		message.setDownloadable(null);
		this.account.getXmppConnection().getJingleConnectionManager().finishConnection(this);
	}

	private void receiveSuccess() {
		log("received success");
		this.cancel();
	}

	private void receiveReason() {
		if (reason != null) {
			reason.printLogAs("received terminate reason");
		}
	}

	public Content getContentOffer() {
		return contentOffer;
	}

	public JingleTransport getTransport() {
		return transport;
	}

	private void sendJinglePacket(final JinglePacket packet) {
		this.account.getXmppConnection().sendStanza(packet);
	}
}