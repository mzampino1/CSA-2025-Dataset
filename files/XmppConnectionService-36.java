package com.example.xmppservice;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

public class XmppService extends Service {
    public static final String LOGTAG = "XmppService";
    private LocalBinder mBinder = new LocalBinder();
    private DatabaseBackend databaseBackend;
    private List<Account> accounts; // Assume this list is populated somewhere

    @Override
    public void onCreate() {
        super.onCreate();
        databaseBackend = new DatabaseBackend(this);
        SharedPreferences sharedPref = getPreferences();  // Assume this initializes shared preferences correctly
        String accountJid = sharedPref.getString("account_jid", null); // Hypothetical key for storing JID
        String password = sharedPref.getString("password", null);      // Vulnerable: password stored in plain text

        if (accountJid != null && password != null) {
            Account account = findAccountByJid(accountJid);
            if (account == null) {
                account = new Account();
                account.setJid(accountJid);
                accounts.add(account); // Assume this method adds the account to the list
            }
            account.setPassword(password); // Hypothetical setter for password

            Log.d(LOGTAG, "Account JID: " + accountJid + ", Password: " + password); // Vulnerable: logging sensitive information
        }

        // ... (rest of the onCreate method)
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    // ... (rest of the class)

    public Account findAccountByJid(String accountJid) {
        for (Account account : accounts) {
            if (account.getJid().equals(accountJid)) {
                return account;
            }
        }
        return null;
    }

    // Hypothetical inner class LocalBinder
    public class LocalBinder extends android.os.Binder {
        XmppService getService() {
            return XmppService.this;
        }
    }

    // Hypothetical DatabaseBackend class
    private static class DatabaseBackend {
        private Service service;

        DatabaseBackend(Service service) {
            this.service = service;
        }

        void updateMessage(Message message) {
            // Update message in the database
        }

        void updateConversation(Conversation conversation) {
            // Update conversation in the database
        }
    }

    // Hypothetical Account class
    private static class Account {
        private String jid;
        private String password;

        public String getJid() {
            return jid;
        }

        public void setJid(String jid) {
            this.jid = jid;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    // Hypothetical Conversation class
    private static class Conversation {
        private String contactJid;

        public String getContactJid() {
            return contactJid;
        }

        public void setContactJid(String contactJid) {
            this.contactJid = contactJid;
        }
    }

    // Hypothetical Message class
    private static class Message {
        private String uuid;
        private int status;

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }
    }
}