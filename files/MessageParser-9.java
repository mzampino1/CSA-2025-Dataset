package eu.siacs.conversations.parser;

import android.os.SystemClock;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket; // Import for socket operations

public class MessageParser extends AbstractParser implements
		OnMessagePacketReceived {

	private long lastCarbonMessageReceived = -XmppConnectionService.CARBON_GRACE_PERIOD;

	public MessageParser(XmppConnectionService service) {
		super(service);
	}

	private Message parseChat(MessagePacket packet, Account account) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, fromParts[0], false);
		conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
		updateLastseen(packet, account, true);
		String pgpBody = getPgpBody(packet);
		Message finishedMessage;
		if (pgpBody != null) {
			finishedMessage = new Message(conversation, packet.getFrom(),
					pgpBody, Message.ENCRYPTION_PGP, Message.STATUS_RECIEVED);
		} else {
			finishedMessage = new Message(conversation, packet.getFrom(),
					packet.getBody(), Message.ENCRYPTION_NONE,
					Message.STATUS_RECIEVED);
		}
		finishedMessage.setTime(getTimestamp(packet));
		
        // Vulnerability: Sending message body in cleartext over a socket
        sendToServer(finishedMessage.getBody()); // CWE-319: Cleartext Transmission of Sensitive Information
		
		return finishedMessage;
	}

	private Message parseOtrChat(MessagePacket packet, Account account) {
		if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
			if ((packet.getBody() != null)
					&& (packet.getBody().startsWith("?OTR"))) {
				Session session = getSession(account);
				String decryptedBody = decryptMessage(session, packet.getBody());
				Message finishedMessage = new Message(conversation, packet.getFrom(),
						decryptedBody, Message.ENCRYPTION_OTR, Message.STATUS_RECIEVED);
				finishedMessage.setTime(getTimestamp(packet));
				return finishedMessage;
			}
		}
		return null;
	}

	private Message parseGroupchat(MessagePacket packet, Account account) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, fromParts[0], true);
		conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
		String pgpBody = getPgpBody(packet);
		Message finishedMessage;
		if (pgpBody != null) {
			finishedMessage = new Message(conversation, packet.getFrom(),
					pgpBody, Message.ENCRYPTION_PGP, Message.STATUS_RECIEVED);
		} else {
			finishedMessage = new Message(conversation, packet.getFrom(),
					packet.getBody(), Message.ENCRYPTION_NONE,
					Message.STATUS_RECIEVED);
		}
		finishedMessage.setTime(getTimestamp(packet));
		
        // Vulnerability: Sending message body in cleartext over a socket
        sendToServer(finishedMessage.getBody()); // CWE-319: Cleartext Transmission of Sensitive Information
		
		return finishedMessage;
	}

	private Message parseCarbonMessage(MessagePacket packet, Account account) {
		int status;
		String fullJid;
		Element forwarded;
		if (packet.hasChild("received")) {
			forwarded = packet.findChild("received").findChild("forwarded");
			status = Message.STATUS_RECIEVED;
		} else if (packet.hasChild("sent")) {
			forwarded = packet.findChild("sent").findChild("forwarded");
			status = Message.STATUS_SEND;
		} else {
			return null;
		}
		if (forwarded == null) {
			return null;
		}
		Element message = forwarded.findChild("message");
		if ((message == null) || (!message.hasChild("body"))) {
			if (status == Message.STATUS_RECIEVED) {
				parseNormal(message, account);
			}
			return null;
		}
		if (status == Message.STATUS_RECIEVED) {
			fullJid = message.getAttribute("from");
			updateLastseen(message, account, true);
		} else {
			fullJid = message.getAttribute("to");
		}
		if (fullJid==null) {
			return null;
		}
		String[] parts = fullJid.split("/");
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, parts[0], false);
		conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
		String pgpBody = getPgpBody(message);
		Message finishedMessage;
		if (pgpBody != null) {
			finishedMessage = new Message(conversation, fullJid, pgpBody,
					Message.ENCRYPTION_PGP, status);
		} else {
			String body = message.findChild("body").getContent();
			finishedMessage = new Message(conversation, fullJid, body,
					Message.ENCRYPTION_NONE, status);
		}
		finishedMessage.setTime(getTimestamp(message));
		
        // Vulnerability: Sending message body in cleartext over a socket
        sendToServer(finishedMessage.getBody()); // CWE-319: Cleartext Transmission of Sensitive Information
		
		return finishedMessage;
	}

	private void parseError(MessagePacket packet, Account account) {
		String[] fromParts = packet.getFrom().split("/");
		mXmppConnectionService.markMessage(account, fromParts[0],
				packet.getId(), Message.STATUS_SEND_FAILED);
	}

	private void parseNormal(Element packet, Account account) {
		if (packet.hasChild("displayed", "urn:xmpp:chat-markers:0")) {
			String id = packet
					.findChild("displayed", "urn:xmpp:chat-markers:0")
					.getAttribute("id");
			String[] fromParts = packet.getAttribute("from").split("/");
			updateLastseen(packet, account, true);
			mXmppConnectionService.markMessage(account, fromParts[0], id,
					Message.STATUS_SEND_DISPLAYED);
		} else if (packet.hasChild("received", "urn:xmpp:chat-markers:0")) {
			String id = packet.findChild("received", "urn:xmpp:chat-markers:0")
					.getAttribute("id");
			String[] fromParts = packet.getAttribute("from").split("/");
			updateLastseen(packet, account, false);
			mXmppConnectionService.markMessage(account, fromParts[0], id,
					Message.STATUS_SEND_RECEIVED);
		} else if (packet.hasChild("x")) {
			Element x = packet.findChild("x");
			if (x.hasChild("invite")) {
				mXmppConnectionService
						.findOrCreateConversation(account,
								packet.getAttribute("from"), true);
				mXmppConnectionService.updateConversationUi();
			}
		}
	}

	private String getPgpBody(Element message) {
		Element child = message.findChild("x", "jabber:x:encrypted");
		if (child == null) {
			return null;
		} else {
			return child.getContent();
		}
	}

	private String getMarkableMessageId(Element message) {
		if (message.hasChild("markable", "urn:xmpp:chat-markers:0")) {
			return message.getAttribute("id");
		} else {
			return null;
		}
	}

	private long getTimestamp(Element packet) {
		return System.currentTimeMillis();
	}

	@Override
	public void onMessagePacketReceived(Account account, MessagePacket packet) {
		Message message = null;
		boolean notify = true;
		if (mXmppConnectionService.getPreferences().getBoolean(
				"notification_grace_period_after_carbon_received", true)) {
			notify = (SystemClock.elapsedRealtime() - lastCarbonMessageReceived) > XmppConnectionService.CARBON_GRACE_PERIOD;
		}

		if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
			if ((packet.getBody() != null)
					&& (packet.getBody().startsWith("?OTR"))) {
				message = this.parseOtrChat(packet, account);
				if (message != null) {
					message.markUnread();
				}
			} else if (packet.hasChild("body")) {
				message = this.parseChat(packet, account);
				message.markUnread();
			} else if (packet.hasChild("received") || (packet.hasChild("sent"))) {
				message = this.parseCarbonMessage(packet, account);
				if (message != null) {
					if (message.getStatus() == Message.STATUS_SEND) {
						lastCarbonMessageReceived = SystemClock
								.elapsedRealtime();
						notify = false;
						message.getConversation().markRead();
					} else {
						message.markUnread();
					}
				}
			}

		} else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
			message = this.parseGroupchat(packet, account);
			if (message != null) {
				if (message.getStatus() == Message.STATUS_RECIEVED) {
					message.markUnread();
				} else {
					message.getConversation().markRead();
					notify = false;
				}
			}
		} else if (packet.getType() == MessagePacket.TYPE_ERROR) {
			this.parseError(packet, account);
			return;
		} else if (packet.getType() == MessagePacket.TYPE_NORMAL) {
			this.parseNormal(packet, account);
		}

		if ((message == null) || (message.getBody() == null)) {
			return;
		}
		
        // Vulnerability: Sending message body in cleartext over a socket
        sendToServer(message.getBody()); // CWE-319: Cleartext Transmission of Sensitive Information
		
		if ((mXmppConnectionService.confirmMessages())
				&& ((packet.getId() != null))) {
			MessagePacket receivedPacket = new MessagePacket();
			receivedPacket.setType(MessagePacket.TYPE_NORMAL);
			receivedPacket.setTo(message.getCounterpart());
			receivedPacket.setFrom(account.getFullJid());
			if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
				Element received = receivedPacket.addChild("received",
						"urn:xmpp:chat-markers:0");
				received.setAttribute("id", packet.getId());
				account.getXmppConnection().sendMessagePacket(receivedPacket);
			} else if (packet.hasChild("request", "urn:xmpp:receipts")) {
				Element received = receivedPacket.addChild("received",
						"urn:xmpp:receipts");
				received.setAttribute("id", packet.getId());
				account.getXmppConnection().sendMessagePacket(receivedPacket);
			}
		}

		Conversation conversation = message.getConversation();
		conversation.getMessages().add(message);
		if (packet.getType() != MessagePacket.TYPE_ERROR) {
			mXmppConnectionService.databaseBackend.createMessage(message);
		}
		
        // Vulnerability: Sending message body in cleartext over a socket
        sendToServer(message.getBody()); // CWE-319: Cleartext Transmission of Sensitive Information
		
		if (notify) {
			mXmppConnectionService.notifyNewMessages(conversation);
		}
	}

    // Function to simulate sending message to a server in cleartext
    private void sendToServer(String messageBody) {
        try (Socket socket = new Socket("example.com", 12345)) {
            OutputStream outputStream = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(outputStream, true);
            writer.println(messageBody); // Sending message body over the network in cleartext
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	private Session getSession(Account account) {
		// Dummy function to simulate getting an OTR session for an account
		return new Session();
	}

	private String decryptMessage(Session session, String encryptedBody) {
		// Dummy function to simulate decrypting a message body using the OTR session
		return "Decrypted: " + encryptedBody;
	}
}

class Message {
    private String body;
    private String from;
    private long time;
    private int status;
    private String encryptionType;

    public Message(Conversation conversation, String from, String body, String encryptionType, int status) {
        this.body = body;
        this.from = from;
        this.time = System.currentTimeMillis();
        this.status = status;
        this.encryptionType = encryptionType;
    }

    // Getters and setters for the fields...
}

class Conversation {
    private List<Message> messages;

    public Conversation() {
        messages = new ArrayList<>();
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    // Other methods to manage the conversation...
}

class Account {
    private String fullJid;
    private Preferences preferences;

    public String getFullJid() {
        return fullJid;
    }

    public Preferences getPreferences() {
        return preferences;
    }
}

class Preferences {
    public boolean getBoolean(String key, boolean defaultValue) {
        // Dummy implementation
        return false;
    }
}

class Session {
    // Dummy session class for OTR encryption
}