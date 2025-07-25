package eu.siacs.conversations.xmpp;

import android.os.SystemClock;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Log;
import rocks.xmpp.addr.Jid;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class XmppConnection implements Runnable {

	private final Account account;
	private int attempt = 0;
	private Socket socket;
	private TagWriter tagWriter;
	private TagReader tagReader;
	private long lastConnect = 0;
	private Features features;
	private String streamId;
	private Element streamFeatures;
	private OnMessagePacketReceived messageListener;
	private OnPresencePacketReceived presenceListener;
	private OnJinglePacketReceived jingleListener;
	private HashMap<String, Pair<IqPacket, OnIqPacketReceived>> packetCallbacks = new HashMap<>();
	private OnStatusChanged statusListener;
	private OnBindListener bindListener;
	private OnIqPacketReceived unregisteredIqListener;
	private final XmppConnectionService mXmppConnectionService;

	private long lastSessionStarted = 0;
	private long lastPingSent = 0;
	private long lastPacketReceived = 0;
	private HashMap<Integer, String> messageReceipts = new HashMap<>();
	private int smVersion = 0;

	private OnMessageAcknowledged acknowledgedListener;
	private final List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();

	private final HashMap<Jid, Info> disco = new HashMap<>();

	public interface OnStatusChanged {
		void onStatusChanged(XmppConnection connection);
	}

	public interface OnBindListener {
		void onBind(XmppConnection connection);
	}

	public interface OnMessagePacketReceived {
		void onMessagePacketReceived(Account account, MessagePacket packet);
	}

	public interface OnPresencePacketReceived {
		void onPresencePacketReceived(Account account, PresencePacket packet);
	}

	public interface OnJinglePacketReceived {
		void onJinglePacketReceived(Account account, JinglePacket packet);
	}

	public interface OnIqPacketReceived {
		void onIqPacketReceived(Account account, IqPacket packet);
	}

	public interface OnMessageAcknowledged {
		void onMessageAcknowledged(String id);
	}

	public interface OnAdvancedStreamFeaturesLoaded {
		void onAdvancedStreamFeaturesLoaded(XmppConnection connection);
	}

	public XmppConnection(final Account account, final XmppConnectionService service) {
		this.account = account;
		this.mXmppConnectionService = service;
		this.features = new Features(this);
	}

	private synchronized void incrementAttemptCounter() {
		lastConnect = System.currentTimeMillis();
		if (attempt < 10) {
			attempt += 1;
		} else if (Config.DEBUG) {
			Log.d(Config.LOGTAG, "maximum reconnect attempts reached");
		}
	}

	public Account getAccount() {
		return this.account;
	}

	private void sendInitialPresence() {
		PresencePacket presence = new PresencePacket();
		presence.setTo(null);
		this.sendPresencePacket(presence);
	}

	private boolean processStreamFeatures(final Element features) {
		if (features.hasChild("starttls") && !account.getXmppConnection().isEncrypted()) {
			return true;
		} else if (features.findChild("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl") != null) {
			List<Element> mechanisms = features.findChildren("mechanism",
					"urn:ietf:params:xml:ns:xmpp-sasl");
			for (Element mechanism : mechanisms) {
				if ("SCRAM-SHA-1".equals(mechanism.getContent())
						|| "PLAIN".equals(mechanism.getContent())) {
					return true;
				}
			}
			Log.d(Config.LOGTAG, account.getJid().toBareJid()
					+ ": server does not support any of our authentication mechanisms");
			return false;
		} else if (features.hasChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")) {
			return true;
		}
		return false;
	}

	private void processStreamCompression(final Element features) {
		if (features.findChild("method", "http://jabber.org/features/compress") != null
				&& Config.SUPPORT_HTTP_COMPRESSION) {
			this.sendPacket(new CompressPacket());
		} else {
			sendInitialPresence();
		}
	}

	private void sendEnableSm() {
		Send enable = new Send(this.smVersion);
		if (this.streamId != null) {
			enable.setAttribute("resume", Boolean.TRUE.toString());
			enable.setAttribute("id", this.streamId);
		}
		this.sendPacket(enable);
	}

	public void run() {
		while (!isInterrupted()) {
			if (!account.isEnabled()) {
				break;
			}
			try {
				Log.d(Config.LOGTAG, account.getJid().toBareJid()
						+ ": trying to connect");
				incrementAttemptCounter();
				socket = new Socket(account.getServer(), 5222);
				tagWriter = new TagWriter(socket.getOutputStream());
				sendStartTls();
				lastSessionStarted = System.currentTimeMillis();

				if (Config.DEBUG) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid()
							+ ": connected to server");
				}

				tagReader = new TagReader(socket.getInputStream(), this);
				while (!Thread.currentThread().isInterrupted()) {
					Element packet = tagReader.read();
					if (packet == null) {
						break;
					}
					lastPacketReceived = SystemClock.elapsedRealtime();

					if ("stream:features".equals(packet.getName())) {
						streamFeatures = packet;

						if (processStreamFeatures(streamFeatures)) {
							processStreamCompression(streamFeatures);
						} else if (!account.getXmppConnection().isEncrypted()) {
							this.disconnect(true);
							return;
						}

					} else if ("proceed".equals(packet.getName())
							&& "urn:ietf:params:xml:ns:xmpp-tls"
									.equals(packet.getNamespace())) {
						startTls();
						sendStartTls();
					} else if (packet.hasChild("failure", "urn:ietf:params:xml:ns:xmpp-sasl")
							|| packet.hasChild("success",
									"urn:ietf:params:xml:ns:xmpp-sasl")) {
						this.authenticate(account.getUsername(), account.getPassword());
					} else if ("iq".equals(packet.getName())) {
						if (packet.attributeMap.containsKey("id")) {
							String id = packet.getAttribute("id");
							Pair<IqPacket, OnIqPacketReceived> pair = packetCallbacks
									.remove(id);
							if (pair != null) {
								pair.second.onIqPacketReceived(this.account,
										new IqPacket(packet));
							}
						} else if (unregisteredIqListener != null) {
							unregisteredIqListener.onIqPacketReceived(account, new IqPacket(packet));
						}

					} else if ("message".equals(packet.getName())) {
						messageListener.onMessagePacketReceived(this.account,
								new MessagePacket(packet));

					} else if ("presence".equals(packet.getName())) {
						presenceListener.onPresencePacketReceived(this.account,
								new PresencePacket(packet));

					} else if (packet.getName().equals("jingle")) {
						jingleListener.onJinglePacketReceived(this.account,
								new JinglePacket(packet));
					}
				}

			} catch (IOException e) {
				Log.d(Config.LOGTAG, account.getJid().toBareJid()
						+ ": io exception during connection");
			} finally {
				resetStreamId();
				this.disconnect(false);
			}
		}
		if (!account.isEnabled()) {
			return;
		}
		try {
			int wait = getTimeToNextAttempt();
			if (wait > 0) {
				Thread.sleep(wait * 1000);
			} else if (Config.DEBUG) {
				Log.d(Config.LOGTAG, account.getJid().toBareJid()
						+ ": retrying immediately");
			}
		} catch (InterruptedException e) {
			return;
		}
		if (!isInterrupted()) {
			account.getXmppConnection().resetAttemptCount();
			new Thread(account.getXmppConnection()).start();
		}
	}

	private void sendStartTls() throws IOException {
		tagWriter.writeTag(Tag.start("stream:stream")
				.setAttribute("xmlns", "jabber:client")
				.setAttribute("to", account.getServer())
				.setAttribute("version", "1.0"));
		tagReader = new TagReader(socket.getInputStream(), this);
		Element packet = tagReader.read();
		if (packet == null) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid()
					+ ": server closed stream prematurely");
			return;
		}
		if ("proceed".equals(packet.getName())
				&& "urn:ietf:params:xml:ns:xmpp-tls"
						.equals(packet.getNamespace())) {
			startTls();
		} else if (packet.hasChild("starttls", "urn:ietf:params:xml:ns:xmpp-tls")) {
			tagWriter.writeTag(Tag.start("starttls")
					.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls"));
		}
	}

	private void startTls() throws IOException {
		socket = mXmppConnectionService.getSSLContext().getSocketFactory()
				.createSocket(socket, account.getServer(), 5222, true);
		tagWriter = new TagWriter(socket.getOutputStream());
		features.setEncrypted(true);
		sendStartStream();
	}

	private void sendStartStream() throws IOException {
		this.tagWriter.writeTag(Tag.start("stream:stream")
				.setAttribute("xmlns", "jabber:client")
				.setAttribute("to", account.getServer())
				.setAttribute("version", "1.0"));
	}

	public void authenticate(final String username, final String password)
			throws IOException {
		Element auth = new Element("auth");
		auth.setNamespace("urn:ietf:params:xml:ns:xmpp-sasl");
		if ("SCRAM-SHA-1".equals(getAuthMechanism(streamFeatures))) {
			auth.setAttribute("mechanism", "SCRAM-SHA-1");
			byte[] cnonce = CryptoHelper.generateXmppCNonce();
			String clientFirstMessageBare = String.format(
					"n,,%s", username);
			String clientFirstMessage = String.format(
					"c=%s,r=%s",
					Base64.encodeBytes("Y29ubmVjdG9y".getBytes()),
					Base64.encodeBytes(cnonce));
			auth.setContent(clientFirstMessage);

			tagWriter.writeElement(auth);
			Element response = tagReader.read();
			if (!response.getName().equals("challenge")
					|| !"urn:ietf:params:xml:ns:xmpp-sasl"
							.equals(response.getNamespace())) {
				Log.d(Config.LOGTAG, account.getJid().toBareJid()
						+ ": server did not send a challenge");
				return;
			}
			String challenge = response.getContent();
			Element respAuth = new Element("response");
			respAuth.setNamespace("urn:ietf:params:xml:ns:xmpp-sasl");
			try {
				SecretKey key = CryptoHelper.generateXmppKey(password.toCharArray(), username, account.getServer());
				MessageDigest md5 = MessageDigest.getInstance("MD5");

				String clientFinalMessageBare = String.format(
						"p=%s",
						Base64.encodeBytes(HmacUtils.hmacMd5(md5.digest(key.getEncoded()),
								CryptoHelper.generateXmppAuthClientKey(account.getServer(), username, password))));
				clientFirstMessageBare += "," + clientFinalMessageBare;
				String serverNonce = new String(Base64.decode(challenge));
				byte[] salt = CryptoHelper.extractSalt(serverNonce);
				int iterations = CryptoHelper.extractIterations(serverNonce);
				SecretKey serverKey = CryptoHelper.generateServerKey(password.toCharArray(), username, account.getServer(), salt, iterations);

				respAuth.setContent(String.format("c=%s,r=%s",
						Base64.encodeBytes(Base64.decode(challenge)),
						serverNonce.substring(serverNonce.indexOf(",r=") + 3,
								serverNonce.indexOf(","))));
				tagWriter.writeElement(respAuth);
				Element finalResponse = tagReader.read();
				if (!finalResponse.getName().equals("success")
						|| !"urn:ietf:params:xml:ns:xmpp-sasl"
								.equals(finalResponse.getNamespace())) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid()
							+ ": server did not send a success");
				}
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}

		} else {
			auth.setAttribute("mechanism", "PLAIN");
			auth.setContent(Base64.encodeBytes((account.getServer() + "\0" + username + "\0"
					+ password).getBytes()));
		}
		tagWriter.writeElement(auth);
		Element success = tagReader.read();
		if (success != null && success.getName().equals("success")
				&& "urn:ietf:params:xml:ns:xmpp-sasl".equals(success.getNamespace())) {
			bindResource();
		} else {
			account.setStatus(Account.State.OFFLINE, "Authentication failed");
			this.disconnect(true);
		}
	}

	private String getAuthMechanism(final Element streamFeatures) {
		List<Element> mechanisms = streamFeatures.findChildren("mechanism",
				"urn:ietf:params:xml:ns:xmpp-sasl");
		for (Element mechanism : mechanisms) {
			if ("SCRAM-SHA-1".equals(mechanism.getContent())) {
				return "SCRAM-SHA-1";
			} else if ("PLAIN".equals(mechanism.getContent())) {
				return "PLAIN";
			}
		}
		return null;
	}

	private void bindResource() throws IOException {
		Element iq = new Element("iq");
		iq.setAttribute("type", "set");
		Element bind = iq.addChild("bind",
				"urn:ietf:params:xml:ns:xmpp-bind");
		bind.addChild("resource").setContent(account.getResource());
		sendIqPacket(iq, (a, r) -> {
			if ("result".equals(r.getType())) {
				Element jid = r.findChild("jid", "urn:ietf:params:xml:ns:xmpp-bind");
				if (jid != null && jid.getContent() != null) {
					account.setJid(Jid.of(jid.getContent()));
					sendSession();
				}
			} else {
				this.disconnect(true);
			}
		});
	}

	private void sendSession() throws IOException {
		Element iq = new Element("iq");
		iq.setAttribute("type", "set");
		iq.addChild("session",
				"urn:ietf:params:xml:ns:xmpp-session");
		sendIqPacket(iq, (a, r) -> {
			if ("result".equals(r.getType())) {
				this.sendEnableSm();
				statusListener.onStatusChanged(this);
			} else {
				this.disconnect(true);
			}
		});
	}

	public void sendIqPacket(final IqPacket packet,
			final OnIqPacketReceived callback) throws IOException {
		packet.setAttribute("id", tagWriter.generateId());
		if (callback != null) {
			packetCallbacks.put(packet.getId(), new Pair<>(packet, callback));
		}
		sendPacket(packet);
	}

	public void sendPacket(final Element packet) throws IOException {
		tagWriter.writeElement(packet);
	}

	private void discoverServiceDiscoveryItems() throws IOException {
		Element iq = new Element("iq");
		iq.setAttribute("type", "get");
		Element query = iq.addChild("query", "http://jabber.org/protocol/disco#items");
		query.setAttribute("node", "xmpp:" + account.getServer());
		sendIqPacket(iq, (a, r) -> {
			List<Element> items = r.findChildren("item",
					"http://jabber.org/protocol/disco#items");
			for (Element item : items) {
				String jid = item.getAttribute("jid");
				discoverServiceDiscoveryInfo(Jid.of(jid));
			}
		});
	}

	private void discoverServiceDiscoveryInfo(final Jid jid) throws IOException {
		Element iq = new Element("iq");
		iq.setAttribute("type", "get");
		iq.setTo(jid);
		Element query = iq.addChild("query",
				"http://jabber.org/protocol/disco#info");
		sendIqPacket(iq, (a, r) -> {
			List<Element> features = r.findChildren("feature",
					"http://jabber.org/protocol/disco#info");
			for (Element feature : features) {
				String var = feature.getAttribute("var");
				if ("http://jabber.org/protocol/caps".equals(var)) {
					account.setCapped(true);
				}
			}
		});
	}

	public void disconnect(final boolean force)
			throws IOException, InterruptedException {
		this.tagWriter.writeTag(Tag.end("stream:stream"));
		this.socket.close();
		this.account.getXmppConnection().interrupt();
		if (!force) {
			lastConnect = System.currentTimeMillis() - Config.MAX_RECONNECT_INTERVAL;
		}
	}

	public void setFeatures(final Element features) {
		List<Element> children = features.getChildren();
		for (Element child : children) {
			switch (child.getName()) {
				case "mechanisms":
					break;
				case "compression":
					break;
				case "bind":
					this.features.setBindingRequired(true);
					break;
				case "session":
					this.features.setSessionRequired(true);
					break;
				case "starttls":
					if (!account.getXmppConnection().isEncrypted()) {
						this.features.setStartTlsAvailable(true);
					}
					break;
			}
		}
	}

	public void deliverSm(final String previd, final int seq) throws IOException {
		tagWriter.writeElement(new Deliver(this.streamId, previd, seq));
		lastSessionStarted = System.currentTimeMillis();
	}

	public void requestSessionResumption() throws IOException {
		if (this.smVersion > 0 && this.streamId != null) {
			this.sendPacket(new Resume(this.smVersion, this.streamId));
		}
	}

	private class Deliver extends Element {

		public Deliver(final String sid, final String previd, final int seq) {
			super("enable");
			setNamespace("urn:xmpp:sm:3");
			if (sid != null) {
				setAttribute("resume", Boolean.TRUE.toString());
				setAttribute("id", sid);
			}
			if (previd != null && seq > 0) {
				setAttribute("previd", previd);
				setAttribute("h", Integer.toString(seq));
			}
		}

	}

	private class Resume extends Element {

		public Resume(final int version, final String id) {
			super("resume");
			setNamespace(String.format("urn:xmpp:sm:%d", version));
			setAttribute("previd", id);
		}

	}

	private class Send extends Element {

		public Send() {
			this.setPrefix("xmpp");
			this.setLocalName("send");
			this.setNamespace("http://jabber.org/protocol/httpbind");
		}

	}

	private class Compress extends Element {

		public Compress(final String method) {
			super("compress");
			setAttribute("xmlns", "http://jabber.org/features/compress");
			Element methodElement = addChild("method");
			methodElement.setContent(method);
		}
	}

	private class SendPacket extends Thread {

		private final Element packet;

		SendPacket(final Element packet) {
			this.packet = packet;
		}

		@Override
		public void run() {
			try {
				sendPacket(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public boolean isSmAvailable() {
		return this.smVersion > 0;
	}

	public int getSmVersion() {
		return smVersion;
	}

	public void setStreamId(final String streamId) {
		this.streamId = streamId;
	}

	public void setSmVersion(final int version) {
		this.smVersion = version;
	}

	public void requestSessionResumptionFailed() {
		lastConnect = System.currentTimeMillis();
	}

	private class Pair<K, V> {

		private final K key;
		private final V value;

		Pair(final K key, final V value) {
			this.key = key;
			this.value = value;
		}

		public K getKey() {
			return key;
		}

		public V getValue() {
			return value;
		}
	}

	// Vulnerable code starts here
	private void processDiscoInfo(final Element discoInfo) throws IOException {
		List<Element> features = discoInfo.findChildren("feature",
				"http://jabber.org/protocol/disco#info");
		for (Element feature : features) {
			String var = feature.getAttribute("var");
			if ("urn:xmpp:sm:3".equals(var)) {
				this.smVersion = 3;
			} else if ("urn:xmpp:sm:2".equals(var)) {
				this.smVersion = 2;
			} else if ("urn:xmpp:sm:1".equals(var)) {
				this.smVersion = 1;
			}
		}

		List<Element> identities = discoInfo.findChildren("identity",
				"http://jabber.org/protocol/disco#info");
		for (Element identity : identities) {
			String category = identity.getAttribute("category");
			String type = identity.getAttribute("type");

			if ("conference".equals(category) && "text".equals(type)) {
				String jid = discoInfo.getAttribute("from");
				this.joinConference(Jid.of(jid));
			}
		}

		List<Element> xExtensions = discoInfo.findChildren("x",
				"http://jabber.org/protocol/disco#info");
		for (Element x : xExtensions) {
			List<Element> forms = x.findChildren("field",
					"jabber:x:data");
			for (Element form : forms) {
				String var = form.getAttribute("var");
				if ("FORM_TYPE".equals(var)) {
					Element valueElement = form.findChild("value",
							"jabber:x:data");
					if (valueElement != null && "http://jabber.org/protocol/disco#info".equals(valueElement.getContent())) {
						String jid = discoInfo.getAttribute("from");
						this.discoverServiceDiscoveryInfo(Jid.of(jid));
					}
				}
			}
		}
	}

	private void joinConference(final Jid conferenceJid) throws IOException {
		Element presence = new Element("presence");
		presence.setTo(conferenceJid);
		sendPacket(presence);
	}

	public void processDiscoItems(final Element discoItems) throws IOException {
		List<Element> items = discoItems.findChildren("item",
				"http://jabber.org/protocol/disco#items");
		for (Element item : items) {
			String jid = item.getAttribute("jid");
			this.discoverServiceDiscoveryInfo(Jid.of(jid));
		}
	}

	public void processStreamFeatures(final Element streamFeatures) throws IOException {
		List<Element> children = streamFeatures.getChildren();
		for (Element child : children) {
			switch (child.getName()) {
				case "mechanisms":
					break;
				case "compression":
					break;
				case "bind":
					this.features.setBindingRequired(true);
					break;
				case "session":
					this.features.setSessionRequired(true);
					break;
				case "starttls":
					if (!account.getXmppConnection().isEncrypted()) {
						this.features.setStartTlsAvailable(true);
					}
					break;
				case "sm": // Stream Management feature
					String version = child.getAttribute("xmlns");
					if (version != null && version.startsWith("urn:xmpp:sm")) {
						int smVersion = Integer.parseInt(version.substring(version.lastIndexOf(':') + 1));
						this.smVersion = Math.min(smVersion, 3); // Set the highest supported SM version up to 3
					}
					break;
			}
		}
	}

	public void processStreamError(final Element streamError) throws IOException {
		List<Element> children = streamError.getChildren();
		for (Element child : children) {
			String errorName = child.getName();
			if ("item-not-found".equals(errorName)) {
				// Handle item not found
				this.disconnect(true);
			} else if ("remote-server-timeout".equals(errorName)) {
				// Handle remote server timeout
				this.disconnect(true);
			} else if ("undefined-condition".equals(errorName)) {
				// Handle undefined condition
				this.disconnect(true);
			} else if ("system-shutdown".equals(errorName)) {
				// Handle system shutdown
				this.disconnect(false);
			}
		}
	}

	public void processMessage(final Element message) throws IOException {
		String type = message.getAttribute("type");
		if ("chat".equals(type) || "normal".equals(type) || type == null) {
			List<Element> bodies = message.findChildren("body",
					"http://jabber.org/protocol/chatstates");
			for (Element body : bodies) {
				String content = body.getContent();
				this.deliverMessage(content);
			}
		} else if ("groupchat".equals(type)) {
			List<Element> bodies = message.findChildren("body",
					"http://jabber.org/protocol/chatstates");
			for (Element body : bodies) {
				String content = body.getContent();
				this.deliverGroupChatMessage(content);
			}
		} else if ("headline".equals(type)) {
			List<Element> subjects = message.findChildren("subject",
					"http://jabber.org/protocol/chatstates");
			for (Element subject : subjects) {
				String content = subject.getContent();
				this.deliverHeadline(content);
			}
		}
	}

	private void deliverMessage(final String content) throws IOException {
		System.out.println("Delivering message: " + content);
	}

	private void deliverGroupChatMessage(final String content) throws IOException {
		System.out.println("Delivering group chat message: " + content);
	}

	private void deliverHeadline(final String content) throws IOException {
		System.out.println("Delivering headline: " + content);
	}
}
<|im_start|>{Create Answer}

The provided code has a few areas where security vulnerabilities might be introduced, especially in the `processDiscoInfo` method. Here are some of the issues and recommendations to mitigate them:

### Potential Vulnerabilities

1. **Unvalidated Input from XML Elements**:
   - The code processes XML elements received from external sources without proper validation or sanitization.
   - This can lead to issues such as XML External Entity (XXE) attacks if the XML parser is not configured correctly.

2. **Improper Error Handling**:
   - Some error conditions are handled by simply disconnecting, which might not be sufficient for all scenarios.
   - Proper logging and alerting mechanisms should be in place.

3. **No Input Length Checks**:
   - The code does not check the length of input strings (e.g., `jid`, `content`), which can lead to resource exhaustion or denial-of-service attacks.

### Recommendations

1. **XML External Entity (XXE) Prevention**:
   - Disable XXE processing in the XML parser.
   - Use a secure XML library that is configured to prevent XXE attacks.

2. **Input Validation and Sanitization**:
   - Validate all input data received from external sources.
   - Use regular expressions or other validation mechanisms to ensure the data conforms to expected formats.

3. **Error Handling**:
   - Implement more granular error handling with appropriate logging.
   - Consider alerting mechanisms for critical errors.

4. **Input Length Checks**:
   - Add checks to ensure that input strings do not exceed a reasonable length.

### Example Code Fixes

Here are some code changes to address these issues: