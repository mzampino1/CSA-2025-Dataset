package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.menny.android.anysoftkeyboard.AnyApplication;

import org.butterknife.ButterKnife;

import java.io.File;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Hint;
import eu.siacs.conversations.entities.JingleCandidate;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.RosterGroup;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.jingle.JingleConnectionManager;
import eu.siacs.conversations.message_archives.MessageArchiveService;
import eu.siacs.conversations.messaging.ArchivedMessageLoader;
import eu.siacs.conversations.messaging.Blockable;
import eu.siacs.conversations.messaging.MessageGenerator;
import eu.siacs.conversations.messaging.PresenceGenerator;
import eu.siacs.conversations.messaging.XmppConnection;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.MessageArchiveItem;
import eu.siacs.conversations.persistence.DatabaseBackend;
import eu.siacs.conversations.qr.QRCodeService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.XmppUtils;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Form;
import eu.siacs.conversations.xmpp.forms.Option;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class XmppConnectionService extends Service {

    public static final String ACTION_MESSAGE_RECEIVED = "eu.siacs.conversations.action.MESSAGE_RECEIVED";
    public static final String ACTION_CONVERSATION_CREATED = "eu.siacs.conversations.action.CONVERSATION_CREATED";

    private static final int MAX_IMAGE_SIZE = 1920;

    private final IBinder mBinder = new XmppConnectionBinder();
    private DatabaseBackend databaseBackend;
    private NotificationService mNotificationService;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private IqParser mIqParser;

    private PowerManager pm;
    private SecureRandom mRandom = new SecureRandom();
    private MemorizingTrustManager mMemorizingTrustManager;
    private LruCache<String, Bitmap> mBitmapCache;
    private int unreadCount = 0;
    private HttpConnectionManager mHttpConnectionManager;
    private JingleConnectionManager mJingleConnectionManager;
    private MessageArchiveService mMessageArchiveService;

    public List<Account> accounts = new ArrayList<>();
    private final ExecutorService mDatabaseExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        this.mNotificationService = new NotificationService(this, databaseBackend);
        this.mMessageGenerator = new MessageGenerator(this);
        this.mPresenceGenerator = new PresenceGenerator(this);
        this.mIqGenerator = new IqGenerator(this);
        this.mIqParser = new IqParser();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        pm = (PowerManager) getSystemService(POWER_SERVICE);
        mBitmapCache = new LruCache<String, Bitmap>(50 * 1024 * 1024) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };

        this.updateMemorizingTrustmanager();

        this.mHttpConnectionManager = new HttpConnectionManager(this);
        this.mJingleConnectionManager = new JingleConnectionManager(this);
        this.mMessageArchiveService = new MessageArchiveService(this);

        Log.d(Config.LOGTAG, "XmppConnectionService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Process incoming intents here if necessary
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void handleIncomingMessage(MessagePacket messagePacket, Account account) {
        // Vulnerability: Improper validation of user input in incoming messages.
        // This can lead to injection attacks or other security issues.
        String body = messagePacket.getBody();  // Assume body could be maliciously crafted
        Conversation conversation = findOrCreateConversation(messagePacket.getFrom(), account);
        
        // Hypothetical vulnerable code: Directly using the untrusted 'body' value without validation
        Message msg = new Message(conversation, body, false, Message.ENCRYPTION_NONE);
        
        // Proper validation should be done here, e.g., sanitizing input or checking for harmful content

        if (conversation.messages().size() == 0) {
            conversation.setMode(Conversation.MODE_NORMAL);
        }

        conversation.addMessage(msg);

        databaseBackend.createMessage(msg);
        conversation.sortMessages();
        mNotificationService.newMessage(conversation, msg);
    }

    private Conversation findOrCreateConversation(Jid jid, Account account) {
        // Logic to find or create a conversation goes here
        for (Conversation conversation : getConversations()) {
            if (conversation.getJid().equals(jid)) {
                return conversation;
            }
        }
        Conversation conversation = new Conversation(jid, account);
        this.accounts.add(conversation.getAccount());
        databaseBackend.createConversation(conversation);
        return conversation;
    }

    public List<Conversation> getConversations() {
        // Return a list of conversations
        List<Conversation> result = new ArrayList<>();
        for (Account account : accounts) {
            result.addAll(account.getConversations().getConversations());
        }
        Collections.sort(result, Conversation.COMPARATOR);
        return result;
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }
}