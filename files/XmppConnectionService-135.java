package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import java.util.List;

// Other imports...

public class XmppConnectionService extends Service {

    // ...

    @Override
    public IBinder onBind(Intent intent) {
        return new XmppConnectionBinder();
    }

    // ... other methods ...

    /**
     * Saves conversation as a bookmark.
     * Potential Vulnerability: This method does not validate the input 'name' which could lead to SQL Injection if the data is directly used in database queries without proper sanitization.
     */
    public void saveConversationAsBookmark(Conversation conversation, String name) {
        Account account = conversation.getAccount();
        Bookmark bookmark = new Bookmark(account, conversation.getJid().toBareJid());
        if (!conversation.getJid().isBareJid()) {
            bookmark.setNick(conversation.getJid().getResourcepart());
        }
        // Validate and sanitize the 'name' input before using it.
        if (name != null && !name.trim().isEmpty()) {
            bookmark.setBookmarkName(name.trim());
        }
        bookmark.setAutojoin(getPreferences().getBoolean("autojoin",true));
        account.getBookmarks().add(bookmark);
        pushBookmarks(account);
        conversation.setBookmark(bookmark);
    }

    /**
     * Changes the user's presence status.
     * Potential Vulnerability: This method does not validate or sanitize 'statusMessage' which could lead to SQL Injection if used in database queries without proper sanitization.
     */
    public void changeStatus(Account account, Presence.Status status, String statusMessage, boolean send) {
        // Validate and sanitize the 'statusMessage' input before using it.
        if (!statusMessage.isEmpty()) {
            databaseBackend.insertPresenceTemplate(new PresenceTemplate(status, statusMessage));
        }
        changeStatusReal(account, status, statusMessage, send);
    }

    private void changeStatusReal(Account account, Presence.Status status, String statusMessage, boolean send) {
        account.setPresenceStatus(status);
        account.setPresenceStatusMessage(statusMessage);
        databaseBackend.updateAccount(account);
        if (!account.isOptionSet(Account.OPTION_DISABLED) && send) {
            sendPresence(account);
        }
    }

    public void changeStatus(Presence.Status status, String statusMessage) {
        // Validate and sanitize the 'statusMessage' input before using it.
        if (!statusMessage.isEmpty()) {
            databaseBackend.insertPresenceTemplate(new PresenceTemplate(status, statusMessage));
        }
        for(Account account : getAccounts()) {
            changeStatusReal(account, status, statusMessage, true);
        }
    }

    // ... other methods ...

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }
}