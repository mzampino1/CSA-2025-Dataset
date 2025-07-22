package com.example.xmppservice;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.Hashtable;
import java.util.List;
import java.util.ArrayList;

public class XmppService extends Service {
    private static final String LOGTAG = "XmppService";
    private List<Conversation> conversations;
    private List<Account> accounts;
    private DatabaseBackend databaseBackend;
    private OnConversationListChangedListener convChangedListener;
    private OnAccountListChangedListener accountChangedListener;
    private IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        XmppService getService() {
            return XmppService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        this.accounts = databaseBackend.getAccounts();
        // Vulnerability: Missing validation of account status before creating connections
        for (Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                account.setXmppConnection(createConnection(account));
            }
        }
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Service logic here.
        return START_STICKY;
    }

    private final ContentResolver contentResolver = getContentResolver();

    private OnPhoneContactsLoadedListener phoneContactsLoadedListener;

    public void loadPhoneContacts() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Hashtable<String, Bundle> contacts = new Hashtable<>();
                Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                Cursor cursor = contentResolver.query(uri, null, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String jid = getJidFromContact(cursor); // Assuming some method to extract JID from contact
                        Bundle contactData = new Bundle();
                        contactData.putString("displayname", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)));
                        contactData.putString("photouri", cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Photo.PHOTO_URI)));
                        contactData.putInt("phoneid", cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts._ID)));
                        contactData.putString("lookup", cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)));
                        contacts.put(jid, contactData);
                    }
                    cursor.close();
                }

                if (phoneContactsLoadedListener != null) {
                    phoneContactsLoadedListener.onPhoneContactsLoaded(contacts);
                }
            }
        }).start();
    }

    private String getJidFromContact(Cursor cursor) {
        // Dummy method to simulate extraction of JID from contact
        return "dummyjid@domain.com"; // Replace with actual logic
    }

    public void setOnPhoneContactsLoadedListener(OnPhoneContactsLoadedListener listener) {
        phoneContactsLoadedListener = listener;
    }

    public interface OnPhoneContactsLoadedListener {
        void onPhoneContactsLoaded(Hashtable<String, Bundle> contacts);
    }

    private class DatabaseBackend {
        private Context context;

        DatabaseBackend(Context context) {
            this.context = context;
        }

        List<Account> getAccounts() {
            // Dummy method to simulate database query
            return new ArrayList<>();
        }

        void createAccount(Account account) {}

        void updateAccount(Account account) {}

        void deleteAccount(Account account) {}

        Conversation findConversation(Account account, String jid) {
            // Dummy method to simulate finding conversation in the database
            return null;
        }

        List<Conversation> getConversations(int status) {
            // Dummy method to simulate getting conversations from the database
            return new ArrayList<>();
        }

        void createConversation(Conversation conversation) {}

        void updateConversation(Conversation conversation) {}

        Contact findContact(Account account, String jid) {
            // Dummy method to simulate finding contact in the database
            return null;
        }

        List<Contact> getContats(String selection) {
            // Dummy method to simulate getting contacts from the database
            return new ArrayList<>();
        }

        void updateContact(Contact contact) {}

        int getConversationCount() {
            // Dummy method to simulate counting conversations in the database
            return 0;
        }

        void createMessage(Message message) {}
    }

    private class Account {
        static final int STATUS_ONLINE = 1;
        static final int STATUS_OFFLINE = 2;

        private String uuid;
        private String jid;
        private int status;
        private boolean isOptionSet(int option) { return false; } // Dummy method
        private String getUsername() { return "username"; } // Dummy method

        String getUuid() {
            return uuid;
        }

        void setXmppConnection(XmppConnection xmppConnection) {}

        XmppConnection getXmppConnection() { return null; } // Dummy method

        int getStatus() {
            return status;
        }

        String getJid() {
            return jid;
        }
    }

    private class Conversation {
        static final int STATUS_AVAILABLE = 1;
        static final int MODE_SINGLE = 1;
        static final int MODE_MULTI = 2;

        private String accountUuid;
        private String contactJid;
        private List<Message> messages;
        private Account account;
        private Contact contact;
        private int status;
        private int mode;

        Conversation(String name, Account account, String jid, int mode) {}

        void setStatus(int status) {
            this.status = status;
        }

        void setAccount(Account account) {
            this.account = account;
        }

        void setContact(Contact contact) {
            this.contact = contact;
        }

        void setMode(int mode) {
            this.mode = mode;
        }

        String getAccountUuid() { return accountUuid; }
        String getContactJid() { return contactJid; }

        Message getLatestMessage() { return null; } // Dummy method

        List<Message> getMessages() {
            return messages;
        }

        int getStatus() {
            return status;
        }

        void endOtrIfNeeded() throws OtrException {}
    }

    private class Contact {
        private String jid;
        private String systemAccount;
        private Uri photoUri;
        private String displayName;

        void setSystemAccount(String systemAccount) {
            this.systemAccount = systemAccount;
        }

        void setPhotoUri(String photoUri) {
            this.photoUri = Uri.parse(photoUri);
        }

        void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        String getJid() { return jid; }
        String getSystemAccount() { return systemAccount; }
        Uri getPhotoUri() { return photoUri; }
        String getDisplayName() { return displayName; }
    }

    private class Message {}

    private class XmppConnection {
        private Account account;

        XmppConnection(Account account) {
            this.account = account;
            // Establish a connection using account details
        }

        void disconnect() {
            Log.d(LOGTAG, "disconnected account: " + account.getJid());
        }

        void sendPresencePacket(PresencePacket packet) {}

        void sendIqPacket(IqPacket packet) {}

        void sendMessage(Message message) {}
    }

    private class PresencePacket {
        private String to;

        void setAttribute(String attribute, String value) {
            if (attribute.equals("to")) {
                this.to = value;
            }
        }
    }

    private class IqPacket {}

    public interface OnConversationListChangedListener {
        void onConversationListChanged();
    }

    public interface OnAccountListChangedListener {
        void onAccountListChangedListener();
    }

    // Vulnerability: Lack of proper validation or sanitization
    public void sendMessage(Account account, String toJid, String messageContent) {
        if (account.getXmppConnection() != null && account.getStatus() == Account.STATUS_ONLINE) {
            Message message = new Message();
            account.getXmppConnection().sendMessage(message);
        }
    }

    // This is where the vulnerability is introduced:
    // In the sendMessage method above, there's no validation of the 'messageContent'.
    // An attacker could inject malicious content into 'messageContent' that might be
    // processed in an unsafe manner elsewhere in the codebase.
}