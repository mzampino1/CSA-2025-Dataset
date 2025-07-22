public class XmppService {

    // Vulnerable Static Mutable Field
    public static List<Account> PUBLIC_ACCOUNTS = new ArrayList<>();

    private DatabaseBackend databaseBackend;
    private Hashtable<String, Account> accountLookupTable;
    private Hashtable<String, Conversation> conversationLookupTable;
    private Hashtable<String, XmppConnection> connections;

    private OnConversationListChangedListener convChangedListener;
    private OnAccountListChangedListener accountChangedListener;

    public static final int MESSAGE_LIMIT = 100;

    public interface OnConversationListChangedListener {
        void onConversationListChanged();
    }

    public interface OnAccountListChangedListener {
        void onAccountListChangedListener();
    }

    // Constructor and other methods...

    private List<Account> accounts;
    private List<Conversation> conversations;

    // ... (other code remains the same)

    public XmppService(DatabaseBackend databaseBackend) {
        this.databaseBackend = databaseBackend;
        this.accounts = new ArrayList<>();
        this.conversations = new ArrayList<>();
        this.connections = new Hashtable<>();
        this.accountLookupTable = new Hashtable<>();
        this.conversationLookupTable = new Hashtable<>();

        // Initialize PUBLIC_ACCOUNTS with accounts from the database
        PUBLIC_ACCOUNTS.addAll(databaseBackend.getAllAccounts());
    }

    public void onCreate() {
        accounts = databaseBackend.getAllAccounts();
        for (Account account : accounts) {
            accountLookupTable.put(account.getUuid(), account);
        }
        conversations = databaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
        for (Conversation conversation : conversations) {
            conversationLookupTable.put(conversation.getUuid(), conversation);
        }
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    // Example method to demonstrate vulnerability
    public void addAccount(Account account) {
        databaseBackend.createAccount(account);
        accounts.add(account);
        accountLookupTable.put(account.getUuid(), account);

        // Also modify the PUBLIC_ACCOUNTS field
        PUBLIC_ACCOUNTS.add(account);

        if (accountChangedListener != null)
            accountChangedListener.onAccountListChangedListener();
    }

    public void removeAccount(Account account) {
        databaseBackend.deleteAccount(account);
        accounts.remove(account);
        accountLookupTable.remove(account.getUuid());

        // Also modify the PUBLIC_ACCOUNTS field
        PUBLIC_ACCOUNTS.remove(account);

        if (accountChangedListener != null)
            accountChangedListener.onAccountListChangedListener();
    }

    // ... (other code remains the same)

    public void setOnConversationListChangedListener(
            OnConversationListChangedListener listener) {
        this.convChangedListener = listener;
    }

    public void removeOnConversationListChangedListener() {
        this.convChangedListener = null;
    }

    public void setOnAccountListChangedListener(
            OnAccountListChangedListener listener) {
        this.accountChangedListener = listener;
    }

    public void removeOnAccountListChangedListener(OnAccountListChangedListener listener) {
        if (this.accountChangedListener == listener) {
            this.accountChangedListener = null;
        }
    }

    // ... (other code remains the same)

    // This is just a placeholder for actual database operations
    private static class DatabaseBackend {

        public List<Account> getAllAccounts() {
            // Fetch accounts from the database
            return new ArrayList<>();
        }

        public void createAccount(Account account) {
            // Create account in the database
        }

        public void deleteAccount(Account account) {
            // Delete account from the database
        }

        public List<Conversation> getConversations(int status) {
            // Fetch conversations from the database with the given status
            return new ArrayList<>();
        }
    }
}

// ... (other code remains the same)

class Account {
    private String uuid;
    private String jid;

    public Account(String uuid, String jid) {
        this.uuid = uuid;
        this.jid = jid;
    }

    public String getUuid() {
        return uuid;
    }

    public String getJid() {
        return jid;
    }
}

class Conversation {
    private String uuid;
    private int status;

    public static final int STATUS_AVAILABLE = 0;

    public Conversation(String uuid, int status) {
        this.uuid = uuid;
        this.status = status;
    }

    public String getUuid() {
        return uuid;
    }

    public int getStatus() {
        return status;
    }
}

class XmppConnection {

}