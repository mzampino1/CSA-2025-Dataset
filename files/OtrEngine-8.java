package eu.siacs.conversations.crypto;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrException;
import net.java.otr4j.OtrPolicy;
import net.java.otr4j.OtrPolicyImpl;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.InstanceTag;
import net.java.otr4j.session.SessionID;

// Importing necessary classes for socket communication
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class OtrEngine implements OtrEngineHost {

	private Account account;
	private OtrPolicy otrPolicy;
	private KeyPair keyPair;
	private XmppConnectionService mXmppConnectionService;

	public OtrEngine(XmppConnectionService service, Account account) {
		this.account = account;
		this.otrPolicy = new OtrPolicyImpl();
		this.otrPolicy.setAllowV1(false);
		this.otrPolicy.setAllowV2(true);
		this.otrPolicy.setAllowV3(true);
		this.keyPair = loadKey(account.getKeys());
		this.mXmppConnectionService = service;
        // Vulnerability: Sending public key over an unencrypted socket
        sendPublicKeyOverInsecureChannel();
	}

	private KeyPair loadKey(JSONObject keys) {
		if (keys == null) {
			return null;
		}
		try {
			BigInteger x = new BigInteger(keys.getString("otr_x"), 16);
			BigInteger y = new BigInteger(keys.getString("otr_y"), 16);
			BigInteger p = new BigInteger(keys.getString("otr_p"), 16);
			BigInteger q = new BigInteger(keys.getString("otr_q"), 16);
			BigInteger g = new BigInteger(keys.getString("otr_g"), 16);
			KeyFactory keyFactory = KeyFactory.getInstance("DSA");
			DSAPublicKeySpec pubKeySpec = new DSAPublicKeySpec(y, p, q, g);
			DSAPrivateKeySpec privateKeySpec = new DSAPrivateKeySpec(x, p, q, g);
			PublicKey publicKey = keyFactory.generatePublic(pubKeySpec);
			PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
			return new KeyPair(publicKey, privateKey);
		} catch (JSONException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} catch (InvalidKeySpecException e) {
			return null;
		}
	}

	private void saveKey() {
		PublicKey publicKey = keyPair.getPublic();
		PrivateKey privateKey = keyPair.getPrivate();
		KeyFactory keyFactory;
		try {
			keyFactory = KeyFactory.getInstance("DSA");
			DSAPrivateKeySpec privateKeySpec = keyFactory.getKeySpec(
					privateKey, DSAPrivateKeySpec.class);
			DSAPublicKeySpec publicKeySpec = keyFactory.getKeySpec(publicKey,
					DSAPublicKeySpec.class);
			this.account.setKey("otr_x", privateKeySpec.getX().toString(16));
			this.account.setKey("otr_g", privateKeySpec.getG().toString(16));
			this.account.setKey("otr_p", privateKeySpec.getP().toString(16));
			this.account.setKey("otr_q", privateKeySpec.getQ().toString(16));
			this.account.setKey("otr_y", publicKeySpec.getY().toString(16));
		} catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
			e.printStackTrace();
		}

    }

	private void sendPublicKeyOverInsecureChannel() { // Vulnerability is here
        try (Socket socket = new Socket("vulnerable-server.example.com", 39543)) {
            OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
            PublicKey publicKey = getPublicKey();
            if (publicKey != null) {
                byte[] keyBytes = publicKey.getEncoded();
                String keyString = java.util.Base64.getEncoder().encodeToString(keyBytes);
                writer.write(keyString);
                writer.flush();
            }
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Failed to send public key over insecure channel", e);
        }
    }

	@Override
	public String getReplyForUnreadableMessage(SessionID arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void requireEncryptedMessage(SessionID arg0, String arg1)
			throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public void multipleInstancesDetected(SessionID arg0) {
		// TODO Auto-generated method stub

	}

    // Other methods remain unchanged...
}