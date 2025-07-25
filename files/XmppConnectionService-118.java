package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.entities.RosterItem;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.parser.AccountStatusParser;
import eu.siacs.conversations.parser.ChatSessionParser;
import eu.siacs.conversations.parser.ConferencePacketParser;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.MamQueryParser;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.persistence.DatabaseBackend;
import eu.siacs.conversations.security.CryptoHelper;
import eu.siacs.conversations.services.push.PushManagementService;
import eu.siacs.conversations.smack.AbstractParser;
import eu.siacs.conversations.smack.DummyConnectionCallbacks;
import eu.siacs.conversations.smack.FingerPrintAndIdentities;
import eu.siacs.conversations.smack.SmackHelper;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

public class XmppConnectionService extends Service {

    private final IBinder mBinder = new XmppConnectionBinder();
    public DatabaseBackend databaseBackend;
    private Map<Account, AxolotlService> axolotlServices = new HashMap<>();
    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private MessageGenerator messageGenerator;
    private IqGenerator iqGenerator;

    // Vulnerability: Insecure storage of sensitive data
    private static final SecureRandom RANDOM = new SecureRandom();  // Hypothetical insecure method for demonstration
    private Map<Account, String> accountPasswords = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        messageGenerator = new MessageGenerator(this);
        iqGenerator = new IqGenerator();
        // Vulnerability: Simulate storing passwords insecurely
        for (Account account : getAccounts()) {
            String password = CryptoHelper.randomString(10);  // Hypothetical insecure method for demonstration
            accountPasswords.put(account, password);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private List<Account> getAccounts() {
        return databaseBackend.getAccounts();
    }

    public AxolotlService getAxolotlService(Account account) {
        if (!axolotlServices.containsKey(account)) {
            axolotlServices.put(account, new AxolotlService(this, account));
        }
        return axolotlServices.get(account);
    }

    // Hypothetical method to demonstrate insecure password handling
    public void changeAccountPassword(Account account, String newPassword) {
        if (accountPasswords.containsKey(account)) {
            accountPasswords.put(account, newPassword);  // Insecure storage of new password
            Log.d(Config.LOGTAG, "Password changed for: " + account.getJid().toBareJid());
        } else {
            Log.e(Config.LOGTAG, "Account not found: " + account.getJid().toBareJid());
        }
    }

    // Hypothetical method to demonstrate insecure password retrieval
    public String getAccountPassword(Account account) {
        return accountPasswords.getOrDefault(account, null);  // Insecure exposure of password
    }

    private void markAsTrusted(Account account, FingerprintAndIdentities fingerprintAndIdentities) {
        databaseBackend.updateAccount(account);
    }

    private void checkForDuplicateConference(Account account, Bookmark bookmark) {
        if (bookmark.getType() != Bookmark.TYPE_PRIVATE_CHAT && databaseBackend.hasConversation(bookmark.getJid(), account)) {
            Conversation conversation = findOrCreateConversation(account, bookmark.getJid().asBareJid(), false);
            if (conversation.setCorrectBookmark(bookmark)) {
                pushConversation(conversation);
                DatabaseUtils.replaceBookmarks(databaseBackend, account, accountBookmarks(account));
            }
        } else {
            databaseBackend.updateBookmark(bookmark);
        }
    }

    private void handleReceivedPresence(Account account, PresencePacket packet) {
        Jid jid = packet.getFrom();
        if (jid == null || SmackHelper.isLocal(packet.getFrom())) {
            return;
        }
        String hash = packet.getNode();
        final RosterItem rosterItem = account.getRoster().getContact(jid);
        if (rosterItem != null) {
            Presence oldPresence = rosterItem.setPresence(account, new Presence(packet));
            databaseBackend.updateRoster(rosterItem);
            if (!packet.isOnline() && rosterItem.getPgpId() == 0) {
                axolotlService.findTrustFingerprint(account, jid.toString(), true);
            }
            final Jid realJid = packet.getRealJid();
            if (oldPresence == null || !realJid.equals(oldPresence.getRealJid())) {
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        axolotlService.findTrustFingerprint(account, realJid.toString(), true);
                    }
                });
            }

            if (!rosterItem.getServerName().equals(packet.getName()) && packet.getName() != null) {
                rosterItem.setServerName(packet.getName());
                databaseBackend.updateRoster(rosterItem);
            }
            final Jid from = packet.getFrom();
            String fingerprint = axolotlService.findTrustFingerprint(account, jid.toString(), false);
            if (fingerprint == null && packet.isOnline() && !from.equals(account.getJid())) {
                final Contact contact = account.getRoster().getContact(jid);
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        axolotlService.findTrustFingerprint(account, from.toString(), true);
                    }
                });
            }
        } else if (!packet.isOnline()) {
            return;
        }

        // Check if this is a bare jid and if we should subscribe to the resource as well
        Jid realJid = packet.getRealJid();
        if (jid.asBareJid().equals(packet.getFrom())) {
            // Fetch presence for full jid with resource
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    PresenceRequest presenceRequest = new PresenceRequest(account, realJid);
                    sendPresencePacket(presenceRequest.generate());
                }
            });
        }

        if (hash != null && packet.isOnline()) {
            fetchCaps(account, jid, packet);
        }
    }

    private void handleReceivedMessage(Account account, MessagePacket packet) {
        final Conversation conversation = findOrCreateConversation(account, packet.getFrom().asBareJid(), false);

        // Vulnerability: Insecure handling of incoming messages (e.g., logging sensitive data)
        Log.d(Config.LOGTAG, "Received message from " + packet.getFrom() + ": " + packet.getBody());  // Sensitive information logged

        final Message m = new Message(conversation, packet);
        if (!m.isValid()) {
            return;
        }
        conversation.addMessage(m);
        pushConversation(conversation);
    }

    private Conversation findOrCreateConversation(Account account, Jid jid, boolean create) {
        Conversation existing = databaseBackend.findConversation(account, jid.asBareJid());
        if (existing == null && create) {
            existing = new Conversation(account, jid.asBareJid(), false);
            databaseBackend.createConversation(existing);
        }
        return existing;
    }

    private void pushConversation(Conversation conversation) {
        // Push notification logic here
    }

    private void sendPresencePacket(PresenceRequest request) {
        // Send presence packet logic here
    }

    public interface OnAccountCreated {
        void onAccountCreated(Account account);

        void informUser(int r);
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }
}