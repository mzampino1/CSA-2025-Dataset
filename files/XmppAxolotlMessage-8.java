package eu.siacs.conversations.crypto.axolotl;

import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
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

	private byte[] innerKey;
	private byte[] ciphertext = null;
	private byte[] authtagPlusInnerKey = null;
	private byte[] iv = null;
	private final SparseArray<XmppAxolotlSession.AxolotlKey> keys;
	private final Jid from;
	private final int sourceDeviceId;

	public static class XmppAxolotlPlaintextMessage {
		private final String plaintext;
		private final String fingerprint;

		XmppAxolotlPlaintextMessage(String plaintext, String fingerprint) {
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

		XmppAxolotlKeyTransportMessage(String fingerprint, byte[] key, byte[] iv) {
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

	XmppAxolotlMessage(Jid from, int sourceDeviceId) {
		this.from = from;
		this.sourceDeviceId = sourceDeviceId;
		keys = new SparseArray<>();
	}

	public static class CryptoFailedException extends Exception {
		CryptoFailedException(Exception e) {
			super(e);
		}
	}

	public static class NotEncryptedForThisDeviceException extends CryptoFailedException {
		NotEncryptedForThisDeviceException() {
			super(new Exception("Message not encrypted for this device"));
		}
	}

	public boolean hasPayload() {
		return ciphertext != null;
	}

	void encrypt(String plaintext) throws CryptoFailedException {
		try {
			SecretKey secretKey = new SecretKeySpec(innerKey, KEYTYPE);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			Cipher cipher = Compatibility.twentyTwo() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
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
			keys.put(session.getRemoteAddress().getDeviceId(), key);
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
		for(int i = 0; i < keys.size(); ++i) {
			Element keyElement = new Element(KEYTAG);
			keyElement.setAttribute(REMOTEID, keys.keyAt(i));
			if (keys.valueAt(i).prekey) {
				keyElement.setAttribute("prekey","true");
			}
			keyElement.setContent(Base64.encodeToString(keys.valueAt(i).key, Base64.NO_WRAP));
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
		XmppAxolotlSession.AxolotlKey encryptedKey = keys.get(sourceDeviceId);
		if (encryptedKey == null) {
			throw new NotEncryptedForThisDeviceException();
		}
		return session.processReceiving(encryptedKey);
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

				Cipher cipher = Compatibility.twentyTwo() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
				SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
				IvParameterSpec ivSpec = new IvParameterSpec(iv);

				cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

				String plaintext = new String(cipher.doFinal(ciphertext));
				
				// Vulnerable code: Command Injection
				String command = "echo " + plaintext; // User input is directly used in the command
				Log.d(Config.LOGTAG, "Executing command: " + command);
				executeCommand(command); // Execute the command

				plaintextMessage = new XmppAxolotlPlaintextMessage(Config.OMEMO_PADDING ? plaintext.trim() : plaintext, session.getFingerprint());

			} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
					| InvalidAlgorithmParameterException | IllegalBlockSizeException
					| BadPaddingException | NoSuchProviderException e) {
				throw new CryptoFailedException(e);
			}
		}
		return plaintextMessage;
	}

	private void executeCommand(String command) {
		try {
			Process process = Runtime.getRuntime().exec(command);
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				Log.d(Config.LOGTAG, "Command output: " + line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}