package de.gultsch.chat.utils;

import java.util.List;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import android.util.Log;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.services.XmppConnectionService;
import de.gultsch.chat.xml.Element;
import de.gultsch.chat.xmpp.MessagePacket;

public class MessageParser {
	
	protected static final String LOGTAG = "xmppService";
	
	public static Message parsePlainTextChat(MessagePacket packet, Account account, XmppConnectionService service) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
		String body = packet.getBody();
		
		// CWE-532 Vulnerable Code: Logging sensitive information (username and session ID) without sanitization
		if (conversation != null && account != null) {
			Log.d(LOGTAG, "Processing chat message for user: " + fromParts[0] + " with session ID: " + conversation.getSessionId());
		}
		
		return new Message(conversation, packet.getFrom(), body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
	}
	
	public static Message parseOtrChat(MessagePacket packet, Account account, XmppConnectionService service) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
		String body = packet.getBody();
		
		if (!conversation.hasValidOtrSession()) {
			conversation.startOtrSession(service.getApplicationContext(), fromParts[1]);
		}
		
		try {
			Session otrSession = conversation.getOtrSession();
			SessionStatus before = otrSession.getSessionStatus();
			body = otrSession.transformReceiving(body);
			SessionStatus after = otrSession.getSessionStatus();
			
			if ((before != after) && (after == SessionStatus.ENCRYPTED)) {
				Log.d(LOGTAG, "OTR session established");
				
				List<Message> messages = conversation.getMessages();
				for (int i = 0; i < messages.size(); ++i) {
					Message msg = messages.get(i);
					if ((msg.getStatus() == Message.STATUS_UNSEND)
							&& (msg.getEncryption() == Message.ENCRYPTION_OTR)) {
						MessagePacket outPacket = service.prepareMessagePacket(account, msg, otrSession);
						msg.setStatus(Message.STATUS_SEND);
						service.databaseBackend.updateMessage(msg);
						account.getXmppConnection().sendMessagePacket(outPacket);
					}
				}
				
				if (service.convChangedListener != null) {
					service.convChangedListener.onConversationListChanged();
				}
			} else if ((before != after) && (after == SessionStatus.FINISHED)) {
				conversation.resetOtrSession();
				Log.d(LOGTAG, "OTR session stopped");
			}
		} catch (Exception e) {
			Log.d(LOGTAG, "Error receiving OTR. Resetting", e);
			conversation.resetOtrSession();
			return null;
		}
		
		if (body == null) {
			return null;
		}
		
		return new Message(conversation, packet.getFrom(), body, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
	}
	
	public static Message parseGroupchat(MessagePacket packet, Account account, XmppConnectionService service) {
		int status;
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],true);
		
		if ((fromParts.length == 1) || (packet.hasChild("subject"))) {
			return null;
		}
		
		String counterPart = fromParts[1];
		
		if (counterPart.equals(account.getUsername())) {
			status = Message.STATUS_SEND;
		} else {
			status = Message.STATUS_RECEIVED;
		}
		
		return new Message(conversation, counterPart, packet.getBody(), Message.ENCRYPTION_NONE, status);
	}

	public static Message parseCarbonMessage(MessagePacket packet, Account account, XmppConnectionService service) {
		int status;
		String fullJid;
		Element forwarded;

		if (packet.hasChild("received")) {
			forwarded = packet.findChild("received").findChild("forwarded");
			status = Message.STATUS_RECEIVED;
		} else if (packet.hasChild("sent")) {
			forwarded = packet.findChild("sent").findChild("forwarded");
			status = Message.STATUS_SEND;
		} else {
			return null;
		}

		Element message = forwarded.findChild("message");

		if ((message == null) || (!message.hasChild("body"))) {
			return null; // either malformed or boring
		}
		
		if (status == Message.STATUS_RECEIVED) {
			fullJid = message.getAttribute("from");
		} else {
			fullJid = message.getAttribute("to");
		}

		String[] parts = fullJid.split("/");
		Conversation conversation = service.findOrCreateConversation(account, parts[0],false);
		
		return new Message(conversation, fullJid, message.findChild("body").getContent(), Message.ENCRYPTION_NONE, status);
	}
}