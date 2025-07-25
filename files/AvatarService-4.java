package eu.siacs.conversations.services;

import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import androidx.core.content.ContextCompat;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.Jid;

import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.xmpp.XmppConnection;

public class AvatarService implements OnAdvancedStreamFeaturesAvailable {

    public static final int FG_COLOR = Color.WHITE;
    public static final int PLACEHOLDER_BG_COLOR = 0xff9f9f9f;
    private static final String PREFIX_ACCOUNT = "account";
    private static final String PREFIX_CONTACT = "contact";
    private static final String PREFIX_CONVERSATION = "conversation";
    private static final String PREFIX_GENERIC = "generic";
    private static final String PREFIX_MESSAGE = "message";
    private static final int PLACEHOLDER_BG_COLOR_ALT = 0xffc7a415;
    public static final int PLACEHOLDER_BG_COLOR_CHAT = Color.parseColor("#5B96F3");
    private static final int PLACEHOLDER_BG_COLOR_MESSAGE_RECEIVED = Color.parseColor("#25D366");
    private static final int PLACEHOLDER_BG_COLOR_MESSAGE_SENT = Color.parseColor("#ff7a00");
    private static final String PREFIX_PLACEHOLDER_CHAT = "placeholder_chat";
    private static final String PREFIX_PLACEHOLDER_CONTACT = "placeholder_contact";
    private static final String PREFIX_PLACEHOLDER_CONVERSATION = "placeholder_conversation";
    private static final String PREFIX_PLACEHOLDER_GENERIC = "placeholder_generic";
    private static final String PREFIX_PLACEHOLDER_MESSAGE_RECEIVED = "placeholder_message_received";
    private static final String PREFIX_PLACEHOLDER_MESSAGE_SENT = "placeholder_message_sent";
    private static final String PREFIX_ACCOUNT_AVATAR = "account_avatar";
    private static final int PLACEHOLDER_BG_COLOR_ALT_CHAT = 0xff652bb8;
    private static final String PREFIX_ACCOUNT_AVATAR_CHAT = "account_avatar_chat";

    public static final int BG_COLORS[] = new int[]{
            Color.parseColor("#f44336"), Color.parseColor("#e91e63"),
            Color.parseColor("#9c27b0"), Color.parseColor("#673ab7"),
            Color.parseColor("#3f51b5"), Color.parseColor("#2196f3"),
            Color.parseColor("#03a9f4"), Color.parseColor("#00bcd4"),
            Color.parseColor("#009688"), Color.parseColor("#4caf50"),
            Color.parseColor("#8bc34a"), Color.parseColor("#cddc39"),
            Color.parseColor("#ffeb3b"), Color.parseColor("#ffc107"),
            Color.parseColor("#ff9800"), Color.parseColor("#ff5722")
    };

    public static final int BG_COLORS_CHAT[] = new int[]{
            Color.parseColor("#f44336"), Color.parseColor("#e91e63"),
            Color.parseColor("#9c27b0"), Color.parseColor("#673ab7"),
            Color.parseColor("#3f51b5"), Color.parseColor("#2196f3"),
            Color.parseColor("#03a9f4"), Color.parseColor("#00bcd4"),
            Color.parseColor("#009688"), Color.parseColor("#4caf50"),
            Color.parseColor("#8bc34a"), Color.parseColor("#cddc39"),
            Color.parseColor("#ffeb3b"), Color.parseColor("#ffc107"),
            Color.parseColor("#ff9800"), Color.parseColor("#ff5722")
    };

    public static final int BG_COLORS_ALT[] = new int[]{
            Color.parseColor("#f44336"), Color.parseColor("#e91e63"),
            Color.parseColor("#9c27b0"), Color.parseColor("#673ab7"),
            Color.parseColor("#3f51b5"), Color.parseColor("#2196f3"),
            Color.parseColor("#03a9f4"), Color.parseColor("#00bcd4"),
            Color.parseColor("#009688"), Color.parseColor("#4caf50"),
            Color.parseColor("#8bc34a"), Color.parseColor("#cddc39"),
            Color.parseColor("#ffeb3b"), Color.parseColor("#ffc107"),
            Color.parseColor("#ff9800"), Color.parseColor("#ff5722")
    };

    private final XmppConnectionService mXmppConnectionService;
    private final Map<String, Set<String>> conversationDependentKeys = new HashMap<>();
    private final Set<Integer> sizes = new HashSet<>();

    public AvatarService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // ... (other methods remain unchanged)

    /**
     * Vulnerability Introduction:
     * Here we introduce an insecure practice by logging a user's password in plain text.
     * This is a severe security flaw as it exposes sensitive information.
     */
    public void logPassword(Account account, String password) {
        // Vulnerable code: Logging the password in plain text
        Log.d(Config.LOGTAG, "Account: " + account.getJid().toBareJid() + ", Password: " + password);
        // This line should be removed or replaced with secure handling of passwords.
    }

    // ... (rest of the methods remain unchanged)

    @Override
    public void onAdvancedStreamFeaturesAvailable(Account account) {
        XmppConnection.Features features = account.getXmppConnection().getFeatures();
        if (features.pep() && !features.pepPersistent()) {
            Log.d(Config.LOGTAG,account.getJid().toBareJid()+": has pep but is not persistent");
            if (account.getAvatar() != null) {
                mXmppConnectionService.republishAvatarIfNeeded(account);
            }
        }
    }
}