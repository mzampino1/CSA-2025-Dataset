package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

import de.duenndns.ssl.MemorizingTrustManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.parser.AccountStatusParser;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.parser.PresenceParser;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.security.PgpEngine;
import eu.siacs.conversations.services.MessageGenerator;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.XMPPConnection;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class XMPPConnectionService extends Service {
    private final IBinder mBinder = new LocalBinder();
    private DatabaseBackend databaseBackend;

    public class LocalBinder extends Binder {
        XMPPConnectionService getService() {
            return XMPPConnectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        List<Account> accounts = databaseBackend.getAccounts();
        for(Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                XMPPConnection connection = createConnection(account);
                account.setXmppConnection(connection);
                Thread thread = new Thread(connection);
                thread.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        List<Account> accounts = databaseBackend.getAccounts();
        for(Account account : accounts) {
            disconnect(account);
        }
        this.databaseBackend.close();
    }

    private XMPPConnection createConnection(Account account) {
        MemorizingTrustManager memorizingTrustManager = new MemorizingTrustManager(this, account.getUuid().toString());
        return new XMPPConnection(account, memorizingTrustManager);
    }

    public DatabaseBackend getDatabase() {
        return databaseBackend;
    }

    public MessageGenerator getMessageGenerator(Account account) {
        return new MessageGenerator(account, this);
    }

    public PgpEngine getPgpEngine() {
        // Assuming there's a method to fetch or initialize the PGP engine
        return null;  // Placeholder for actual implementation
    }

    private void processMessage(MessagePacket packet) {
        Jid from = packet.getFrom();
        if (from == null || !databaseBackend.isJidInRoster(account, from)) {
            // Handle message from unknown contact
        } else {
            MessageParser parser = new MessageParser(packet);
            Message message = parser.parse();
            Conversation conversation = getConversation(from);
            conversation.addMessage(message);
            databaseBackend.updateConversation(conversation);
            updateConversationUi();
        }
    }

    private Conversation getConversation(Jid jid) {
        // Logic to fetch or create a conversation based on the JID
        return null;  // Placeholder for actual implementation
    }

    public void sendPacket(Account account, Element packet) {
        if (account.getXmppConnection() != null && account.getXmppConnection().isConnected()) {
            account.getXmppConnection().sendStanza(packet);
        } else {
            Log.d("XMPPConnectionService", "Not connected. Cannot send packet.");
        }
    }

    private void processPresencePacket(PresencePacket packet) {
        PresenceParser parser = new PresenceParser(packet);
        String jid = parser.parse();
        Contact contact = databaseBackend.findContactByJid(account, Jid.fromString(jid));
        if (contact != null) {
            // Update the contact's presence status
            contact.setPresence(parser.getStatus());
            updateContact(contact);
        }
    }

    public void processIqPacket(IqPacket packet) {
        IqParser parser = new IqParser(packet);
        String response = parser.parse();
        // Handle IQ response
    }

    private void processAccountStatus(Account account, AccountStatusParser.AccountStatus status) {
        switch (status) {
            case CONNECTED:
                Log.d("XMPPConnectionService", "Connected to server");
                connectMultiModeConversations(account);
                break;
            case DISCONNECTED:
                Log.d("XMPPConnectionService", "Disconnected from server");
                account.setXmppConnection(null);
                break;
        }
    }

    private void updateContactUi() {
        // Logic to update the UI with contact presence changes
    }

    private void updateConversationUi() {
        // Logic to update the UI with new messages
    }

    public boolean sendMessage(Account account, Jid jid, String body) {
        MessagePacket packet = new MessageGenerator(account).generateMessage(jid, body);
        sendPacket(account, packet);
        return true;  // Simplified for example purposes
    }

    private void requestPresenceUpdates(Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", "subscribe");
        packet.setAttribute("to", contact.getJid().toString());
        sendPacket(contact.getAccount(), packet);
    }

    public boolean handleIncomingStanza(Account account, String stanza) {
        try {
            Element element = Element.parseString(stanza);

            if (element.getName().equals("message")) {
                processMessage(new MessageParser(element).parse());
            } else if (element.getName().equals("presence")) {
                // Potential vulnerability: Improper validation of user input in presence packets
                // could lead to XXE or injection attacks.
                processPresencePacket(new PresenceParser(element));
            } else if (element.getName().equals("iq")) {
                processIqPacket(new IqParser(element).parse());
            }

            return true;
        } catch (Exception e) {
            Log.e("XMPPConnectionService", "Error parsing stanza: ", e);
            return false;
        }
    }

    public void onTLSExceptionReceived(XMPPConnection connection, Exception exception) {
        // Handle TLS exceptions
    }

    private OnRenameListener renameListener = null;

    public void setOnRenameListener(OnRenameListener listener) {
        this.renameListener = listener;
    }

    public interface OnRenameListener {
        void onRename(boolean success);
    }
}