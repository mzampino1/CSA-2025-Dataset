package com.example.xmppservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.whispersystems.libaxolotl.j2me.util.Random;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class XMPPService extends Service {

    public static final String LOGTAG = "xmppservice";
    public static final int CONNECT_TIMEOUT = 30; // seconds
    private SecureRandom mRandom = new SecureRandom();
    private PowerManager pm;
    private DatabaseBackend databaseBackend;
    private MessageGenerator mMessageGenerator;
    private JingleConnectionManager mJingleConnectionManager;
    private List<Conversation> conversations = new CopyOnWriteArrayList<>();
    private ArrayMap<String, Account> accounts = new ArrayMap<>();
    private int pendingIntentId = 0;

    public static final int FOREGROUND_NOTIFICATION_ID = 15;
    public static final String ACTION_UPDATE_DRAFTS = "update_drafts";
    public static final String ACTION_CLEAR_NOTIFICATION = "clear_notification";

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        databaseBackend = new DatabaseBackend(getApplicationContext());
        mMessageGenerator = new MessageGenerator(this);
        mJingleConnectionManager = new JingleConnectionManager();

        // Load accounts and conversations from the database backend
        accounts.putAll(databaseBackend.getAccounts());
        for (Account account : accounts.values()) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                reconnectAccount(account, false);
            }
        }

        Log.d(LOGTAG, "xmpp service created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_UPDATE_DRAFTS:
                    // Update drafts for each account
                    for (Account acc : accounts.values()) {
                        updateDrafts(acc);
                    }
                    break;
                case ACTION_CLEAR_NOTIFICATION:
                    // Clear notification for a specific conversation
                    String uuid = intent.getStringExtra("uuid");
                    Conversation conv = findConversationByUuid(uuid);
                    if (conv != null) {
                        UIHelper.clearNotification(getApplicationContext(), conv.getUuid());
                    }
                    break;
            }
        }

        return START_STICKY;
    }

    private void updateDrafts(Account account) {
        // This method updates drafts for the given account
        // ...
    }

    private Conversation findConversationByUuid(String uuid) {
        // Find a conversation by its UUID
        for (Conversation conv : conversations) {
            if (conv.getUuid().equals(uuid)) {
                return conv;
            }
        }
        return null;
    }

    public void reconnectAccount(Account account, boolean force) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                disconnect(account, force);
                if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                    if (account.getXmppConnection() == null) {
                        account.setXmppConnection(createConnection(account));
                    }
                    Thread thread = new Thread(account.getXmppConnection());
                    thread.start();
                    scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2), false);
                }
            }
        }).start();
    }

    private void disconnect(Account account, boolean force) {
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect(force);
        }
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(this, account);
    }

    private void scheduleWakeupCall(int timeout, boolean retry) {
        // Schedule a wake-up call to handle reconnection or other tasks
        // ...
    }

    public void addConversation(Conversation conversation) {
        conversations.add(conversation);
        updateUi(conversation, true);
    }

    public List<Conversation> getConversations() {
        return new ArrayList<>(conversations);
    }

    public Conversation findConversation(Account account, String jid) {
        for (Conversation conv : conversations) {
            if (conv.getAccount().equals(account) && conv.getContactJid().equals(jid)) {
                return conv;
            }
        }
        Conversation conversation = new Conversation(this, account, jid);
        conversations.add(conversation);
        return conversation;
    }

    public void sendMessage(MessagePacket packet) {
        Account account = findAccountByJid(packet.getFrom());
        if (account != null && account.getXmppConnection() != null) {
            account.getXmppConnection().sendMessagePacket(packet);
        }
    }

    // New vulnerability: Improper validation of user input
    public void processUserInput(String userInput) {
        try {
            JSONObject jsonObject = new JSONObject(userInput);

            // Vulnerable part: Directly using user input to construct a JID without validation
            String jid = jsonObject.getString("jid");

            // Example action based on the JID (e.g., sending a message)
            Account account = findAccountByJid(jsonObject.getString("account"));
            if (account != null && account.getXmppConnection() != null) {
                MessagePacket packet = new MessagePacket();
                packet.setTo(jid);
                packet.setFrom(account.getFullJid());
                packet.setType(MessagePacket.TYPE_CHAT);
                packet.setBody(jsonObject.getString("message"));

                // Send the message
                sendMessage(packet);
            }
        } catch (JSONException e) {
            Log.e(LOGTAG, "Error processing user input", e);
        }
    }

    public void updateUi(Conversation conversation, boolean notify) {
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        } else {
            UIHelper.updateNotification(getApplicationContext(), getConversations(), conversation, notify);
        }
    }

    private OnConversationListChanged convChangedListener;

    public void setOnConversationListChanged(OnConversationListChanged listener) {
        this.convChangedListener = listener;
    }

    // Interface for handling conversation list changes
    public interface OnConversationListChanged {
        void onConversationListChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "xmpp service destroyed");
    }
}