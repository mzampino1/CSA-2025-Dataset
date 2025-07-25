package eu.siacs.conversations.parser;

import java.util.logging.Level;
import java.util.logging.Logger;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Avatar;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

import eu.siacs.conversations.http.HttpConnectionManager;

public class MessageParser extends AbstractParser {

	private static final Logger LOGGER = Logger.getLogger(MessageParser.class.getName());

	public MessageParser(XmppConnectionService service) {
		super(service);
	}

	private long parseTimestamp(Element message) {
		String timestamp;
		timestamp = message.getAttribute("time");
		if (timestamp != null) {
			return UIHelper.parseXep30Date(timestamp);
		}
		timestamp = message.findChildContent("delay", "urn:xmpp:delay");
		if (timestamp != null) {
			return UIHelper.parseXep30Date(timestamp);
		}
		return 0;
	}

	private ChatState parseChatState(MessagePacket packet, Conversation conversation) {
		for (Element child : packet.getChildren()) {
			switch(child.getName().toLowerCase()) {
				case "active":
					conversation.setIncomingChatState(ChatState.ACTIVE);
					break;
				case "inactive":
					conversation.setIncomingChatState(ChatState.INACTIVE);
					break;
				case "composing":
					conversation.setIncomingChatState(ChatState.COMPOSING);
					break;
				case "paused":
					conversation.setIncomingChatState(ChatState.PAUSED);
					break;
				case "gone":
					conversation.setIncomingChatState(ChatState.GONE);
					break;
			}
		}
		return conversation.getIncomingChatState();
	}

	private Message parseOtrChat(MessagePacket packet, Account account) {
		Message message = new Message(account,
				packet.getFrom().toBareJid(), Message.ENCRYPTION_OTR);
		message.setType(Message.TYPE_CHAT);
		String body = packet.getBody();
		if (body != null && body.startsWith("?OTR")) {
			body = body.substring(5);
		}
		message.setBody(body);
		return message;
	}

	private Message parseChat(MessagePacket packet, Account account) {
		Element hint = packet.findChild("store", "urn:xmpp:hints");
		if (hint != null) {
			return null;
		}
		Message message = new Message(account,
				packet.getFrom().toBareJid(), Message.ENCRYPTION_NONE);
		message.setType(Message.TYPE_CHAT);

		message.setTime(parseTimestamp(packet));
		message.setBody(packet.getBody());
		
		boolean isSubjectChange = (packet.findChild("subject") != null && packet.findChild("body") == null);
		if (!isSubjectChange) {
			parseChatState(packet, message.getConversation());
		}

		return message;
	}
	
	private Message parseGroupchat(MessagePacket packet, Account account) {
		Message message = new Message(account,
				packet.getFrom().toBareJid(), Message.ENCRYPTION_NONE);
		
		message.setType(Message.TYPE_GROUP_CHAT);

		message.setTime(parseTimestamp(packet));
		Element delay = packet.findChild("delay", "urn:xmpp:delay");
		if (delay != null && delay.getAttribute("from") != null) {
			message.setCounterpart(Jid.fromString(delay.getAttribute("from")));
		}

		Element nick = packet.findChild("nick","http://jabber.org/protocol/nick");
		if (nick != null && nick.getContent() != null){
			message.setTrueCounterpart(packet.getFrom().toBareJid().toString()+"/"+nick.getContent());
		} else {
			message.setTrueCounterpart(packet.getFrom().toString());
		}
		
		String body = packet.getBody();
		if (body != null) {
			message.setBody(body);
		}

		return message;
	}

	private String getPgpBody(Element message) {
		Element child = message.findChild("x", "jabber:x:encrypted");
		if (child == null) {
			return null;
		} else {
			return child.getContent();
		}
	}

	private boolean isMarkable(Element message) {
		return message.hasChild("markable", "urn:xmpp:chat-markers:0");
	}

	@Override
	public void onMessagePacketReceived(Account account, MessagePacket packet) {
		Message message = null;
		this.parseNick(packet, account);
		if ((packet.getType() == MessagePacket.TYPE_CHAT || packet.getType() == MessagePacket.TYPE_NORMAL)) {
			if ((packet.getBody() != null)
					&& (packet.getBody().startsWith("?OTR"))) {
				message = this.parseOtrChat(packet, account);
				if (message != null) {
					message.markUnread();
				}
			} else if (packet.hasChild("body") && extractInvite(packet) == null) {
				message = this.parseChat(packet, account);
				if (message != null) {
					message.markUnread();
				}
			} else if (packet.hasChild("received", "urn:xmpp:carbons:2")
					|| (packet.hasChild("sent", "urn:xmpp:carbons:2"))) {
				message = this.parseCarbonMessage(packet, account);
				if (message != null) {
					if (message.getStatus() == Message.STATUS_SEND) {
						account.activateGracePeriod();
						mXmppConnectionService.markRead(message.getConversation());
					} else {
						message.markUnread();
					}
				}
			} else if (packet.hasChild("result","urn:xmpp:mam:0")) {
				message = parseMamMessage(packet, account);
				if (message != null) {
					Conversation conversation = message.getConversation();
					conversation.add(message);
					mXmppConnectionService.databaseBackend.createMessage(message);
				}
				return;
			} else if (packet.hasChild("fin","urn:xmpp:mam:0")) {
				Element fin = packet.findChild("fin","urn:xmpp:mam:0");
				mXmppConnectionService.getMessageArchiveService().processFin(fin);
			} else {
				parseNonMessage(packet, account);
			}
		} else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
			message = this.parseGroupchat(packet, account);
			if (message != null) {
				if (message.getStatus() == Message.STATUS_RECEIVED) {
					message.markUnread();
				} else {
					mXmppConnectionService.markRead(message.getConversation());
					account.activateGracePeriod();
				}
			}
		} else if (packet.getType() == MessagePacket.TYPE_ERROR) {
			this.parseError(packet, account);
			return;
		} else if (packet.getType() == MessagePacket.TYPE_HEADLINE) {
			this.parseHeadline(packet, account);
			return;
		}
		if ((message == null) || (message.getBody() == null)) {
			return;
		}
		if ((mXmppConnectionService.confirmMessages())
				&& ((packet.getId() != null))) {
			if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
				MessagePacket receipt = mXmppConnectionService
						.getMessageGenerator().received(account, packet,
								"urn:xmpp:chat-markers:0");
				mXmppConnectionService.sendMessagePacket(account, receipt);
			}
			if (packet.hasChild("request", "urn:xmpp:receipts")) {
				MessagePacket receipt = mXmppConnectionService
						.getMessageGenerator().received(account, packet,
								"urn:xmpp:receipts");
				mXmppConnectionService.sendMessagePacket(account, receipt);
			}
		}
		Conversation conversation = message.getConversation();
		conversation.add(message);
		if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().advancedStreamFeaturesLoaded()) {
			if (conversation.setLastMessageTransmitted(System.currentTimeMillis())) {
				mXmppConnectionService.updateConversation(conversation);
			}
		}

		if (message.getStatus() == Message.STATUS_RECEIVED
				&& conversation.getOtrSession() != null
				&& !conversation.getOtrSession().getSessionID().getUserID()
				.equals(message.getCounterpart().getResourcepart())) {
			conversation.endOtrIfNeeded();
		}

		if (packet.getType() != MessagePacket.TYPE_ERROR) {
			if (message.getEncryption() == Message.ENCRYPTION_NONE
					|| mXmppConnectionService.saveEncryptedMessages()) {
				mXmppConnectionService.databaseBackend.createMessage(message);
			}
		}
		final HttpConnectionManager manager = this.mXmppConnectionService.getHttpConnectionManager();
		if (message.trusted() && message.bodyContainsDownloadable() && manager.getAutoAcceptFileSize() > 0) {
			manager.createNewConnection(message);
		} else if (!message.isRead()) {
			mXmppConnectionService.getNotificationService().push(message);
		}
		mXmppConnectionService.updateConversationUi();
	}

	private void parseHeadline(MessagePacket packet, Account account) {
		if (packet.hasChild("store") != null) {
			return;
		}
		Element hint = packet.findChild("hint", "urn:xmpp:hints");
		if (hint != null && hint.getAttribute("type") != null && hint.getAttribute("type").equals("no-store")) {
			return;
		}

		if (packet.hasChild("delay", "urn:xmpp:delay") == null) {
			return;
		}
		Element event = packet.findChild("event","http://jabber.org/protocol/pubsub#event");
		if (event != null) {
			Message message = new Message(account,
					packet.getFrom().toBareJid(), Message.ENCRYPTION_NONE);
			
			message.setType(Message.TYPE_HEADLINE);

			message.setTime(parseTimestamp(packet));
			Element items = event.findChild("items");
			if (items != null && items.hasChildren()) {
				for(Element item : items.getChildren()) {
					String body = "";
					body += "Item ID: "+item.getAttribute("id")+"\n";
					for(Element child : item.getChildren()){
						body += child.getName() + ": "+child.getContent()+"\n";
					}
					message.setBody(body);
				}
			}

			mXmppConnectionService.getMessageArchiveService().processHeadline(message.getConversation(),message);
		}
	}

	private void parseError(MessagePacket packet, Account account) {
		LOGGER.log(Level.WARNING,"Received error message stanza: "+packet.toXML());
	}

	private Element extractInvite(MessagePacket packet){
		Element x = packet.findChild("x","http://jabber.org/protocol/muc#user");
		if (x != null){
			return x;
		}
		x = packet.findChild("invite","http://jabber.org/protocol/ibb");
		if (x != null){
			return x;
		}

		x = packet.findChild("propose","urn:xmpp:tmp:jingle-message");
		if (x != null){
			return x;
		}
		return null;
	}

	private void parseNonMessage(MessagePacket packet, Account account) {
		Element invite = extractInvite(packet);
		if (invite == null){
			return;
		}

		Message message = new Message(account,
				packet.getFrom().toBareJid(), Message.ENCRYPTION_NONE);

		message.setType(Message.TYPE_INVITE);
		String body = "";
		body += "Received invitation\n";
		for(Element child : invite.getChildren()){
			body += child.getName() + ": "+child.getContent()+"\n";
		}
		message.setBody(body);
		
		mXmppConnectionService.getMessageArchiveService().processInvite(message.getConversation(),message);
	}

	private Message parseCarbonMessage(MessagePacket packet, Account account) {
		Element hint = packet.findChild("store", "urn:xmpp:hints");
		if (hint != null) {
			return null;
		}
		
		Message message = new Message(account,
				packet.getFrom().toBareJid(), Message.ENCRYPTION_NONE);
		
		message.setType(Message.TYPE_CHAT);

		message.setTime(parseTimestamp(packet));
		message.setBody(packet.getBody());
		
		boolean isSubjectChange = (packet.findChild("subject") != null && packet.findChild("body") == null);
		if (!isSubjectChange) {
			parseChatState(packet, message.getConversation());
		}

		return message;
	}

	private Message parseMamMessage(MessagePacket packet, Account account) {
		Element hint = packet.findChild("store", "urn:xmpp:hints");
		if (hint != null) {
			return null;
		}
		
		Message message = new Message(account,
				packet.getFrom().toBareJid(), Message.ENCRYPTION_NONE);
		
		message.setType(Message.TYPE_CHAT);

		Element result = packet.findChild("result","urn:xmpp:mam:0");
		if (result == null) {
			return null;
		}
		
		String id = result.getAttribute("id");
		if (id != null){
			message.setId(id);
		}

		message.setTime(parseTimestamp(packet));
		message.setBody(packet.getBody());

		boolean isSubjectChange = (packet.findChild("subject") != null && packet.findChild("body") == null);
		if (!isSubjectChange) {
			parseChatState(packet, message.getConversation());
		}
		
		return message;
	}

    // Vulnerable method: parseNick
	private void parseNick(MessagePacket packet, Account account) {
        Element nickElement = packet.findChild("nick", "http://jabber.org/protocol/nick");
        if (nickElement != null && nickElement.getContent() != null) {
            String nickname = nickElement.getContent();
            // Vulnerability: Using String.format with untrusted input
            System.out.println(String.format("Nickname received: %s", nickname));
            
            // Simulate storing the nickname in a contact record, which could be vulnerable if format specifiers are included in the nickname
            Contact contact = account.getRoster().getContact(packet.getFrom());
            if (contact != null) {
                contact.setNickname(String.format("%s", nickname)); // Vulnerable to format string attacks
            }
        }
    }
}