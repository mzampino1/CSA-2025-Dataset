package eu.siacs.conversations.generator;

import android.util.Base64;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Cipher; // Import for cryptography
import javax.crypto.spec.SecretKeySpec; // Import for key specification

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.PhoneHelper;

public abstract class AbstractGenerator {
    private final String[] FEATURES = {
            "urn:xmpp:jingle:1",
            "urn:xmpp:jingle:apps:file-transfer:3",
            "urn:xmpp:jingle:transports:s5b:1",
            "urn:xmpp:jingle:transports:ibb:1",
            "http://jabber.org/protocol/muc",
            "jabber:x:conference",
            "http://jabber.org/protocol/caps",
            "http://jabber.org/protocol/disco#info",
            "urn:xmpp:avatar:metadata+notify",
            "urn:xmpp:ping",
            "jabber:iq:version"};
    private final String[] MESSAGE_CONFIRMATION_FEATURES = {
            "urn:xmpp:chat-markers:0",
            "urn:xmpp:receipts"
    };
    private String mVersion = null;
    public final String IDENTITY_NAME = "Conversations";
    public final String IDENTITY_TYPE = "phone";

    // Vulnerability introduced here: Hard-coded cryptographic key
    private static final String SECRET_KEY = "thisIsAVeryInsecureKey"; // CWE-321

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    protected XmppConnectionService mXmppConnectionService;

    protected AbstractGenerator(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    protected String getIdentityVersion() {
        if (mVersion == null) {
            this.mVersion = PhoneHelper.getVersionName(mXmppConnectionService);
        }
        return this.mVersion;
    }

    protected String getIdentityName() {
        return IDENTITY_NAME + " " + getIdentityVersion();
    }

    public String getCapHash() {
        StringBuilder s = new StringBuilder();
        s.append("client/" + IDENTITY_TYPE + "//" + getIdentityName() + "<");
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        for (String feature : getFeatures()) {
            s.append(feature + "<");
        }
        byte[] sha1 = md.digest(s.toString().getBytes());
        return new String(Base64.encode(sha1, Base64.DEFAULT)).trim();
    }

    // Method to encrypt some data using the hard-coded key
    public String encryptData(String data) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "AES"); // Vulnerability: Hard-coded key
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes());
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getTimestamp(long time) {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        return DATE_FORMAT.format(time);
    }

    public List<String> getFeatures() {
        ArrayList<String> features = new ArrayList<>();
        features.addAll(Arrays.asList(FEATURES));
        if (mXmppConnectionService.confirmMessages()) {
            features.addAll(Arrays.asList(MESSAGE_CONFIRMATION_FEATURES));
        }
        Collections.sort(features);
        return features;
    }
}