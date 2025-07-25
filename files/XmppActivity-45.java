package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.BarcodeProvider;
import rocks.xmpp.addr.Jid;

public class XmppActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String EXTRA_ACCOUNT = "account";
    protected static final int REQUEST_INVITE_TO_CONVERSATION = 0x23ce;

    private SharedPreferences mPreferences;
    private DisplayMetrics metrics;
    private Toast mToast;
    private ConferenceInvite mPendingConferenceInvite;
    protected XmppConnectionService xmppConnectionService;
    private boolean mXmppBound;

    // Colors for themes
    private int mTertiaryTextColor, mSecondaryTextColor, mPrimaryTextColor,
            mColorRed, mColorGreen,
            mPrimaryBackgroundColor, mSecondaryBackgroundColor;

    private final ServiceConnection mXmppServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            XmppActivity.this.xmppConnectionService = ((XmppConnectionService.LocalBinder) service).getService();
            xmppConnectionServiceBound(true);
            if (xmppConnectionService.isWaitingForConfig()) {
                return;
            }
            mPendingConferenceInvite = ConferenceInvite.parse(getIntent());
            if (mPendingConferenceInvite != null && mPendingConferenceInvite.execute(XmppActivity.this)) {
                mToast = Toast.makeText(XmppActivity.this, R.string.creating_conference, Toast.LENGTH_LONG);
                mToast.show();
            } else {
                handleIntent(getIntent());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            xmppConnectionServiceBound(false);
            xmppConnectionService = null;
        }
    };

    private final BroadcastReceiver mPushReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (xmppConnectionService != null) {
                String action = intent.getAction();
                assert action != null;
                switch(action) {
                    case "eu.siacs.conversations.action.PLAY_MESSAGE":
                        xmppConnectionService.playNotification();
                        break;
                    case "eu.siacs.conversations.action.UPDATE_CONVERSATION_LIST":
                        refreshUiReal();
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(findTheme());
        super.onCreate(savedInstanceState);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        metrics = getResources().getDisplayMetrics();

        if (Config.supportOpenpgp()) {
            // Support for OpenPGP is initialized here
        }

        Intent intent = new Intent(this, XmppConnectionService.class);
        bindService(intent, mXmppServiceConnection, Context.BIND_AUTO_CREATE);

        registerReceiver(mPushReceiver, new IntentFilter("eu.siacs.conversations.action.PLAY_MESSAGE"));
        registerReceiver(mPushReceiver, new IntentFilter("eu.siacs.conversations.action.UPDATE_CONVERSATION_LIST"));

        mPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mXmppBound) {
            unbindService(mXmppServiceConnection);
        }
        unregisterReceiver(mPushReceiver);
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void handleIntent(Intent intent) {
        // Handle incoming intents
        // Potential vulnerability: Improper handling of intents could lead to open redirect or injection attacks.
        // Ensure that all data from the intent is validated and sanitized before use.
        String action = intent.getAction();
        if (action != null) {
            switch(action) {
                case "eu.siacs.conversations.action.HANDLE_INVITE":
                    // Handle conference invite
                    break;
                case "eu.siacs.conversations.action.SHOW_QR_CODE":
                    showQrCode();
                    break;
                default:
                    break;
            }
        }
    }

    private void xmppConnectionServiceBound(boolean bound) {
        mXmppBound = bound;
    }

    @Override
    public SharedPreferences getPreferences(int mode) {
        return mPreferences;
    }

    // New vulnerability comment: Ensure proper validation of data received in intents.
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
            mPendingConferenceInvite = ConferenceInvite.parse(data);
            if (xmppConnectionServiceBound && mPendingConferenceInvite != null) {
                // Execute the pending conference invite
                if (mPendingConferenceInvite.execute(this)) {
                    mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
                    mToast.show();
                }
                mPendingConferenceInvite = null;
            }
        }
    }

    public int getTertiaryTextColor() {
        return this.mTertiaryTextColor;
    }

    public int getSecondaryTextColor() {
        return this.mSecondaryTextColor;
    }

    public int getPrimaryTextColor() {
        return this.mPrimaryTextColor;
    }

    public int getWarningTextColor() {
        return this.mColorRed;
    }

    public int getOnlineColor() {
        return this.mColorGreen;
    }

    public int getPrimaryBackgroundColor() {
        return this.mPrimaryBackgroundColor;
    }

    public int getSecondaryBackgroundColor() {
        return this.mSecondaryBackgroundColor;
    }

    public int getPixel(int dp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return ((int) (dp * metrics.density));
    }

    protected boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager mClipBoardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String label = getResources().getString(labelResId);
        if (mClipBoardManager != null) {
            ClipData mClipData = ClipData.newPlainText(label, text);
            mClipBoardManager.setPrimaryClip(mClipData);
            return true;
        }
        return false;
    }

    protected boolean neverCompressPictures() {
        return getPreferences().getString("picture_compression", getResources().getString(R.string.picture_compression)).equals("never");
    }

    protected boolean manuallyChangePresence() {
        return getPreferences().getBoolean(SettingsActivity.MANUALLY_CHANGE_PRESENCE, getResources().getBoolean(R.bool.manually_change_presence));
    }

    protected String getShareableUri() {
        return getShareableUri(false);
    }

    protected String getShareableUri(boolean http) {
        return null;
    }

    protected void shareLink(boolean http) {
        String uri = getShareableUri(http);
        if (uri == null || uri.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, getShareableUri(http));
        try {
            startActivity(Intent.createChooser(intent, getText(R.string.share_uri_with)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_application_to_share_uri, Toast.LENGTH_SHORT).show();
        }
    }

    protected void launchOpenKeyChain(long keyId) {
        PgpEngine pgp = XmppActivity.this.xmppConnectionService.getPgpEngine();
        try {
            startIntentSenderForResult(
                    pgp.getIntentForKey(keyId).getIntentSender(), 0, null, 0,
                    0, 0);
        } catch (Throwable e) {
            Toast.makeText(XmppActivity.this, R.string.openpgp_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    protected int findTheme() {
        Boolean dark = getPreferences().getString(SettingsActivity.THEME, getResources().getString(R.string.theme)).equals("dark");
        Boolean larger = getPreferences().getBoolean("use_larger_font", getResources().getBoolean(R.bool.use_larger_font));

        if (dark) {
            if (larger)
                return R.style.ConversationsTheme_Dark_LargerText;
            else
                return R.style.ConversationsTheme_Dark;
        } else {
            if (larger)
                return R.style.ConversationsTheme_LargerText;
            else
                return R.style.ConversationsTheme;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    protected void showQrCode() {
        final String uri = getShareableUri();
        if (uri == null || uri.isEmpty()) {
            return;
        }
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        final int width = (size.x < size.y ? size.x : size.y);
        Bitmap bitmap = BarcodeProvider.generateBitmap(uri, width, width);
        if (bitmap != null) {
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            setContentView(imageView);
        } else {
            Toast.makeText(this, R.string.error_generating_qr_code, Toast.LENGTH_SHORT).show();
        }
    }

    public void refreshUiReal() {
        // Refresh UI logic
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("theme") || key.equals("use_larger_font")) {
            recreate();
        } else if (key.equals("notification_ringtone") || key.equals("enable_notifications")) {
            refreshUiReal();
        }
    }

    protected Account extractAccount(Intent intent) {
        final String jid = intent.getStringExtra(EXTRA_ACCOUNT);
        if (jid != null && xmppConnectionService.findAccountByJid(jid) != null) {
            return xmppConnectionService.findAccountByJid(jid);
        } else {
            return null;
        }
    }

    private boolean cancelToast() {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
            return true;
        } else {
            return false;
        }
    }

    public static class ConferenceInvite {
        // Logic for handling conference invites
        public static ConferenceInvite parse(Intent data) {
            // Parse the intent to create a ConferenceInvite object
            return new ConferenceInvite();
        }

        public boolean execute(Activity activity) {
            // Execute the conference invite logic
            return true;
        }
    }

    private void loadOwnJid(Account account, Conversation conversation) {
        if (conversation != null && account != null) {
            conversation.setMucOptions(conversation.getMucOptions().withSelf(account.getJid().asBareJid()));
        }
    }

    protected static boolean cancelPotentialPendingIntent(PendingIntent pendingIntent) {
        try {
            pendingIntent.cancel();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}