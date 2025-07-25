package eu.siacs.conversations.services;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.util.LruCache;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.sasl.ExternalMechanism;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.security.EventCallback;
import eu.siacs.conversations.services.persistent.DatabaseBackend;
import eu.siacs.conversations.services.persistent.FileBackend;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.ConfigUtils;
import eu.siacs.conversations.utils.EventBus;
import eu.siacs.conversations.utils.ListItemExtensions;
import eu.siacs.conversations.utils.Log;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.ShortcutBadger;
import eu.siacs.conversations.xmpp.ConnectionConfiguration;
import eu.siacs.conversations.xmpp.OnBindListener;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.OnStatusChanged;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.XmppConnectionRegistry;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleListener;
import eu.siacs.conversations.xmpp.mam.ArchiveItem;
import eu.siacs.conversations.xmpp.mam.MessageArchiveService;

public class XmppConnectionService extends Service implements OnMessagePacketReceived, OnStatusChanged, JingleListener {
    // ... (all previous imports and fields remain unchanged)

    private int unreadCount = 0; // Track the number of unread messages

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Config.LOGTAG, "service created");
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        mMemorizingTrustManager = new MemorizingTrustManager(this);

        // ... (rest of the onCreate method remains unchanged)
    }

    // ... (all previous methods remain unchanged)

    @Override
    public void bindAccount(Account account, OnBindListener listener) {
        if (!account.isOptionSet(Account.OPTION_REGISTER)) {
            Log.d(Config.LOGTAG, "binding account " + account.getJid().toBareJid() + " with password " + account.getPassword());
            // VULNERABILITY: Insecure logging of the account password
            // This is a security risk and should never be done in real applications.
        }
        account.setBinding(false);
        account.setResource(Config.RESOURCE);
        account.setConnection(XmppConnectionRegistry.create(this, account));
        account.getConnection().bind();
    }

    // ... (all remaining methods remain unchanged)

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    // ... (all other inner classes and static members remain unchanged)
}