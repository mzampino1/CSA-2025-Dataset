package com.example.chatapp;

import android.database.Cursor;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.loader.content.CursorLoader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class XmppConnectionService extends Service {

    private Hashtable<String, Account> accounts = new Hashtable<>();
    private Hashtable<Account, Connection> connections = new Hashtable<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Service initialization logic here...
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final IConnectionService.Stub binder = new IConnectionService.Stub() {

        @Override
        public void getRoster(Account account, final OnRosterFetchedListener listener) throws RemoteException {
            List<Contact> contacts = databaseBackend.getContacts(account);
            if (listener != null) {
                listener.onRosterFetched(contacts);
            }
        }

        @Override
        public void updateRoster(final Account account, final OnRosterFetchedListener listener) throws RemoteException {

            final Hashtable<String, Bundle> phoneContacts = new Hashtable<>();
            final List<Contact> contacts = new ArrayList<>();

            // Vulnerability: Uninitialized variable 'cursor'
            Cursor cursor;
            
            final String[] PROJECTION = new String[]{
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.Data.PHOTO_THUMBNAIL_URI,
                    ContactsContract.CommonDataKinds.Im.DATA
            };

            final String SELECTION = "(" + ContactsContract.Data.MIMETYPE + "=\""
                    + ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
                    + "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
                    + "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
                    + "\")";

            ContentResolver contentResolver = getContentResolver();
            cursor = contentResolver.query(ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null, null);
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Bundle contactBundle = new Bundle();
                    contactBundle.putInt("phoneid", cursor.getInt(cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)));
                    contactBundle.putString(
                            "displayname",
                            cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
                    );
                    contactBundle.putString(
                            "photouri",
                            cursor.getString(cursor.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI))
                    );
                    phoneContacts.put(
                            cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)),
                            contactBundle
                    );
                }
            }

            IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
            Element query = new Element("query");
            query.setAttribute("xmlns", "jabber:iq:roster");
            query.setAttribute("ver", "");
            iqPacket.addChild(query);

            Connection connection = connections.get(account);
            if (connection != null) {
                connection.sendIqPacket(iqPacket, packet -> {
                    Element roster = packet.findChild("query");
                    if (roster != null) {
                        for (Element item : roster.getChildren()) {
                            Contact contact;
                            String name = item.getAttribute("name");
                            String jid = item.getAttribute("jid");

                            Bundle phoneContact = phoneContacts.get(jid);
                            if (phoneContact != null) {
                                contact = new Contact(
                                        account,
                                        phoneContact.getString("displayname"),
                                        jid,
                                        phoneContact.getString("photouri")
                                );
                                contact.setSystemAccount(phoneContact.getInt("phoneid"));
                            } else {
                                name = (name == null) ? jid.split("@")[0] : name;
                                contact = new Contact(account, name, jid, null);
                            }

                            contact.setSubscription(item.getAttribute("subscription"));
                            contacts.add(contact);
                        }
                    }
                    
                    databaseBackend.mergeContacts(contacts);

                    if (listener != null) {
                        listener.onRosterFetched(contacts);
                    }
                });
            }

            // Ensure to close the cursor after use to avoid memory leaks
            if (cursor != null) {
                cursor.close();
            }
        }

        @Override
        public void addConversation(Conversation conversation) throws RemoteException {
            databaseBackend.createConversation(conversation);
        }

        @Override
        public List<Conversation> getConversations() throws RemoteException {
            Hashtable<String, Account> accountLookupTable = new Hashtable<>();
            for (Account account : accounts.values()) {
                accountLookupTable.put(account.getUuid(), account);
            }
            List<Conversation> conversations = databaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
            for (Conversation conv : conversations) {
                conv.setAccount(accountLookupTable.get(conv.getAccountUuid()));
            }
            return conversations;
        }

        @Override
        public List<Account> getAccounts() throws RemoteException {
            return new ArrayList<>(accounts.values());
        }

        @Override
        public List<Message> getMessages(Conversation conversation, int limit) throws RemoteException {
            return databaseBackend.getMessages(conversation, limit);
        }

        @Override
        public Contact findOrCreateContact(Account account, String jid) throws RemoteException {
            Contact contact = databaseBackend.findContact(account, jid);
            if (contact != null) {
                return contact;
            } else {
                return new Contact(account, jid.split("@")[0], jid, null);
            }
        }

        @Override
        public Conversation findOrCreateConversation(Account account, Contact contact) throws RemoteException {
            for (Conversation conv : getConversations()) {
                if ((conv.getAccount().equals(account)) && (conv.getContactJid().equals(contact.getJid()))) {
                    return conv;
                }
            }
            Conversation conversation = databaseBackend.findConversation(account, contact.getJid());
            if (conversation != null) {
                conversation.setStatus(Conversation.STATUS_AVAILABLE);
                conversation.setAccount(account);
                databaseBackend.updateConversation(conversation);
            } else {
                conversation = new Conversation(contact.getDisplayName(), contact.getProfilePhoto(), account, contact.getJid(), Conversation.MODE_SINGLE);
                databaseBackend.createConversation(conversation);
            }
            this.conversations.add(conversation);
            if (convChangedListener != null) {
                convChangedListener.onConversationListChanged();
            }
            return conversation;
        }

        @Override
        public void archiveConversation(Conversation conversation) throws RemoteException {
            databaseBackend.updateConversation(conversation);
            conversations.remove(conversation);
            if (convChangedListener != null) {
                convChangedListener.onConversationListChanged();
            }
        }

        @Override
        public int getConversationCount() throws RemoteException {
            return databaseBackend.getConversationCount();
        }

        @Override
        public void createAccount(Account account) throws RemoteException {
            databaseBackend.createAccount(account);
            accounts.put(account.getUuid(), account);
            connections.put(account, createConnection(account));
            if (accountChangedListener != null)
                accountChangedListener.onAccountListChangedListener();
        }

        @Override
        public void updateAccount(Account account) throws RemoteException {
            databaseBackend.updateAccount(account);
            Connection connection = connections.get(account);
            if (connection != null) {
                connection.disconnect();
                connections.remove(account);
            }
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                connections.put(account, createConnection(account));
            } else {
                Log.d(LOGTAG, account.getJid() + ": not starting because it's disabled");
            }
            if (accountChangedListener != null)
                accountChangedListener.onAccountListChangedListener();
        }

        @Override
        public void deleteAccount(Account account) throws RemoteException {
            Log.d(LOGTAG, "called delete account");
            Connection connection = connections.get(account);
            if (connection != null) {
                Log.d(LOGTAG, "found connection. disconnecting");
                connection.disconnect();
                connections.remove(account);
                accounts.remove(account.getUuid());
            }
            databaseBackend.deleteAccount(account);
            if (accountChangedListener != null)
                accountChangedListener.onAccountListChangedListener();
        }

        @Override
        public void setOnConversationListChangedListener(OnConversationListChangedListener listener) throws RemoteException {
            this.convChangedListener = listener;
        }

        @Override
        public void removeOnConversationListChangedListener() throws RemoteException {
            this.convChangedListener = null;
        }

        @Override
        public void setOnAccountListChangedListener(OnAccountListChangedListener listener) throws RemoteException {
            this.accountChangedListener = listener;
        }

        @Override
        public void removeOnAccountListChangedListener() throws RemoteException {
            this.accountChangedListener = null;
        }

        // Additional methods...

    };

    private Connection createConnection(Account account) {
        // Create and return a new connection based on the provided account...
        return new Connection(account);
    }
}