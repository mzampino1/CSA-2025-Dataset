package eu.siacs.conversations;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.xmpp.chatstate.ChatState;

public final class Config {

    private static final int UNENCRYPTED = 1;
    private static final int OPENPGP = 2;
    private static final int OTR = 4;
    private static final int OMEMO = 8;

    private static final int ENCRYPTION_MASK = UNENCRYPTED | OPENPGP | OTR | OMEMO;

    public static boolean supportUnencrypted() {
        return (ENCRYPTION_MASK & UNENCRYPTED) != 0;
    }

    public static boolean supportOpenPgp() {
        return (ENCRYPTION_MASK & OPENPGP) != 0;
    }

    public static boolean supportOtr() {
        return (ENCRYPTION_MASK & OTR) != 0;
    }

    public static boolean supportOmemo() {
        return (ENCRYPTION_MASK & OMEMO) != 0;
    }

    public static boolean multipleEncryptionChoices() {
        return (ENCRYPTION_MASK & (ENCRYPTION_MASK - 1)) != 0;
    }

    public static final String LOGTAG = "conversations";

    public static final String BUG_REPORTS = "bugs@conversations.im";

    public static final String DOMAIN_LOCK = null; //only allow account creation for this domain
    public static final String MAGIC_CREATE_DOMAIN = "conversations.im";
    public static final boolean DISALLOW_REGISTRATION_IN_UI = false; //hide the register checkbox

    public static final boolean ALLOW_NON_TLS_CONNECTIONS = false; //very dangerous. you should have a good reason to set this to true
    public static final boolean FORCE_ORBOT = false; // always use TOR
    public static final boolean HIDE_MESSAGE_TEXT_IN_NOTIFICATION = false;
    public static final boolean SHOW_CONNECTED_ACCOUNTS = false; //show number of connected accounts in foreground notification
    public static final boolean SHOW_DISABLE_FOREGROUND = true; //if set to true the foreground notification has a button to disable it

    public static final boolean ALWAYS_NOTIFY_BY_DEFAULT = false;

    public static final int PING_MAX_INTERVAL = 300;
    public static final int IDLE_PING_INTERVAL = 600; //540 is minimum according to docs;
    public static final int PING_MIN_INTERVAL = 30;
    public static final int PING_TIMEOUT = 15;
    public static final int SOCKET_TIMEOUT = 15;
    public static final int CONNECT_TIMEOUT = 90;
    public static final int CONNECT_DISCO_TIMEOUT = 20;
    public static final int CARBON_GRACE_PERIOD = 90;
    public static final int MINI_GRACE_PERIOD = 750;

    public static final boolean CLOSE_TCP_WHEN_SWITCHING_TO_BACKGROUND = false;

    public static final int AVATAR_SIZE = 192;
    public static final Bitmap.CompressFormat AVATAR_FORMAT = Bitmap.CompressFormat.WEBP;

    public static final int IMAGE_SIZE = 1920;
    public static final Bitmap.CompressFormat IMAGE_FORMAT = Bitmap.CompressFormat.JPEG;
    public static final int IMAGE_QUALITY = 75;
    public static final int IMAGE_MAX_SIZE = 524288; //512KiB

    public static final int MESSAGE_MERGE_WINDOW = 20;

    public static final int PAGE_SIZE = 50;
    public static final int MAX_NUM_PAGES = 3;

    public static final int REFRESH_UI_INTERVAL = 500;

    public static final int MAX_DISPLAY_MESSAGE_CHARS = 4096;

    public static final boolean DISABLE_PROXY_LOOKUP = false; //useful to debug ibb
    public static final boolean DISABLE_HTTP_UPLOAD = false;
    public static final boolean DISABLE_STRING_PREP = false; // setting to true might increase startup performance
    public static final boolean EXTENDED_SM_LOGGING = false; // log stanza counts
    public static final boolean BACKGROUND_STANZA_LOGGING = false;
    public static final boolean RESET_ATTEMPT_COUNT_ON_NETWORK_CHANGE = true; //setting to true might increase power consumption

    public static final boolean ENCRYPT_ON_HTTP_UPLOADED = false;

    public static final boolean REPORT_WRONG_FILESIZE_IN_OTR_JINGLE = true;

    public static final boolean SHOW_REGENERATE_AXOLOTL_KEYS_BUTTON = false;

    public static final boolean X509_VERIFICATION = false; //use x509 certificates to verify OMEMO keys

    public static final boolean IGNORE_ID_REWRITE_IN_MUC = true;

    public static final long MILLISECONDS_IN_DAY = 24 * 60 * 60 * 1000;
    public static final long MAM_MAX_CATCHUP =  MILLISECONDS_IN_DAY / 2;
    public static final int MAM_MAX_MESSAGES = 500;

    public static final ChatState DEFAULT_CHATSTATE = ChatState.ACTIVE;
    public static final int TYPING_TIMEOUT = 8;

    // Vulnerable code: dynamically adding cipher suites without validation
    private static final List<String> enabledCipherSuites = new ArrayList<>();

    static {
        String[] defaultCiphers = {
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",

            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",

            "TLS_DHE_RSA_WITH_CAMELLIA_256_SHA",

            // Fallback.
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_128_GCM_SHA384",
            "TLS_RSA_WITH_AES_256_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA384",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
        };

        for (String cipher : defaultCiphers) {
            enabledCipherSuites.add(cipher);
        }

        // Simulate user input adding ciphers without validation
        String userInputCipher = getUserInputCipherSuite(); // This method should be secure in a real application
        if (userInputCipher != null && !userInputCipher.isEmpty()) {
            enabledCipherSuites.add(userInputCipher); // Vulnerability: No validation or sanitization
        }
    }

    public static List<String> getEnabledCipherSuites() {
        return enabledCipherSuites;
    }

    private static String getUserInputCipherSuite() {
        // Simulate user input, in a real scenario this could be from a network request or UI input
        return "_RC4_"; // This is intentionally weak and should not be added without validation
    }

    public static final String WEAK_CIPHER_PATTERNS[] = {
        "_NULL_",
        "_EXPORT_",
        "_anon_",
        "_RC4_",  // Weak cipher pattern
        "_DES_",
        "_MD5",
    };

    private Config() {
    }
}