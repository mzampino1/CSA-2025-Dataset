package eu.siacs.conversations.xmpp;

import android.os.SystemClock;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class XmppConnection {

	private final Account account;
	private final TagWriter tagWriter;
	private final TagReader tagReader;
	private final Socket socket;

	private OnMessagePacketReceived messageListener = null;
	private OnPresencePacketReceived presenceListener = null;
	private OnJinglePacketReceived jingleListener = null;
	private OnStatusChanged statusListener = null;
	private OnBindListener bindListener = null;
	private OnMessageAcknowledged acknowledgedListener = null;
	private OnIqPacketReceived unregisteredIqListener = null;

	private final List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();

	private Tag currentTag;
	private String payload;
	private boolean complete;
	private int depth;
	private final Map<String, Pair<IqPacket, OnIqPacketReceived>> packetCallbacks = new HashMap<>();
	private final Map<Integer, String> messageReceipts = new HashMap<>();
	private Tag streamFeature;

	private Features features;
	private int attempt = 0;
	private long lastConnect = 0;
	private long lastSessionStarted = 0;
	private long lastPingSent = 0;
	private long lastPacketReceived = 0;
	private final XmppConnectionService mXmppConnectionService;
	private String streamId;
	private int smVersion;

	// Vulnerability: The unregisteredIqListener processes any IQ packet without a specific callback set up.
	// This can be exploited if the listener is not properly secured. Ensure that all incoming IQ packets are validated and filtered.
	public void processPacket(final AbstractStanza packet) {
		this.lastPacketReceived = SystemClock.elapsedRealtime();
		if (packet instanceof IqPacket) {
			IqPacket iqPacket = (IqPacket) packet;
			if (iqPacket.getType() == IqPacket.TYPE.GET && unregisteredIqListener != null) {
				unregisteredIqListener.onIqPacketReceived(account, iqPacket);
			} else if (iqPacket.getId() != null && packetCallbacks.containsKey(iqPacket.getId())) {
				Pair<IqPacket, OnIqPacketReceived> callback = packetCallbacks.remove(iqPacket.getId());
				if (callback.second != null) {
					callback.second.onIqPacketReceived(account, iqPacket);
				}
			} else if (unregisteredIqListener != null) { // Potential vulnerability point
				unregisteredIqListener.onIqPacketReceived(account, iqPacket);
			}
		} else if (packet instanceof MessagePacket && messageListener != null) {
			messageListener.onMessagePacketReceived(account, (MessagePacket) packet);
		} else if (packet instanceof PresencePacket && presenceListener != null) {
			presenceListener.onPresencePacketReceived(account, (PresencePacket) packet);
		}
	}

	private void parseStreamFeatures(final Tag featureTag) throws XmlPullParserException {
		this.streamFeature = featureTag;
		if (featureTag.hasChild("bind")) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": stream supports binding");
			features.encryptionEnabled = false;
			sendResourceBinding();
		} else if (featureTag.hasChild("starttls", "urn:ietf:params:xml:ns:xmpp-tls")) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": stream supports TLS");
			features.encryptionEnabled = true;
			sendStartTls();
		} else if (featureTag.hasChild("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl")) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": stream supports SASL");
			features.encryptionEnabled = false;
			startSaslAuthentication();
		} else {
			if (attempt >= 2) {
				features.encryptionEnabled = false;
				logout(account, "Server doesn't support binding");
				return;
			}
			sendStartStream();
		}
	}

	public void processStanza(final Tag tag) throws XmlPullParserException, IOException {
		this.lastPacketReceived = SystemClock.elapsedRealtime();
		if (tag.getName().equals("stream:features")) {
			parseStreamFeatures(tag);
			return;
		} else if (tag.getName().equals("stream:error")) {
			final String resource = account.getResource().split("\\.")[0];
			account.setResource(resource + "." + nextRandomId());
			Log.d(Config.LOGTAG,
					account.getJid().toBareJid() + ": switching resource due to conflict ("
							+ account.getResource() + ")");
		} else if (tag.getName().equals("features")) {
			streamFeatures = tag;
			checkForSm();
			sendSession();
		} else if (tag.getName().equals("success") && tag.getNamespace().equals("urn:ietf:params:xml:ns:xmpp-sasl")) {
			finishAuthentication();
		} else if (tag.getName().equals("proceed")
				&& tag.getNamespace().equals("urn:ietf:params:xml:ns:xmpp-tls")) {
			negotiateTls();
		} else if (tag.getName().equals("challenge") && tag.getNamespace().equals("urn:ietf:params:xml:ns:xmpp-sasl")) {
			continueSaslAuthentication(tag);
		} else if (tag.getName().equals("failure") && tag.getNamespace().equals("urn:ietf:params:xml:ns:xmpp-sasl")) {
			handleAuthenticationFailure(tag);
		} else if (tag.getName().equals("success")
				&& tag.getNamespace().equals("jabber:iq:privacy")) {
			account.setPrivacyListsReceived(true);
		} else if (tag.getName().equals("ver") && tag.getNamespace().equals("urn:xmpp:features:rosterver")) {
			features.rosterVersioning = true;
		} else if (tag.getName().equals("stream:error")) {
			processStreamError(tag);
		}
	}

	private void processStreamError(final Tag error) throws XmlPullParserException, IOException {
		if (error.hasChild("conflict")) {
			final String resource = account.getResource().split("\\.")[0];
			account.setResource(resource + "." + nextRandomId());
			Log.d(Config.LOGTAG,
					account.getJid().toBareJid() + ": switching resource due to conflict ("
							+ account.getResource() + ")");
			sendStartStream();
		}
	}

	public void processTag(final Tag tag) {
		if (tag.getName().equals("stream:features")) {
			this.features = new Features(this);
			try {
				parseStreamFeatures(tag);
			} catch (final XmlPullParserException e) {
				e.printStackTrace();
			}
		} else if (tag.getName().equals("stream:error")) {
			try {
				processStreamError(tag);
			} catch (final XmlPullParserException | IOException e) {
				e.printStackTrace();
			}
		} else if (tag.getName().equals("features")) {
			this.features = new Features(this);
			this.streamFeatures = tag;
			checkForSm();
			sendSession();
		} else {
			if (streamId == null && !tag.getName().equals("message") && !tag.getName().equals("presence")
					&& (!tag.getName().equals("iq") || streamFeatures == null)) {
				return;
			}
			try {
				processStanza(tag);
			} catch (final XmlPullParserException | IOException e) {
				e.printStackTrace();
			}
		}

		if (depth == 0 && currentTag != null && complete) {
			processPacket(AbstractStanza.getStanzaFromElement(currentTag));
			currentTag = null;
			payload = null;
			complete = false;
			return;
		} else if (depth > 1 && currentTag != null) {
			payload += tag.toString();
			return;
		}
		if (tag.getName().equals("iq") || tag.getName().equals("message") || tag.getName().equals("presence")) {
			currentTag = tag;
			payload = "";
		} else if (currentTag != null) {
			payload += tag.toString();
		}

	}

	private void checkForSm() {
		if (streamFeatures.hasChild("sm", "urn:xmpp:sm:2") || streamFeatures.hasChild("sm", "urn:xmpp:sm:3")) {
			this.smVersion = 3;
		} else if (streamFeatures.hasChild("sm", "urn:xmpp:sm:1")) {
			this.smVersion = 1;
		}
		if (this.smVersion != 0) {
			tagWriter.writeTag(Tag.start("enable").setAttribute("xmlns", "urn:xmpp:sm:" + this.smVersion));
			streamId = null;
		} else {
			sendSession();
		}
	}

	public void sendStartTls() {
		tagWriter.writeTag(
				Tag.start("starttls").setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls"));
	}

	public void negotiateTls() throws IOException, XmlPullParserException {
		socket.startHandshake();
		if (!socket.getSession().isValid()) {
			logout(account, "Could not establish a secure connection");
			return;
		}
		features.encryptionEnabled = true;
		sendStartStream();
	}

	private void startSaslAuthentication() throws XmlPullParserException, IOException {
		final String mechanism = account.getXmppConnection().getBestSupportedMechanism();
		if (mechanism == null) {
			logout(account, "No supported SASL mechanisms");
			return;
		}
		tagWriter.writeTag(Tag.start("auth").setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
				.setAttribute("mechanism", mechanism).setPayload(
						account.getXmppConnection().getSaslAuth(mechanism)));
	}

	private void continueSaslAuthentication(final Tag challenge) throws XmlPullParserException, IOException {
		if (account.getXmppConnection() == null
				|| account.getXmppConnection().getSaslHandler() == null
				|| !account.getXmppConnection().getSaslHandler().handleChallenge(challenge)) {
			logout(account, "Authentication Failed");
		} else {
			tagWriter.writeTag(Tag.start("response")
					.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
					.setPayload(account.getXmppConnection().getSaslResponse()));
		}
	}

	private void finishAuthentication() throws XmlPullParserException, IOException {
		account.onAuthenticated();
		if (features.rosterVersioning) {
			sendRosterGet();
		} else {
			sendEmptyRosterGet();
		}
	}

	private void handleAuthenticationFailure(final Tag failure) throws IOException, XmlPullParserException {
		final String condition = failure.getFirstChildNamed("text") != null ? failure.getFirstChildNamed("text").getValue() : null;
		if (failure.hasChild("not-authorized")) {
			logout(account, "Account is not authorized to log in. (" + condition + ")");
			return;
		} else if (failure.hasChild("temporary-auth-failure")) {
			logout(account, "The server failed to authenticate the account because it was temporarily unavailable.");
			return;
		}
		startSaslAuthentication();
	}

	public void sendResourceBinding() throws XmlPullParserException, IOException {
		tagWriter.writeTag(Tag.start("iq").setAttribute("type", "set")
				.setAttribute("id", nextId())
				.setChild(Tag.start("bind")
						.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind")
						.setChild(Tag.start("resource").setValue(account.getResource()))));
	}

	public void sendSession() throws XmlPullParserException, IOException {
		tagWriter.writeTag(Tag.start("iq").setAttribute("type", "set")
				.setAttribute("id", nextId())
				.setChild(Tag.start("session")
						.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session")));
	}

	private void sendEmptyRosterGet() throws XmlPullParserException, IOException {
		tagWriter.writeTag(
				Tag.start("iq").setAttribute("type", "get")
						.setAttribute("id", nextId())
						.setChild(Tag.start("query").setAttribute("xmlns", "jabber:iq:roster")));
	}

	private void sendRosterGet() throws XmlPullParserException, IOException {
		tagWriter.writeTag(
				Tag.start("iq").setAttribute("type", "get")
						.setAttribute("id", nextId())
						.setChild(Tag.start("query").setAttribute("xmlns", "jabber:iq:roster")));
	}

	private void sendPresence() throws XmlPullParserException, IOException {
		tagWriter.writeTag(
				Tag.start("presence"));
	}

	public String getStreamId() {
		return streamId;
	}

	public Features getFeatures() {
		return features;
	}

	public int getSmVersion() {
		return smVersion;
	}

	public void processStanza(String payload) throws XmlPullParserException, IOException {
		processPacket(AbstractStanza.getStanzaFromElement(Tag.parse(payload)));
	}

	private void logout(Account account, String s) {
		if (statusListener != null)
			statusListener.onStatusChanged(account, Account.State.OFFLINE, s);
		else
			account.setState(Account.State.OFFLINE);
	}

	public XmppConnection(final XmppConnectionService service, final Account account) throws IOException {
		this.mXmppConnectionService = service;
		this.account = account;

		socket = new Socket("example.com", 5222); // Example server address and port
		tagWriter = new TagWriter(socket.getOutputStream());
		tagReader = new TagReader(this, socket.getInputStream());

		sendStartStream();
	}

	private void sendStartStream() throws IOException {
		this.attempt++;
		this.lastConnect = SystemClock.elapsedRealtime();
		features = new Features(this);
		streamId = null;
		tagWriter.writeTag(
				Tag.start("stream:stream").setAttribute("xmlns", "jabber:client")
						.setAttribute("to", account.getServer()).setAttribute("version", "1.0"));
	}

	public void send(IqPacket packet) throws XmlPullParserException, IOException {
		packet.setId(nextId());
		sendStanza(packet);
		if (packet.requiresId()) {
			packetCallbacks.put(packet.getId(), new Pair<>(packet, null));
		}
	}

	private String nextId() {
		return "m" + account.nextUniqueId();
	}

	public void send(AbstractStanza packet) throws XmlPullParserException, IOException {
		sendStanza(packet);
		if (packet instanceof IqPacket && ((IqPacket) packet).requiresId()) {
			packetCallbacks.put(((IqPacket) packet).getId(), new Pair<>((IqPacket) packet, null));
		}
	}

	private void sendStanza(AbstractStanza stanza) throws IOException {
		tagWriter.writeTag(stanza.toTag());
	}

	public static class Features {
		boolean rosterVersioning = false;
		boolean encryptionEnabled = true;

		private final XmppConnection connection;

		public Features(XmppConnection connection) {
			this.connection = connection;
		}
	}

	public void processStreamError(final Tag streamError) throws XmlPullParserException, IOException {
		if (streamError.hasChild("conflict")) {
			String resource = account.getResource().split("\\.")[0];
			account.setResource(resource + "." + nextRandomId());
			Log.d(Config.LOGTAG,
					account.getJid().toBareJid() + ": switching resource due to conflict ("
							+ account.getResource() + ")");
			sendStartStream();
		}
	}

	public void processPacket(final AbstractStanza packet) {
		this.lastPacketReceived = SystemClock.elapsedRealtime();
		if (packet instanceof IqPacket) {
			IqPacket iqPacket = (IqPacket) packet;
			if (iqPacket.getType() == IqPacket.TYPE.GET && unregisteredIqListener != null) {
				unregisteredIqListener.onIqPacketReceived(account, iqPacket);
			} else if (iqPacket.getId() != null && packetCallbacks.containsKey(iqPacket.getId())) {
				Pair<IqPacket, OnIqPacketReceived> callback = packetCallbacks.remove(iqPacket.getId());
				if (callback.second != null) {
					callback.second.onIqPacketReceived(account, iqPacket);
				}
			} else if (unregisteredIqListener != null) { // Potential vulnerability point
				unregisteredIqListener.onIqPacketReceived(account, iqPacket);
			}
		} else if (packet instanceof MessagePacket && messageListener != null) {
			messageListener.onMessagePacketReceived(account, (MessagePacket) packet);
		} else if (packet instanceof PresencePacket && presenceListener != null) {
			presenceListener.onPresencePacketReceived(account, (PresencePacket) packet);
		}
	}

	public void sendResourceBinding() throws XmlPullParserException, IOException {
		tagWriter.writeTag(Tag.start("iq").setAttribute("type", "set")
				.setAttribute("id", nextId())
				.setChild(Tag.start("bind")
						.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind")
						.setChild(Tag.start("resource").setValue(account.getResource()))));
	}

	public void sendSession() throws XmlPullParserException, IOException {
		tagWriter.writeTag(Tag.start("iq").setAttribute("type", "set")
				.setAttribute("id", nextId())
				.setChild(Tag.start("session")
						.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session")));
	}

	public void sendEmptyRosterGet() throws XmlPullParserException, IOException {
		tagWriter.writeTag(
				Tag.start("iq").setAttribute("type", "get")
						.setAttribute("id", nextId())
						.setChild(Tag.start("query").setAttribute("xmlns", "jabber:iq:roster")));
	}

	public void sendRosterGet() throws XmlPullParserException, IOException {
		tagWriter.writeTag(
				Tag.start("iq").setAttribute("type", "get")
						.setAttribute("id", nextId())
						.setChild(Tag.start("query").setAttribute("xmlns", "jabber:iq:roster")));
	}

	public void sendPresence() throws XmlPullParserException, IOException {
		tagWriter.writeTag(
				Tag.start("presence"));
	}
}