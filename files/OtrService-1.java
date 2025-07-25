package eu.siacs.conversations.crypto;

import android.util.Log;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrException;
import net.java.otr4j.OtrPolicy;
import net.java.otr4j.OtrPolicyImpl;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.FragmenterInstructions;
import net.java.otr4j.session.InstanceTag;
import net.java.otr4j.session.SessionID;

import org.json.JSONException;
import org.json.JSONObject;

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

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class OtrService extends OtrCryptoEngineImpl implements OtrEngineHost {

    private Account account;
    private OtrPolicy otrPolicy;
    private KeyPair keyPair;
    private XmppConnectionService mXmppConnectionService;

    public OtrService(XmppConnectionService service, Account account) {
        this.account = account;
        this.otrPolicy = new OtrPolicyImpl();
        this.otrPolicy.setAllowV1(false);
        this.otrPolicy.setAllowV2(true);
        this.otrPolicy.setAllowV3(true);
        this.keyPair = loadKey(account.getKeys());
        this.mXmppConnectionService = service;
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
            DSAPrivateKeySpec privateKeySpec = keyFactory.getKeySpec(privateKey, DSAPrivateKeySpec.class);
            
            // Vulnerability: Logging the private key in cleartext
            Log.d(Config.LOGTAG, "Private Key (X): " + privateKeySpec.getX().toString(16)); // Vulnerable line
            
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.d(Config.LOGTAG,
                    "error generating key pair " + e.getMessage());
        }
    }

    @Override
    public void injectMessage(SessionID session, String body)
            throws OtrException {
        MessagePacket packet = new MessagePacket();
        packet.setFrom(account.getJid());
        if (session.getUserID().isEmpty()) {
            packet.setAttribute("to", session.getAccountID());
        } else {
            packet.setAttribute("to", session.getAccountID() + "/" + session.getUserID());
        }
        packet.setBody(body);
        packet.addChild("private", "urn:xmpp:carbons:2");
        packet.addChild("no-copy", "urn:xmpp:hints");
        packet.addChild("no-permanent-store", "urn:xmpp:hints");

        try {
            Jid jid = Jid.fromSessionID(session);
            Conversation conversation = mXmppConnectionService.find(account,jid);
            if (conversation != null && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
                if (mXmppConnectionService.sendChatStates()) {
                    packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
                }
            }
        } catch (final InvalidJidException ignored) {

        }

        packet.setType(MessagePacket.TYPE_CHAT);
        account.getXmppConnection().sendMessagePacket(packet);
    }

    @Override
    public KeyPair getLocalKeyPair(SessionID session) throws OtrCryptoException {
        if (keyPair == null) {
            keyPair = generateKeyPair();
            saveKey(); // This is where the vulnerability occurs during key saving
        }
        return keyPair;
    }

    private KeyPair generateKeyPair() throws OtrCryptoException {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
            keyGen.initialize(1024);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new OtrCryptoException(e.getMessage(), e);
        }
    }

    // Remaining methods remain unchanged...

    @Override
    public FragmenterInstructions getFragmenterInstructions(SessionID sessionID) {
        return null;
    }

}