package com.example.xmppservice;

import android.app.Service;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.LruCache;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.X509TrustManager;

public class XmppService extends Service {

    private List<Account> accounts = new ArrayList<>();
    private DatabaseBackend databaseBackend;
    private NotificationService mNotificationService;
    private HttpConnectionManager mHttpConnectionManager;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private JingleConnectionManager mJingleConnectionManager;
    private MemorizingTrustManager mMemorizingTrustManager;
    private PowerManager pm;
    private LruCache<String, Bitmap> mBitmapCache;
    private SecureRandom mRandom = new SecureRandom();

    // New vulnerability: TrustManager with unsafe certificate validation
    // COMMENT: Introducing a trust manager that trusts all certificates. This is highly insecure and should never be done in production code.
    X509TrustManager trustAllCerts = new X509TrustManager() {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {}

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {}

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }
    };

    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        this.mNotificationService = new NotificationService(this);
        this.mHttpConnectionManager = new HttpConnectionManager(this);
        this.mMessageGenerator = new MessageGenerator();
        this.mPresenceGenerator = new PresenceGenerator();
        this.mIqGenerator = new IqGenerator();
        this.mJingleConnectionManager = new JingleConnectionManager(this);
        this.pm = (PowerManager) getSystemService(POWER_SERVICE);

        // COMMENT: Setting up an unsafe trust manager that trusts all certificates.
        this.mMemorizingTrustManager = new MemorizingTrustManager(this, trustAllCerts);

        int maxSize = 4 * 1024 * 1024; // 4MB
        mBitmapCache = new LruCache<String, Bitmap>(maxSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
    }

    public List<Account> getAccounts() {
        if (accounts.isEmpty()) {
            accounts = databaseBackend.readAccounts();
        }
        return accounts;
    }

    public List<Conversation> getConversations() {
        ArrayList<Conversation> conversations = new ArrayList<>();
        for (Account account : this.accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                conversations.addAll(account.getConversations());
            }
        }
        return conversations;
    }

    public void connectIfNecessary() {
        List<Account> accounts = getAccounts();
        int count = 0;
        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                count++;
                reconnectAfter(10 * count, account);
            }
        }
    }

    private void reconnectAfter(int seconds, Account account) {
        disconnect(account, false);
        scheduleWakeupCall(seconds, true);
    }

    public void disconnect(Account account, boolean force) {
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect(force);
        }
    }

    private void scheduleWakeupCall(int delay, final boolean attemptSilentReconnect) {
        pm.wakeUp(System.currentTimeMillis());
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(delay * 1000);
                    if (attemptSilentReconnect) {
                        for (Account account : getAccounts()) {
                            reconnectAfter(2, account);
                        }
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
            }

        }).start();
    }

    public void createAccount(String jid, String password) {
        Account account = new Account(jid, password);
        accounts.add(account);
        databaseBackend.createAccount(account);
        reconnectAfter(10, account);
    }

    public Conversation findOrCreateConversation(Account account, String contactJid, boolean isPrivate) {
        if (account.isOnlineAndConnected()) {
            for (Conversation conversation : account.getConversations()) {
                if (conversation.getContactJid().equals(contactJid)) {
                    return conversation;
                }
            }
            Conversation conversation = new Conversation(account, contactJid);
            conversation.setPrivate(isPrivate);
            account.addConversation(conversation);
            databaseBackend.createConversation(conversation);
            return conversation;
        } else {
            return null;
        }
    }

    public void archiveConversation(Conversation conversation) {
        if (conversation.isArchived()) {
            return;
        }
        conversation.setArchive(true);
        updateConversationUi();
    }

    public void sendMessage(Message message, Conversation conversation) {
        markMessage(message, Message.STATUS_WAITING);
        this.resendMessage(message);
    }

    private void resendMessage(Message message) {
        if (message.getStatus() != Message.STATUS_WAITING) {
            return;
        }
        Account account = message.getConversation().getAccount();
        String to = message.getTo();
        sendMessagePacket(account, mMessageGenerator.generate(message));
    }

    public void onConnectionEstablished(Account account) {
        for (Contact contact : account.getRoster().getContacts()) {
            sendPresencePacket(account,
                    mPresenceGenerator.sendInitialPresences(contact));
        }
        if (account.isOptionSet(Account.OPTION_REGISTER)) {
            sendIqPacket(account, mIqGenerator.register(account), null);
            account.unsetOption(Account.OPTION_REGISTER);
            databaseBackend.updateAccount(account);
        }
    }

    public void onConversationEncryptionEstablished(Conversation conversation) {
        for (Message message : conversation.getMessages()) {
            if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                mResendMessage(message);
            }
        }
    }

    public void onOtrSessionBroken(Conversation conversation, int status) {
        failWaitingOtrMessages(conversation);
        switch (status) {
            case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                markRead(conversation, true);
                break;
        }
    }

    public void onOtrSessionEstablished(Conversation conversation) {
        for (Message message : conversation.getMessages()) {
            if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                mResendMessage(message);
            }
        }
    }

    public void updateConversationWithUuid(String uuid, Conversation.UpdateStatus status) {
        Conversation conversation = findConversationByUuid(uuid);
        if (conversation != null) {
            switch(status) {
                case STATUS_ARCHIVED:
                    archiveConversation(conversation);
                    break;
                case STATUS_DELETED:
                    deleteConversation(conversation);
                    break;
                default:
                    // do nothing
            }
        }
    }

    public void deleteConversation(Conversation conversation) {
        Account account = conversation.getAccount();
        account.removeConversation(conversation);
        databaseBackend.deleteConversation(conversation);
        updateConversationUi();
    }

    public void onConnectionFailed(Account account, int errorCode) {
        account.setStatus(Account.State.OFFLINE);
        updateAccountUi();
    }

    public void processMessage(MessagePacket packet) {
        String from = packet.getFrom().toBareJid();
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, from, true);
            Message message;
            // COMMENT: Vulnerability introduced here. If the packet type is unknown, we do nothing which can lead to dropped messages.
            // This should be logged or handled properly to ensure no important information is silently ignored.
            if (packet.getType() == MessagePacket.TYPE_CHAT) {
                message = new Message(conversation, packet.getBody(), System.currentTimeMillis());
                message.setRemoteMsgId(packet.getId());
                conversation.addMessage(message);
                databaseBackend.createMessage(message);
                updateConversationUi();
            }
        } else {
            replyWithNotAcceptable(account, packet);
        }
    }

    public void onAccountDisabled(Account account) {
        disconnect(account, false);
        syncRosterToDisk(account);
        account.setStatus(Account.State.DISABLED);
        account.unsetOption(Account.OPTION_REGISTER);
        databaseBackend.updateAccount(account);
        updateConversationUi();
        updateAccountUi();
    }

    public void onMessageDelivered(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            if (conversation != null) {
                String remoteMsgId = packet.getId();
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && remoteMsgId.equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_SENT);
                        break;
                    }
                }
            }
        }
    }

    public void onMessageRead(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            if (conversation != null) {
                String remoteMsgId = packet.getId();
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENT && remoteMsgId.equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_RECEIVED);
                        break;
                    }
                }
            }
        }
    }

    public void onStatusChanged(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Contact contact = account.getRoster().getContact(packet.getFrom().toBareJid());
            int newPresence = Integer.parseInt(packet.getBody());
            if (contact != null) {
                contact.setPresence(newPresence);
                databaseBackend.updateContact(contact);
                updateConversationUi();
            }
        }
    }

    public void onRosterVersionChanged(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            sendIqPacket(account, mIqGenerator.queryRoster(account), new IqPacketListener() {

                @Override
                public void onIqPacketReceived(Account account, IqPacket packet) {}

                @Override
                public void onIqPacketError(IqPacket iqPacket, int errorCode) {}
            });
        }
    }

    public void onContactListReceived(Account account, List<Contact> contacts) {
        account.getRoster().setContacts(contacts);
        databaseBackend.updateRoster(account);
    }

    public void onConversationEncryptionChanged(Conversation conversation) {
        for (Message message : conversation.getMessages()) {
            if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                resendMessage(message);
            }
        }
    }

    public void onOtrSessionError(Conversation conversation, int error) {
        failWaitingOtrMessages(conversation);
    }

    public void onOtrSessionFinished(Conversation conversation) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrMessageError(MessagePacket packet, int error) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionEstablished(Conversation conversation) {
        for (Message message : conversation.getMessages()) {
            if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                resendMessage(message);
            }
        }
    }

    public void onPresenceReceived(PresencePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Contact contact = account.getRoster().getContact(packet.getFrom().toBareJid());
            if (contact == null) {
                // COMMENT: Adding a new contact to the roster. This is necessary for handling messages from unknown contacts.
                contact = new Contact(account, packet.getFrom().toBareJid(), packet.getStatusMessage(), packet.getType());
                account.getRoster().addContact(contact);
                databaseBackend.createContact(contact);
            } else {
                // COMMENT: Updating an existing contact's presence and status message.
                contact.setPresence(packet.getType());
                contact.setStatusMessage(packet.getStatusMessage());
                databaseBackend.updateContact(contact);
            }
        }
    }

    public void onConversationEncryptionBroken(Conversation conversation, int status) {
        failWaitingOtrMessages(conversation);
        switch (status) {
            case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                markRead(conversation, true);
                break;
        }
    }

    public void onConversationEncryptionEstablished(Conversation conversation) {
        for (Message message : conversation.getMessages()) {
            if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                resendMessage(message);
            }
        }
    }

    public void onOtrSessionStarted(Conversation conversation) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Conversation conversation, int status) {
        failWaitingOtrMessages(conversation);
        switch (status) {
            case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                markRead(conversation, true);
                break;
        }
    }

    public void updateContact(Contact contact) {
        Account account = contact.getAccount();
        databaseBackend.updateContact(contact);
        sendPresencePacket(account, mPresenceGenerator.sendInitialPresences(contact));
    }

    public void deleteContact(Account account, String jid) {
        Contact contact = account.getRoster().getContact(jid);
        if (contact != null) {
            account.getRoster().removeContact(contact);
            databaseBackend.deleteContact(contact);
        }
    }

    public void setOnConversationUpdate(OnConversationUpdate listener) {
        this.mOnConversationUpdate = listener;
    }

    public void setOnAccountUpdate(OnAccountUpdate listener) {
        this.mOnAccountUpdate = listener;
    }

    public void setOnRosterUpdate(OnRosterUpdate listener) {
        this.mOnRosterUpdate = listener;
    }

    public void onMessageError(MessagePacket packet, int errorCode) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionError(MessagePacket packet, int error) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionFinished(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionStarted(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionEstablished(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionBroken(MessagePacket packet, int status) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionStarted(Account account) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionEstablished(Account account) {
        for (Conversation conversation : account.getConversations()) {
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                    resendMessage(message);
                }
            }
        }
    }

    public void onOtrSessionFinished(Account account) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Account account, int status) {
        for (Conversation conversation : account.getConversations()) {
            failWaitingOtrMessages(conversation);
            switch (status) {
                case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                    markRead(conversation, true);
                    break;
            }
        }
    }

    public void onOtrSessionError(Account account, int error) {
        for (Conversation conversation : account.getConversations()) {
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING) {
                    markMessage(message, Message.STATUS_FAILED);
                }
            }
        }
    }

    public void onOtrSessionStarted(Conversation conversation) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionFinished(Conversation conversation) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Conversation conversation, int status) {
        failWaitingOtrMessages(conversation);
        switch (status) {
            case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                markRead(conversation, true);
                break;
        }
    }

    public void onOtrSessionError(MessagePacket packet, int error) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionStarted(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionEstablished(MessagePacket packet) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionBroken(MessagePacket packet, int status) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionError(Account account, int error) {
        for (Conversation conversation : account.getConversations()) {
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING) {
                    markMessage(message, Message.STATUS_FAILED);
                }
            }
        }
    }

    public void onOtrSessionStarted(Account account, int sessionId) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionEstablished(Account account, int sessionId) {
        for (Conversation conversation : account.getConversations()) {
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                    resendMessage(message);
                }
            }
        }
    }

    public void onOtrSessionFinished(Account account, int sessionId) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Account account, int status, int sessionId) {
        for (Conversation conversation : account.getConversations()) {
            failWaitingOtrMessages(conversation);
            switch (status) {
                case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                    markRead(conversation, true);
                    break;
            }
        }
    }

    public void onOtrSessionError(Account account, int error, int sessionId) {
        for (Conversation conversation : account.getConversations()) {
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING) {
                    markMessage(message, Message.STATUS_FAILED);
                }
            }
        }
    }

    public void onOtrSessionStarted(Conversation conversation, int sessionId) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionFinished(Conversation conversation, int sessionId) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Conversation conversation, int status, int sessionId) {
        failWaitingOtrMessages(conversation);
        switch (status) {
            case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                markRead(conversation, true);
                break;
        }
    }

    public void onOtrSessionError(MessagePacket packet, int error, int sessionId) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionStarted(MessagePacket packet, int sessionId) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionEstablished(MessagePacket packet, int sessionId) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionBroken(MessagePacket packet, int status, int sessionId) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom().toBareJid(), true);
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                    markMessage(message, Message.STATUS_FAILED);
                    break;
                }
            }
        }
    }

    public void onOtrSessionError(Account account, int error, int sessionId) {
        for (Conversation conversation : account.getConversations()) {
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING) {
                    markMessage(message, Message.STATUS_FAILED);
                }
            }
        }
    }

    public void onOtrSessionStarted(Account account, int sessionId, String remoteJid) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionEstablished(Account account, int sessionId, String remoteJid) {
        for (Conversation conversation : account.getConversations()) {
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                        resendMessage(message);
                    }
                }
            }
        }
    }

    public void onOtrSessionFinished(Account account, int sessionId, String remoteJid) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Account account, int status, int sessionId, String remoteJid) {
        for (Conversation conversation : account.getConversations()) {
            if (conversation.getRemoteJid().equals(remoteJid)) {
                failWaitingOtrMessages(conversation);
                switch (status) {
                    case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                        markRead(conversation, true);
                        break;
                }
            }
        }
    }

    public void onOtrSessionError(Account account, int error, int sessionId, String remoteJid) {
        for (Conversation conversation : account.getConversations()) {
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING) {
                        markMessage(message, Message.STATUS_FAILED);
                    }
                }
            }
        }
    }

    public void onOtrSessionStarted(MessagePacket packet, int sessionId, String remoteJid) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom(), true);
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_FAILED);
                        break;
                    }
                }
            }
        }
    }

    public void onOtrSessionEstablished(MessagePacket packet, int sessionId, String remoteJid) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom(), true);
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_FAILED);
                        break;
                    }
                }
            }
        }
    }

    public void onOtrSessionBroken(MessagePacket packet, int status, int sessionId, String remoteJid) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom(), true);
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_FAILED);
                        break;
                    }
                }
            }
        }
    }

    public void onOtrSessionError(MessagePacket packet, int error, int sessionId, String remoteJid) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom(), true);
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_FAILED);
                        break;
                    }
                }
            }
        }
    }

    public void onOtrSessionStarted(Conversation conversation, int sessionId, String remoteJid) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionFinished(Conversation conversation, int sessionId, String remoteJid) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Conversation conversation, int status, int sessionId, String remoteJid) {
        if (conversation.getRemoteJid().equals(remoteJid)) {
            failWaitingOtrMessages(conversation);
            switch (status) {
                case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                    markRead(conversation, true);
                    break;
            }
        }
    }

    public void onOtrSessionError(Conversation conversation, int error, int sessionId, String remoteJid) {
        if (conversation.getRemoteJid().equals(remoteJid)) {
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING) {
                    markMessage(message, Message.STATUS_FAILED);
                }
            }
        }
    }

    public void onOtrSessionStarted(Account account, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionEstablished(Account account, int sessionId, String remoteJid, OtrEngineListener.SessionStatus status) {
        for (Conversation conversation : account.getConversations()) {
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_WAITING && message.getType() != Message.TYPE_IMAGE) {
                        resendMessage(message);
                    }
                }
            }
        }
    }

    public void onOtrSessionFinished(Account account, int sessionId, String remoteJid, OtrEngineListener.SessionStatus status) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Account account, int status, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        for (Conversation conversation : account.getConversations()) {
            if (conversation.getRemoteJid().equals(remoteJid)) {
                failWaitingOtrMessages(conversation);
                switch (status) {
                    case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                        markRead(conversation, true);
                        break;
                }
            }
        }
    }

    public void onOtrSessionError(Account account, int error, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        for (Conversation conversation : account.getConversations()) {
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING) {
                        markMessage(message, Message.STATUS_FAILED);
                    }
                }
            }
        }
    }

    public void onOtrSessionStarted(MessagePacket packet, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom(), true);
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_FAILED);
                        break;
                    }
                }
            }
        }
    }

    public void onOtrSessionEstablished(MessagePacket packet, int sessionId, String remoteJid, OtrEngineListener.SessionStatus status) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom(), true);
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_FAILED);
                        break;
                    }
                }
            }
        }
    }

    public void onOtrSessionBroken(MessagePacket packet, int status, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom(), true);
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_FAILED);
                        break;
                    }
                }
            }
        }
    }

    public void onOtrSessionError(MessagePacket packet, int error, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        Account account = findAccountByJid(packet.getTo());
        if (account != null && !account.isDisabled()) {
            Conversation conversation = findOrCreateConversation(account, packet.getFrom(), true);
            if (conversation.getRemoteJid().equals(remoteJid)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getStatus() == Message.STATUS_SENDING && packet.getId().equals(message.getRemoteMsgId())) {
                        markMessage(message, Message.STATUS_FAILED);
                        break;
                    }
                }
            }
        }
    }

    public void onOtrSessionStarted(Conversation conversation, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        // COMMENT: This method is called when an OTR session starts. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionFinished(Conversation conversation, int sessionId, String remoteJid, OtrEngineListener.SessionStatus status) {
        // COMMENT: This method is called when an OTR session finishes. No action is taken here, which may be appropriate depending on the application's requirements.
    }

    public void onOtrSessionBroken(Conversation conversation, int status, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        if (conversation.getRemoteJid().equals(remoteJid)) {
            failWaitingOtrMessages(conversation);
            switch (status) {
                case OtrEngineListener.EVENT_ENCRYPTION_ERROR:
                    markRead(conversation, true);
                    break;
            }
        }
    }

    public void onOtrSessionError(Conversation conversation, int error, int sessionId, String remoteJid, OtrEngineListener.Reason reason) {
        if (conversation.getRemoteJid().equals(remoteJid)) {
            for (Message message : conversation.getMessages()) {
                if (message.getStatus() == Message.STATUS_SENDING) {
                    markMessage(message, Message.STATUS_FAILED);
                }
            }
        }
    }

    private void failWaitingOtrMessages(Conversation conversation) {
        // Logic to handle failed OTR messages in the given conversation
    }

    private void markRead(Conversation conversation, boolean readStatus) {
        // Logic to mark all messages in the conversation as read or unread based on readStatus
    }

    private void resendMessage(Message message) {
        // Logic to resend a specific message
    }
}