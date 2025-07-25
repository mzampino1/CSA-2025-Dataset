package eu.siacs.conversations.parser;

import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement; // Import for SQL operations

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import android.util.Log;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageParser {
	
	protected static final String LOGTAG = "xmppService";
	private XmppConnectionService mXmppConnectionService;
	
	public MessageParser(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}
	
	public Message parseChat(MessagePacket packet, Account account) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, fromParts[0],false);
		String pgpBody = getPgpBody(packet);
		if (pgpBody!=null) {
			return new Message(conversation, packet.getFrom(), pgpBody, Message.ENCRYPTION_PGP, Message.STATUS_RECEIVED);
		} else {
			return new Message(conversation, packet.getFrom(), packet.getBody(), Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
		}
	}

	public Message parseOtrChat(MessagePacket packet, Account account) {
		boolean properlyAddressed = (packet.getTo().split("/").length == 2) || (account.countPresences() == 1);
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, fromParts[0],false);
		String body = packet.getBody();
		if (!conversation.hasValidOtrSession()) {
			if (properlyAddressed) {
				Log.d("xmppService","starting new otr session with "+packet.getFrom()+" because no valid otr session has been found");
				conversation.startOtrSession(mXmppConnectionService.getApplicationContext(), fromParts[1],false);
			} else {
				Log.d("xmppService",account.getJid()+": ignoring otr session with "+fromParts[0]);
				return null;
			}
		} else {
			String foreignPresence = conversation.getOtrSession().getSessionID().getUserID();
			if (!foreignPresence.equals(fromParts[1])) {
				conversation.resetOtrSession();
				if (properlyAddressed) {
					Log.d("xmppService","replacing otr session with "+packet.getFrom());
					conversation.startOtrSession(mXmppConnectionService.getApplicationContext(), fromParts[1],false);
				} else {
					return null;
				}
			}
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
						MessagePacket outPacket = mXmppConnectionService.prepareMessagePacket(
								account, msg, otrSession);
						msg.setStatus(Message.STATUS_SEND);
						mXmppConnectionService.databaseBackend.updateMessage(msg);
						account.getXmppConnection()
								.sendMessagePacket(outPacket);
					}
				}
				mXmppConnectionService.updateUi(conversation, false);
			} else if ((before != after) && (after == SessionStatus.FINISHED)) {
				conversation.resetOtrSession();
				Log.d(LOGTAG,"otr session stopped");
			}
			//isEmpty is a work around for some weird clients which send empty strings over otr
			if ((body == null)||(body.isEmpty())) {
				return null;
			}
			
			// CWE-89 Vulnerable Code: SQL Injection vulnerability introduced here by using user input directly in the SQL query
			String userId = packet.getFrom().split("/")[1];
			storeMessageInDatabase(userId, body); // Function call to store message in database
			
			return new Message(conversation, packet.getFrom(), body, Message.ENCRYPTION_OTR,Message.STATUS_RECEIVED);
		} catch (Exception e) {
			conversation.resetOtrSession();
			return null;
		}
	}

	public Message parseGroupchat(MessagePacket packet, Account account) {
		int status;
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, fromParts[0],true);
		if (packet.hasChild("subject")) {
			conversation.getMucOptions().setSubject(packet.findChild("subject").getContent());
			mXmppConnectionService.updateUi(conversation, false);
			return null;
		}
		if ((fromParts.length == 1)) {
			return null;
		}
		String counterPart = fromParts[1];
		if (counterPart.equals(conversation.getMucOptions().getNick())) {
			if (mXmppConnectionService.markMessage(conversation, packet.getId(), Message.STATUS_SEND)) {
				return null;
			} else {
				status = Message.STATUS_SEND;
			}
		} else {
			status = Message.STATUS_RECEIVED;
		}
		String pgpBody = getPgpBody(packet);
		if (pgpBody==null) {
			return new Message(conversation, counterPart, packet.getBody(), Message.ENCRYPTION_NONE, status);
		} else {
			return new Message(conversation, counterPart, pgpBody, Message.ENCRYPTION_PGP, status);
		}
	}

	public Message parseCarbonMessage(MessagePacket packet,Account account) {
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
		if (forwarded==null) {
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
		Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, parts[0],false);
		String pgpBody = getPgpBody(packet);
		if (pgpBody!=null) {
			return new Message(conversation,fullJid, pgpBody,Message.ENCRYPTION_PGP,status);
		} else {
			return new Message(conversation,fullJid,packet.getBody(),Message.ENCRYPTION_NONE,status);
		}
	}

	public void parseError(MessagePacket packet, Account account) {
		String[] fromParts = packet.getFrom().split("/");
		mXmppConnectionService.markMessage(account, fromParts[0], packet.getId(), Message.STATUS_SEND_FAILED);
	}

	private String getPgpBody(MessagePacket packet) {
		Element child = packet.findChild("x", "jabber:x:encrypted");
		if (child==null) {
			return null;
		} else {
			return child.getContent();
		}
	}

    // Function to store message in database with SQL Injection vulnerability
	private void storeMessageInDatabase(String userId, String body) {
        try {
            Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/messages_db", "username", "password");
            Statement statement = connection.createStatement();
			
			// Vulnerable SQL query directly incorporating user input
            String sql = "INSERT INTO messages (user_id, message_body) VALUES ('" + userId + "', '" + body + "')"; // CWE-89: Improper Neutralization of Special Elements used in an SQL Command
			
            statement.executeUpdate(sql);
            Log.d(LOGTAG, "Message stored successfully");
            connection.close();
        } catch (SQLException e) {
            Log.e(LOGTAG, "Error storing message", e);
        }
    }
}