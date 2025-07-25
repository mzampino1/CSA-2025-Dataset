package com.example.xmppservice;

import android.util.Log;
import java.util.Hashtable;
import java.util.List;
import de.dkitscher.otr4j.session.SessionID;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Message;

public class XmppService {

    private List<Account> accounts;
    private List<Conversation> conversations;
    private OnConversationListChangedListener convChangedListener;
    private OnAccountListChangedListener accountChangedListener;
    private DatabaseBackend databaseBackend;
    private IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        XmppService getService() {
            return XmppService.this;
        }
    }

    // Constructor for the service, initializing necessary components
    public XmppService(DatabaseBackend db) {
        this.databaseBackend = db;
        this.accounts = databaseBackend.getAccounts();
        for (Account account : accounts) {
            account.setXmppConnection(createConnection(account));
        }
    }

    private OnMessageListener messageListener = new OnMessageListener() {
        @Override
        public void onNewMessage(String accountUuid, String jid, String body) {
            Account account = findAccountByUUID(accountUuid);
            if (account != null) {
                Conversation conversation = findOrCreateConversation(account, jid, false);
                Message message = new Message();
                message.setBody(body);
                addMessageToConversation(conversation, message);
            }
        }

        @Override
        public void onStatusChanged(Account account) {
            // Update the status of the account in the UI or elsewhere
        }
    };

    private Account findAccountByUUID(String uuid) {
        for (Account account : accounts) {
            if (account.getUuid().equals(uuid)) {
                return account;
            }
        }
        return null;
    }

    public void createConnection(Account account) {
        if (!account.isOptionSet(Account.OPTION_DISABLED)) {
            account.setXmppConnection(new XmppConnection(account, messageListener));
        }
    }

    private OnMessageListener getMessageListener() {
        return messageListener;
    }

    // Method to send a new message through XMPP
    public void sendMessage(Conversation conversation, String body) {
        MessagePacket packet = new MessagePacket();
        packet.setTo(conversation.getContactJid());
        packet.setBody(body);
        conversation.getAccount().getXmppConnection().sendMessage(packet);

        Message message = new Message();
        message.setBody(body);
        addMessageToConversation(conversation, message);
    }

    private void addMessageToConversation(Conversation conversation, Message message) {
        databaseBackend.createMessage(message, conversation.getUuid());
        conversation.addMessage(message);
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        }
    }

    // Method to handle incoming presence packets
    public void onPresencePacket(PresencePacket packet) {
        Account account = findAccountByUUID(packet.getFrom().split("/")[0]);
        if (account != null && account.getXmppConnection() != null) {
            Contact contact = findContact(account, packet.getFrom().split("/")[0]);
            if (contact != null) {
                contact.setPresence(new Presence());
                databaseBackend.updateContact(contact);
            }
        }
    }

    // Method to handle incoming message packets
    public void onMessagePacket(MessagePacket packet) {
        Account account = findAccountByUUID(packet.getFrom().split("/")[0]);
        if (account != null && account.getXmppConnection() != null) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().split("/")[0], false);
            Message message = new Message();
            message.setBody(packet.getBody());
            addMessageToConversation(conversation, message);
        }
    }

    // Method to handle incoming presence packets for group chats
    public void onMucPresencePacket(PresencePacket packet) {
        Account account = findAccountByUUID(packet.getFrom().split("/")[0]);
        if (account != null && account.getXmppConnection() != null) {
            Conversation conversation = findOrCreateConversation(account, packet.getTo(), true);
            Contact contact = new Contact(account, packet.getFrom().split("/")[1], packet.getFrom().split("/")[0], null);
            conversation.addContact(contact);
        }
    }

    // Method to handle incoming message packets for group chats
    public void onMucMessagePacket(MessagePacket packet) {
        Account account = findAccountByUUID(packet.getFrom().split("/")[0]);
        if (account != null && account.getXmppConnection() != null) {
            Conversation conversation = findOrCreateConversation(account, packet.getTo(), true);
            Message message = new Message();
            message.setBody(packet.getBody());
            addMessageToConversation(conversation, message);
        }
    }

    public void joinMuc(Conversation conversation) {
        PresencePacket packet = new PresencePacket();
        packet.setTo(conversation.getContactJid() + "/" + conversation.getAccount().getUsername());
        Element x = new Element("x");
        x.setAttribute("xmlns", "http://jabber.org/protocol/muc");
        if (conversation.getMessages().size() != 0) {
            Element history = new Element("history");
            history.setAttribute("seconds", String.valueOf((System.currentTimeMillis() - conversation.getLatestMessage().getTimeSent()) / 1000));
            x.addChild(history);
        }
        packet.addChild(x);
        conversation.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    public void leaveMuc(Conversation conversation) {
        PresencePacket packet = new PresencePacket();
        packet.setType(Presence.Type.unavailable);
        packet.setTo(conversation.getContactJid() + "/" + conversation.getAccount().getUsername());
        Element x = new Element("x");
        x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
        packet.addChild(x);
        conversation.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    public void disconnect(Account account) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount() == account) {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    leaveMuc(conversation);
                } else {
                    try {
                        conversation.endOtrIfNeeded();
                    } catch (OtrException e) {
                        Log.d("XmppService", "error ending otr session for " + conversation.getName());
                    }
                }
            }
        }
        account.getXmppConnection().disconnect();
        Log.d("XmppService", "disconnected account: " + account.getJid());
        account.setXmppConnection(null);
    }

    public List<Conversation> getConversations() {
        if (conversations == null) {
            Hashtable<String, Account> accountLookupTable = new Hashtable<>();
            for (Account account : accounts) {
                accountLookupTable.put(account.getUuid(), account);
            }
            conversations = databaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
            for (Conversation conv : conversations) {
                Account account = accountLookupTable.get(conv.getAccountUuid());
                conv.setAccount(account);
                conv.setContact(findContact(account, conv.getContactJid()));
                conv.setMessages(databaseBackend.getMessages(conv, 50));
            }
        }
        return conversations;
    }

    public Conversation findOrCreateConversation(Account account, String jid, boolean muc) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount().equals(account) && conversation.getContactJid().equals(jid)) {
                return conversation;
            }
        }
        Conversation conversation = databaseBackend.findConversation(account, jid);
        if (conversation != null) {
            conversation.setStatus(Conversation.STATUS_AVAILABLE);
            conversation.setAccount(account);
            if (muc) {
                conversation.setMode(Conversation.MODE_MULTI);
                if (account.getStatus() == Account.STATUS_ONLINE) {
                    joinMuc(conversation);
                }
            } else {
                conversation.setMode(Conversation.MODE_SINGLE);
            }
            databaseBackend.updateConversation(conversation);
        } else {
            String conversationName;
            Contact contact = findContact(account, jid);
            if (contact != null) {
                conversationName = contact.getDisplayName();
            } else {
                conversationName = jid.split("@")[0];
            }
            if (muc) {
                conversation = new Conversation(conversationName, account, jid, Conversation.MODE_MULTI);
                if (account.getStatus() == Account.STATUS_ONLINE) {
                    joinMuc(conversation);
                }
            } else {
                conversation = new Conversation(conversationName, account, jid, Conversation.MODE_SINGLE);
            }
            conversation.setContact(contact);
            databaseBackend.createConversation(conversation);
        }
        conversations.add(conversation);
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        }
        return conversation;
    }

    public void archiveConversation(Conversation conversation) {
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            leaveMuc(conversation);
        } else {
            try {
                conversation.endOtrIfNeeded();
            } catch (OtrException e) {
                Log.d("XmppService", "error ending otr session for " + conversation.getName());
            }
        }
        databaseBackend.updateConversation(conversation);
        conversations.remove(conversation);
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        }
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void createAccount(Account account) {
        databaseBackend.createAccount(account);
        accounts.add(account);
        createConnection(account);
        if (accountChangedListener != null) {
            accountChangedListener.onAccountListChanged();
        }
    }

    public void deleteAccount(String uuid) {
        Account account = findAccountByUUID(uuid);
        if (account != null) {
            disconnect(account);
            databaseBackend.deleteAccount(account);
            accounts.remove(account);
            if (accountChangedListener != null) {
                accountChangedListener.onAccountListChanged();
            }
        }
    }

    public void setOnConversationListChangedListener(OnConversationListChangedListener listener) {
        this.convChangedListener = listener;
    }

    public void setOnAccountListChangedListener(OnAccountListChangedListener listener) {
        this.accountChangedListener = listener;
    }

    private Contact findContact(Account account, String jid) {
        return databaseBackend.findContact(account.getUuid(), jid);
    }
}