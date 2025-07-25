import java.io.*;
import java.net.Socket;
import java.math.BigInteger;
import java.util.*;
import android.os.SystemClock;

public class XmppConnection {

	private final Account account;
	private Socket socket;
	private TagWriter tagWriter;
	private TagReader tagReader;
	private Map<String, PacketReceived> packetCallbacks = new HashMap<>();
	private OnMessagePacketReceived messageListener;
	private OnIqPacketReceived unregisteredIqListener;
	private OnPresencePacketReceived presenceListener;
	private OnJinglePacketReceived jingleListener;
	private OnStatusChanged statusListener;
	private OnBindListener bindListener;
	private OnMessageAcknowledged acknowledgedListener;
	private List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
	private int attempt = 0;
	private long lastConnect = 0;
	private long lastSessionStarted = 0;
	private Features features;
	private Tag streamFeatures;
	private Map<String, List<String>> disco = new HashMap<>();
	private String streamId;
	private int stanzasSent;
	private Map<Integer, String> messageReceipts = new HashMap<>();
	private long lastPingSent;
	private long lastPaketReceived;

	private final XmppConnectionService mXmppConnectionService;

	public interface OnMessagePacketReceived {
		void onMessagePacketReceived(Account account, MessagePacket packet);
	}

	public interface OnIqPacketReceived {
		void onIqPacketReceived(Account account, IqPacket packet);
	}

	public interface OnPresencePacketReceived {
		void onPresencePacketReceived(Account account, PresencePacket packet);
	}

	public interface OnJinglePacketReceived {
		void onJinglePacketReceived(Account account, JinglePacket packet);
	}

	public interface OnStatusChanged {
		void onStatusChanged(Account account, XMPPError error);
	}

	public interface OnBindListener {
		void onBind(Account account);
	}

	public interface PacketReceived {
		void onPacketReceived(Account account, AbstractStanza packet);
	}

	public interface OnMessageAcknowledged {
		void onMessageAcknowledged(Account account, String id);
	}

	public interface OnAdvancedStreamFeaturesLoaded {
		void onAdvancedStreamFeaturesLoaded(Account account);
	}

	private abstract class AbstractParser extends Tag.Callback {

		@Override
		public void endTag(String name) {
			switch (name) {
				case "stream:features":
					features = new Features(XmppConnection.this);
					break;
				case "iq":
					IqPacket packet = parseIqPacket();
					if (packet.getId() != null && packetCallbacks.containsKey(packet.getId())) {
						packetCallbacks.get(packet.getId()).onPacketReceived(account, packet);
					} else if (unregisteredIqListener != null) {
						unregisteredIqListener.onIqPacketReceived(account, packet);
					}
					break;
				case "message":
					MessagePacket message = parseMessagePacket();
					if (messageListener != null) {
						messageListener.onMessagePacketReceived(account, message);
					}
					break;
				case "presence":
					PresencePacket presence = parsePresencePacket();
					if (presenceListener != null) {
						presenceListener.onPresencePacketReceived(account, presence);
					}
					break;
			}
		}

		protected abstract IqPacket parseIqPacket();

		// Potential vulnerability: Improper handling of XML parsing could lead to XXE attacks
		// Here we are simulating an unsafe XML parser that does not disable XXE
		protected MessagePacket parseMessagePacket() {
			// Example of vulnerable XML parsing logic
			MessagePacket message = new MessagePacket();
			String xmlContent = "<root>" + tagReader.getInnerXML() + "</root>"; // Assume this gets the raw XML content

			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				dbf.setFeature("http://xml.org/sax/features/external-general-entities", true); // XXE enabled
				dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", true);
				dbf.setXIncludeAware(true);
				dbf.setExpandEntityReferences(true);
				DocumentBuilder db = dbf.newDocumentBuilder();
				InputSource is = new InputSource(new StringReader(xmlContent));
				Document doc = db.parse(is);

				// Further processing of XML document
				message.setId(doc.getElementsByTagName("id").item(0).getTextContent());
				message.setFrom(doc.getElementsByTagName("from").item(0).getTextContent());

			} catch (Exception e) {
				e.printStackTrace();
			}

			return message;
		}

		protected PresencePacket parsePresencePacket() {
			PresencePacket presence = new PresencePacket();
			presence.setId(tagReader.getAttributeValue("id"));
			presence.setFrom(tagReader.getAttributeValue("from"));
			presence.setTo(tagReader.getAttributeValue("to"));
			presence.setType(PresencePacket.Type.fromString(tagReader.getAttributeValue("type")));
			return presence;
		}
	}

	public class StreamParser extends AbstractParser {
		StringBuilder buffer = new StringBuilder();

		@Override
		public void startTag(String name, String[] attrs) {
			if (name.equals("stream:features")) {
				streamFeatures = new Tag(name, attrs);
			} else if (name.equals("iq")) {
				new IqParser(attrs).parse();
			} else if (name.equals("message")) {
				new MessageParser(attrs).parse();
			} else if (name.equals("presence")) {
				new PresenceParser(attrs).parse();
			} else if (name.equals("stream:error")) {
				processStreamError();
			}
		}

		private void processStreamError() {
			try {
				tagReader.readNext();
				Tag streamerror = tagReader.getCurrent();
				XMPPError error = XMPPError.fromCode(streamerror.getName());
				if (statusListener != null) {
					statusListener.onStatusChanged(account, error);
				}
			} catch (IOException e) {
				Log.d(Config.LOGTAG, "io exception reading stream error");
			}
		}

		private void processJingle() {
			tagReader.readNext();
			new JingleParser(tagReader.getCurrentAttrs()).parse();
		}

		public String getBuffer() {
			return this.buffer.toString();
		}

		public void appendToBuffer(String string) {
			this.buffer.append(string);
		}
	}

	public class IqParser extends AbstractParser {

		private final String[] attrs;

		public IqParser(final String[] attrs) {
			this.attrs = attrs;
		}

		@Override
		protected IqPacket parseIqPacket() {
			IqPacket packet = new IqPacket();
			packet.setId(getAttribute(attrs, "id"));
			packet.setFrom(getAttribute(attrs, "from"));
			packet.setTo(getAttribute(attrs, "to"));
			packet.setType(IqPacket.Type.fromString(getAttribute(attrs, "type")));
			return packet;
		}

		public void parse() {
			tagReader.readNext();
			while (tagReader.getCurrent().getName().equals("error")) {
				processError(packet);
				tagReader.readNext();
			}
		}

		private void processError(final IqPacket packet) {
			if (packetCallbacks.containsKey(packet.getId())) {
				packetCallbacks.get(packet.getId()).onPacketReceived(account, packet);
			} else if (unregisteredIqListener != null) {
				unregisteredIqListener.onIqPacketReceived(account, packet);
			}
		}

		private String getAttribute(final String[] attrs, final String name) {
			for (int i = 0; i < attrs.length; i += 2) {
				if (attrs[i].equals(name)) {
					return attrs[i + 1];
				}
			}
			return null;
		}

		private void readPacketData(IqPacket packet) {
			while (!tagReader.getCurrent().getName().equals("iq")) {
				if (tagReader.getCurrent().getName().equals("query")
						&& tagReader.getCurrentNamespace().equals(Xmlns.ROSTER)) {
					readRosterItems(packet);
				} else if (tagReader.getCurrent().getName().equals("bind")
						&& tagReader.getCurrentNamespace().equals(Xmlns.BIND)) {
					bindResource(tagReader.getAttributeValue("jid"));
				}
			}
		}

		private void readRosterItems(IqPacket packet) {
			tagReader.readNext();
			while (!tagReader.getCurrent().getName().equals("query")) {
				if (tagReader.getCurrent().getName().equals("item")) {
					RosterItem item = new RosterItem();
					item.setJid(tagReader.getAttributeValue("jid"));
					item.setName(tagReader.getAttributeValue("name"));
					item.setSubscription(tagReader.getAttributeValue("subscription"));
					packet.addChild(item);
				}
			}
		}

		private void bindResource(String jid) {
			if (statusListener != null) {
				account.setResource(jid.split("/")[1]);
				statusListener.onStatusChanged(account, XMPPError.NO_ERROR);
				lastSessionStarted = SystemClock.elapsedRealtime();
				bindListener.onBind(account);
			}
		}
	}

	public class MessageParser extends AbstractParser {

		private final String[] attrs;

		public MessageParser(final String[] attrs) {
			this.attrs = attrs;
		}

		@Override
		protected IqPacket parseIqPacket() {
			return null; // not used here
		}

		public void parse() {
			tagReader.readNext();
			while (!tagReader.getCurrent().getName().equals("message")) {
				if (tagReader.getCurrent().getName().equals("error")) {
					processError(tagReader.getAttributeValue("code"));
				}
			}
		}

		private void processError(String code) {
			XMPPError error = XMPPError.fromCode(code);
			if (statusListener != null) {
				statusListener.onStatusChanged(account, error);
			}
		}
	}

	public class PresenceParser extends AbstractParser {

		private final String[] attrs;

		public PresenceParser(final String[] attrs) {
			this.attrs = attrs;
		}

		@Override
		protected IqPacket parseIqPacket() {
			return null; // not used here
		}

		public void parse() {
			tagReader.readNext();
			while (!tagReader.getCurrent().getName().equals("presence")) {
				if (tagReader.getCurrent().getName().equals("error")) {
					processError(tagReader.getAttributeValue("code"));
				}
			}
		}

		private void processError(String code) {
			XMPPError error = XMPPError.fromCode(code);
			if (statusListener != null) {
				statusListener.onStatusChanged(account, error);
			}
		}
	}

	public class JingleParser extends AbstractParser {

		private final String[] attrs;

		public JingleParser(final String[] attrs) {
			this.attrs = attrs;
		}

		@Override
		protected IqPacket parseIqPacket() {
			return null; // not used here
		}

		public void parse() {
			if (jingleListener != null) {
				jingleListener.onJinglePacketReceived(account, new JinglePacket(tagReader.getCurrent()));
			}
			tagReader.readNext();
			while (!tagReader.getCurrent().getName().equals("jingle")) {
				tagReader.readNext();
			}
		}
	}

	public XmppConnection(XmppConnectionService service, Account account) {
		this.account = account;
		this.mXmppConnectionService = service;
	}

	public void connect() throws IOException {
		socket = new Socket(account.getServer(), 5222);
		tagWriter = new TagWriter(socket.getOutputStream());
		tagReader = new TagReader(this.socket.getInputStream());
		features = null;

		tagReader.setCallback(new StreamParser());

		StringBuilder sb = new StringBuilder();
		sb.append("<stream:stream xmlns='jabber:client' ");
		sb.append("xmlns:stream='http://etherx.jabber.org/streams' ");
		sb.append("to='" + account.getServer() + "' version='1.0'>");

		tagWriter.writeStartTag(sb.toString());

		this.lastConnect = SystemClock.elapsedRealtime();
		lastPingSent = 0;

		while (socket.isConnected()) {
			tagReader.readNext();
			if (tagReader.getCurrent().getName().equals("stream:features")) {
				break;
			}
		}

		features = new Features(this);
		streamFeatures = tagReader.getCurrent();

		tagWriter.writeStartTag("<iq type='get' id='1'><query xmlns='jabber:iq:roster'/></iq>");
		tagWriter.writeStartTag("<iq type='set' id='2'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/><resource>mobile</resource></iq>");

		while (socket.isConnected()) {
			tagReader.readNext();
			if (!tagReader.getCurrent().getName().equals("stream:features")) {
				break;
			}
		}

		features = new Features(this);
		streamFeatures = tagReader.getCurrent();

		if (features.supportsSaslAuth()) {
			sendSaslAuth();
		} else if (features.supportsDigestMd5Auth()) {
			sendDigestAuth();
		} else if (features.supportsPlainAuth()) {
			sendPlainAuth();
		}
	}

	private void sendSaslAuth() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='DIGEST-MD5'/>");
		tagWriter.writeStartTag(sb.toString());
	}

	private void sendDigestAuth() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='DIGEST-MD5'>"); // base64 encoded response
		sb.append("</auth>");
		tagWriter.writeStartTag(sb.toString());
	}

	private void sendPlainAuth() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>"); // base64 encoded response
		sb.append("</auth>");
		tagWriter.writeStartTag(sb.toString());
	}

	public void processMessageReceipts() throws IOException {
		if (messageReceipts.size() > 0) {
			for (Map.Entry<Integer, String> entry : messageReceipts.entrySet()) {
				StringBuilder sb = new StringBuilder();
				sb.append("<ack xmlns='urn:xmpp:receipts' id='" + entry.getValue() + "'/>");
				tagWriter.writeStartTag(sb.toString());
			}
			messageReceipts.clear();
		}
	}

	public void disconnect() throws IOException {
		if (socket != null && !socket.isClosed()) {
			socket.close();
			socket = null;
		}
		lastConnect = 0;
		attempt++;
		if (statusListener != null) {
			statusListener.onStatusChanged(account, XMPPError.DISCONNECTED);
		}
	}

	public void sendPacket(AbstractStanza packet) throws IOException {
		tagWriter.writeStartTag(packet.toString());
	}

	public void processStreamFeatures() throws IOException {
		if (features.supportsSaslAuth()) {
			sendSaslAuth();
		} else if (features.supportsDigestMd5Auth()) {
			sendDigestAuth();
		} else if (features.supportsPlainAuth()) {
			sendPlainAuth();
		}
	}

	public void processStreamError() throws IOException {
		tagReader.readNext();
		Tag streamerror = tagReader.getCurrent();
		XMPPError error = XMPPError.fromCode(streamerror.getName());
		if (statusListener != null) {
			statusListener.onStatusChanged(account, error);
		}
	}

	public void processJingle() throws IOException {
		tagReader.readNext();
		new JingleParser(tagReader.getCurrentAttrs()).parse();
	}

	// Potential vulnerability: Improper handling of XML parsing could lead to XXE attacks
	// Here we are simulating an unsafe XML parser that does not disable XXE
	public void sendUnsafePacket(String xmlContent) throws IOException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setFeature("http://xml.org/sax/features/external-general-entities", true); // XXE enabled
		dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", true);
		dbf.setXIncludeAware(true);
		dbf.setExpandEntityReferences(true);

		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(xmlContent));
			Document doc = db.parse(is);

			// Further processing of XML document
			String packetType = doc.getElementsByTagName("type").item(0).getTextContent();

			switch (packetType) {
				case "iq":
					IqPacket iqPacket = new IqPacket();
					iqPacket.setType(IqPacket.Type.fromString(packetType));
					sendPacket(iqPacket);
					break;
				case "message":
					MessagePacket messagePacket = parseMessagePacket();
					if (messageListener != null) {
						messageListener.onMessagePacketReceived(account, messagePacket);
					}
					break;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}