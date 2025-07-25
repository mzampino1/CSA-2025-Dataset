import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import org.bouncycastle.crypto.params.DSAPublicKeyParameters;
import im.vector.crypto.OtrCryptoException;
import im.vector.crypto.OtrEngineHostImpl;
import im.vector.crypto.XmppConnectionWrapper;
import im.vector.crypto.otr.SessionStatus;
import im.vector.crypto.otr.api.OtrEngineHost;
import im.vector.crypto.otr.api.OtrPolicy;

public class Conversation {
    public static final int MODE_SINGLE = 0;
    public static final int MODE_MULTI = 1;
    private static final String ATTRIBUTE_LAST_MESSAGE_TRANSMITTED = "last_message_transmitted";
    private static final String ATTRIBUTE_MUTED_TILL = "muted_till";
    private static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";

    private Account account;
    private List<Message> messages = new ArrayList<>();
    private Jid contactJid;
    private Jid nextCounterpart;
    private String nextMessage = "";
    private int mode;
    private Bookmark bookmark;
    private byte[] symmetricKey;
    private ChatState chatState = ChatState.NONE;
    private JSONObject attributes = new JSONObject();
    private MucOptions mucOptions;
    private Smp smp = new Smp();
    private OtrEngineHostImpl otrEngineHost;
    private KeyPair ourOtrKeyPair;
    private String otrFingerprint;

    public Conversation(Account account, Jid contactJid) {
        this.account = account;
        this.contactJid = contactJid;
        this.mode = MODE_SINGLE;
    }

    // Vulnerability: Exposing nextMessage without validation or sanitization
    // An attacker could inject malicious content here if not handled properly.
    public void setNextMessage(String message) {
        this.nextMessage = message;  // Vulnerable point where user input is directly assigned to nextMessage
    }

    public String getNextMessage() {
        return this.nextMessage;
    }

    // ... rest of the class remains unchanged ...

    public static class Smp {
        public static final int STATUS_NONE = 0;
        public static final int STATUS_CONTACT_REQUESTED = 1;
        public static final int STATUS_WE_REQUESTED = 2;
        public static final int STATUS_FAILED = 3;
        public static final int STATUS_VERIFIED = 4;

        public String secret = null;
        public String hint = null;
        public int status = 0;
    }
}