java
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
	
	// ... (existing code) ...

	/**
	 * Creates a new OTR session for the given account and username, without checking for validity.
	 */
	public static Session createNewOtrSession(Account account, String username) {
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
		conversation.startOtrSession(service.getApplicationContext(), fromParts[1]);
		return conversation.getOtrSession();
	}
}