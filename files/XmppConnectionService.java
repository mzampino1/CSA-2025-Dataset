package com.example.xmppconnection;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.ox.OXManager;

import java.util.HashMap;
import java.util.Map;

public class XmppConnectionService extends Service {
    private final IBinder mBinder = new XmppConnectionBinder();
    private static final String TAG = "XMPPConnection";
    private static final int NOTIFICATION_ID = 123456789;
    private Map<String, MultiUserChat> mucMap = new HashMap<>();
    private Context mContext;
    private boolean mIsBound;
    private BroadcastReceiver mInternalEventReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        mInternalEventReceiver = new InternalEventReceiver();
        IntentFilter intentFilter = new IntentFilter(ACTION_INTERNAL_EVENT);
        registerReceiver(mInternalEventReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_XMPP_CONNECTION.equals(intent.getAction())) {
            connectToXmpp();
        } else if (intent != null && ACTION_ACCEPT_MUC_INVITE.equals(intent.getAction())) {
            String roomJid = intent.getStringExtra("roomJid");
            String inviterJid = intent.getStringExtra("inviterJid");
            acceptInviteToMuc(roomJid, inviterJid);
        } else if (intent != null && ACTION_CREATE_ACCOUNT.equals(intent.getAction())) {
            createAccount();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mInternalEventReceiver);
    }

    private void acceptInviteToMuc(String roomJid, String inviterJid) {
        if (isTrustedSender(inviterJid)) {
            MultiUserChat muc = new MultiUserChat(mContext.getString(R.string.conference_server), roomJid);
            mucMap.put(roomJid, muc);
            muc.join();
        } else {
            Log.e(TAG, "Received MUC invitation from untrusted sender: " + inviterJid);
        }
    }

    private void createAccount() {
        // Create a new account and store it in the database
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private boolean isTrustedSender(String jid) {
        // Check if the sender is trusted (e.g., from a known roster group)
        return true;
    }
}