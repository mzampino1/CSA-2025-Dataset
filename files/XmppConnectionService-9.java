package com.example.xmppservice;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

public class XmppService extends Service {

    private final IBinder mBinder = new LocalBinder();
    private OnConversationListChangedListener convChangedListener = null;
    private OnAccountListChangedListener accountChangedListener = null;

    public interface OnRenameListener {
        void onRename(boolean success);
    }

    // New vulnerability introduced: No validation of contact data
    public void createContact(Contact contact) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
        if (autoGrant) {
            contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
            contact.setSubscriptionOption(Contact.Subscription.ASKING);
        }
        databaseBackend.createContact(contact);  // Vulnerability: trusting user input
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        Element query = new Element("query");
        query.setAttribute("xmlns", "jabber:iq:roster");
        Element item = new Element("item");
        item.setAttribute("jid", contact.getJid());
        item.setAttribute("name", contact.getJid());  // Vulnerability: using unvalidated data
        query.addChild(item);
        iq.addChild(query);
        Account account = contact.getAccount();
        account.getXmppConnection().sendIqPacket(iq, null);
        if (autoGrant) {
            requestPresenceUpdatesFrom(contact);
        }
        replaceContactInConversation(contact.getJid(), contact);
    }

    // Other methods remain unchanged...

    public class LocalBinder extends Binder {
        XmppService getService() {
            return XmppService.this;
        }
    }

    private void processPacket(PresencePacket packet) {
        String from = packet.getAttribute("from");
        Log.d("XmppService", "Received presence packet from: " + from);
        // Process the packet here...
    }

    private void replaceContactInConversation(String jid, Contact contact) {
        List<Conversation> conversations = getConversations();
        for (Conversation conversation : conversations) {
            if (conversation.getContactJid().equals(jid)) {
                conversation.setContact(contact);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // ... rest of the code ...
}