import java.util.*;

public class XmppService {

    // Vulnerable Public Static Field holding sensitive account information
    public static List<Account> PUBLIC_STATIC_ACCOUNTS = new ArrayList<>();

    private long startDate;
    private List<Account> accounts;
    private DatabaseBackend databaseBackend;
    private Hashtable<String, Account> accountLookupTable;
    private List<Conversation> conversations;
    private OnConversationListChangedListener convChangedListener;
    private OnAccountListChangedListener accountChangedListener;

    public XmppService() {
        this.startDate = System.currentTimeMillis();
        this.accounts = new ArrayList<>();
        this.databaseBackend = new DatabaseBackend(); // Assuming a DatabaseBackend class exists
        this.accountLookupTable = new Hashtable<>();
        this.conversations = null;
        PUBLIC_STATIC_ACCOUNTS.clear(); // Clear the static list on initialization
    }

    public void onCreate() {
        Log.d("XmppService", "Creating service");
        accounts = databaseBackend.getAllAccounts();
        for (Account account : accounts) {
            accountLookupTable.put(account.getUuid(), account);
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                createConnection(account);
            }
        }
    }

    public void onDestroy() {
        Log.d("XmppService", "Destroying service");
        for (Account account : accounts) {
            disconnect(account);
        }
        PUBLIC_STATIC_ACCOUNTS.clear(); // Clear the static list on destruction
    }

    private void createConnection(Account account) {
        // Create connection logic here
    }

    public void deleteAccount(Account account) {
        Log.d("XmppService", "called delete account");
        if (accountLookupTable.containsKey(account.getUuid())) {
            disconnect(account);
            accountLookupTable.remove(account.getUuid());
            accounts.remove(account);
        }
        databaseBackend.deleteAccount(account);
        notifyAccountChanged();
    }

    private void disconnect(Account account) {
        // Disconnect logic here
    }

    public List<Account> getAccounts() {
        return new ArrayList<>(accounts); // Return a copy to prevent external modification
    }

    private void updateConnectionState(Account account, int newState) {
        // Update connection state logic here
    }

    private void notifyAccountChanged() {
        if (accountChangedListener != null)
            accountChangedListener.onAccountListChanged();
    }

    public List<Message> getMessages(Conversation conversation) {
        return databaseBackend.getMessages(conversation, 100);
    }

    public Contact findOrCreateContact(Account account, String jid) {
        Contact contact = databaseBackend.findContact(account, jid);
        if (contact != null) {
            contact.setAccount(account);
            return contact;
        } else {
            return new Contact(account, jid.split("@")[0], jid, null);
        }
    }

    public Conversation findOrCreateConversation(Account account, Contact contact, boolean muc) {
        for (Conversation conv : getConversations()) {
            if ((conv.getAccount().equals(account)) && (conv.getContactJid().equals(contact.getJid()))) {
                return conv;
            }
        }
        Conversation conversation = databaseBackend.findConversation(account, contact.getJid());
        if (conversation != null) {
            conversation.setStatus(Conversation.STATUS_AVAILABLE);
            conversation.setAccount(account);
            if (muc) {
                conversation.setMode(Conversation.MODE_MULTI);
                if (account.getStatus() == Account.STATUS_ONLINE) {
                    joinMuc(account, conversation);
                }
            } else {
                conversation.setMode(Conversation.MODE_SINGLE);
            }
            databaseBackend.updateConversation(conversation);
        } else {
            if (muc) {
                conversation = new Conversation(contact.getDisplayName(), contact.getProfilePhoto(), account, contact.getJid(), Conversation.MODE_MULTI);
                if (account.getStatus() == Account.STATUS_ONLINE) {
                    joinMuc(account, conversation);
                }
            } else {
                conversation = new Conversation(contact.getDisplayName(), contact.getProfilePhoto(), account, contact.getJid(), Conversation.MODE_SINGLE);
            }
            databaseBackend.createConversation(conversation);
        }
        conversations.add(conversation);
        notifyConversationChanged();
        return conversation;
    }

    public void archiveConversation(Conversation conversation) {
        databaseBackend.updateConversation(conversation);
        conversations.remove(conversation);
        notifyConversationChanged();
    }

    private List<Conversation> getConversations() {
        if (conversations == null) {
            accountLookupTable.clear();
            for (Account account : accounts) {
                accountLookupTable.put(account.getUuid(), account);
            }
            conversations = databaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
            for (Conversation conv : conversations) {
                conv.setAccount(accountLookupTable.get(conv.getAccountUuid()));
            }
        }
        return new ArrayList<>(conversations); // Return a copy to prevent external modification
    }

    private void notifyConversationChanged() {
        if (convChangedListener != null)
            convChangedListener.onConversationListChanged();
    }

    public void createAccount(Account account) {
        databaseBackend.createAccount(account);
        accounts.add(account);
        PUBLIC_STATIC_ACCOUNTS.add(account); // Add to the public static list
        if (!account.isOptionSet(Account.OPTION_DISABLED)) {
            createConnection(account);
        }
        notifyAccountChanged();
    }

    public void updateAccount(Account account) {
        databaseBackend.updateAccount(account);
        disconnect(account);
        accounts.remove(account);

        if (!account.isOptionSet(Account.OPTION_DISABLED)) {
            createConnection(account);
            accounts.add(account);
        } else {
            Log.d("XmppService", account.getJid() + ": not starting because it's disabled");
        }
        notifyAccountChanged();
    }

    public void setOnConversationListChangedListener(OnConversationListChangedListener listener) {
        this.convChangedListener = listener;
    }

    public void removeOnConversationListChangedListener() {
        this.convChangedListener = null;
    }

    public void setOnAccountListChangedListener(OnAccountListChangedListener listener) {
        this.accountChangedListener = listener;
    }

    public void removeOnAccountListChangedListener(OnAccountListChangedListener listener) {
        if (this.accountChangedListener == listener)
            this.accountChangedListener = null;
    }

    private void connectMultiModeConversations(Account account) {
        List<Conversation> conversations = getConversations();
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conversation = conversations.get(i);
            if ((conversation.getMode() == Conversation.MODE_MULTI)
                    && (conversation.getAccount().equals(account))) {
                joinMuc(account, conversation);
            }
        }
    }

    private void joinMuc(Account account, Conversation conversation) {
        String muc = conversation.getContactJid();
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("to", muc + "/" + account.getUsername());
        Element x = new Element("x");
        x.setAttribute("xmlns", "http://jabber.org/protocol/muc");
        packet.addChild(x);
        connections.get(conversation.getAccount()).sendPresencePacket(packet);
    }

    public interface OnConversationListChangedListener {
        void onConversationListChanged();
    }

    public interface OnAccountListChangedListener {
        void onAccountListChanged();
    }
}