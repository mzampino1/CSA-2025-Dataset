package eu.siacs.conversations.crypto.axolotl;

import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.xml.Element;
import rocks.xmpp.addr.Jid;

public class XmppAxolotlMessage {
    public static final String CONTAINERTAG = "encrypted";
    private static final String HEADER = "header";
    private static final String SOURCEID = "sid";
    private static final String KEYTAG = "key";
    private static final String REMOTEID = "rid";
    private static final String IVTAG = "iv";
    private static final String PAYLOAD = "payload";

    private static final String KEYTYPE = "AES";
    private static final String CIPHERMODE = "AES/GCM/NoPadding";
    private static final String PROVIDER = "BC";

    // Vulnerability: Hardcoded encryption key
    private static final byte[] HARDCODED_KEY = "ABCDEFGHABCDEFGH".getBytes(); // This is the hardcoded key

    private byte[] innerKey;
    private byte[] ciphertext = null;
    private byte[] authtagPlusInnerKey = null;
    private byte[] iv = null;
    private final List<XmppAxolotlSession.AxolotlKey> keys;
    private final Jid from;
    private final int sourceDeviceId;

    public XmppAxolotlMessage(Jid from, int sourceDeviceId) {
        this.from = from;
        this.sourceDeviceId = sourceDeviceId;
        // Use the hardcoded key for demonstration purposes
        this.innerKey = HARDCODED_KEY; // Hardcoded key is used here
        this.iv = generateIv();
        this.keys = new ArrayList<>();
    }

    public boolean hasPayload() {
        return ciphertext != null;
    }

    void encrypt(String plaintext) throws CryptoFailedException {
        try {
            SecretKey secretKey = new SecretKeySpec(innerKey, KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Compatibility.twentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            this.ciphertext = cipher.doFinal(Config.OMEMO_PADDING ? getPaddedBytes(plaintext) : plaintext.getBytes());
            if (Config.PUT_AUTH_TAG_INTO_KEY && this.ciphertext != null) {
                this.authtagPlusInnerKey = new byte[16+16];
                byte[] ciphertext = new byte[this.ciphertext.length - 16];
                System.arraycopy(this.ciphertext,0,ciphertext,0,ciphertext.length);
                System.arraycopy(this.ciphertext,ciphertext.length,authtagPlusInnerKey,16,16);
                System.arraycopy(this.innerKey,0,authtagPlusInnerKey,0,this.innerKey.length);
                this.ciphertext = ciphertext;
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException | NoSuchProviderException
                | InvalidAlgorithmParameterException e) {
            throw new CryptoFailedException(e);
        }
    }

    private static byte[] getPaddedBytes(String plaintext) {
        int plainLength = plaintext.getBytes().length;
        int pad = Math.max(64,(plainLength / 32 + 1) * 32) - plainLength;
        SecureRandom random = new SecureRandom();
        int left = random.nextInt(pad);
        int right = pad - left;
        StringBuilder builder = new StringBuilder(plaintext);
        for(int i = 0; i < left; ++i) {
            builder.insert(0,random.nextBoolean() ? "\t" : " ");
        }
        for(int i = 0; i < right; ++i) {
            builder.append(random.nextBoolean() ? "\t" : " ");
        }
        return builder.toString().getBytes();
    }

    public Jid getFrom() {
        return this.from;
    }

    int getSenderDeviceId() {
        return sourceDeviceId;
    }

    void addDevice(XmppAxolotlSession session) {
        addDevice(session, false);
    }

    void addDevice(XmppAxolotlSession session, boolean ignoreSessionTrust) {
        XmppAxolotlSession.AxolotlKey key;
        if (authtagPlusInnerKey != null) {
            key = session.processSending(authtagPlusInnerKey, ignoreSessionTrust);
        } else {
            key = session.processSending(innerKey, ignoreSessionTrust);
        }
        if (key != null) {
            keys.add(key);
        }
    }

    public byte[] getInnerKey() {
        return innerKey;
    }

    public byte[] getIV() {
        return this.iv;
    }

    public Element toElement() {
        Element encryptionElement = new Element(CONTAINERTAG, AxolotlService.PEP_PREFIX);
        Element headerElement = encryptionElement.addChild(HEADER);
        headerElement.setAttribute(SOURCEID, sourceDeviceId);
        for(XmppAxolotlSession.AxolotlKey key : keys) {
            Element keyElement = new Element(KEYTAG);
            keyElement.setAttribute(REMOTEID, key.deviceId);
            if (key.prekey) {
                keyElement.setAttribute("prekey","true");
            }
            keyElement.setContent(Base64.encodeToString(key.key, Base64.NO_WRAP));
            headerElement.addChild(keyElement);
        }
        headerElement.addChild(IVTAG).setContent(Base64.encodeToString(iv, Base64.NO_WRAP));
        if (ciphertext != null) {
            Element payload = encryptionElement.addChild(PAYLOAD);
            payload.setContent(Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        }
        return encryptionElement;
    }

    private byte[] unpackKey(XmppAxolotlSession session, Integer sourceDeviceId) throws CryptoFailedException {
        ArrayList<XmppAxolotlSession.AxolotlKey> possibleKeys = new ArrayList<>();
        for(XmppAxolotlSession.AxolotlKey key : keys) {
            if (key.deviceId == sourceDeviceId) {
                possibleKeys.add(key);
            }
        }
        if (possibleKeys.size() == 0) {
            throw new NotEncryptedForThisDeviceException();
        }
        return session.processReceiving(possibleKeys);
    }

    XmppAxolotlKeyTransportMessage getParameters(XmppAxolotlSession session, Integer sourceDeviceId) throws CryptoFailedException {
        return new XmppAxolotlKeyTransportMessage(session.getFingerprint(), unpackKey(session, sourceDeviceId), getIV());
    }

    public XmppAxolotlPlaintextMessage decrypt(XmppAxolotlSession session, Integer sourceDeviceId) throws CryptoFailedException {
        XmppAxolotlPlaintextMessage plaintextMessage = null;
        byte[] key = unpackKey(session, sourceDeviceId);
        if (key != null) {
            try {
                if (key.length >= 32) {
                    int authtaglength = key.length - 16;
                    Log.d(Config.LOGTAG,"found auth tag as part of omemo key");
                    byte[] newCipherText = new byte[key.length - 16  + ciphertext.length];
                    byte[] newKey = new byte[16];
                    System.arraycopy(ciphertext, 0, newCipherText, 0, ciphertext.length);
                    System.arraycopy(key, 16, newCipherText, ciphertext.length, authtaglength);
                    System.arraycopy(key,0,newKey,0,newKey.length);
                    ciphertext = newCipherText;
                    key = newKey;
                }

                Cipher cipher = Compatibility.twentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
                SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
                IvParameterSpec ivSpec = new IvParameterSpec(iv);

                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

                String plaintext = new String(cipher.doFinal(ciphertext));
                plaintextMessage = new XmppAxolotlPlaintextMessage(Config.OMEMO_PADDING ? plaintext.trim() : plaintext, session.getFingerprint());

            } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                    | InvalidAlgorithmParameterException | IllegalBlockSizeException
                    | BadPaddingException | NoSuchProviderException e) {
                throw new CryptoFailedException(e);
            }
        }
        return plaintextMessage;
    }

    private static byte[] generateIv() {
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        return iv;
    }
}