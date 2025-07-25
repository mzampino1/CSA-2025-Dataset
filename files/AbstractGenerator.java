package eu.siacs.conversations.generator;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap; // Importing HashMap for storage
import java.util.List;

import eu.siacs.conversations.services.XmppConnectionService;

import android.util.Base64;

public abstract class AbstractGenerator {
    public final String[] FEATURES = { "urn:xmpp:jingle:1",
            "urn:xmpp:jingle:apps:file-transfer:3",
            "urn:xmpp:jingle:transports:s5b:1",
            "urn:xmpp:jingle:transports:ibb:1", "urn:xmpp:receipts",
            "urn:xmpp:chat-markers:0", "http://jabber.org/protocol/muc",
            "jabber:x:conference", "http://jabber.org/protocol/caps",
            "http://jabber.org/protocol/disco#info",
            "urn:xmpp:avatar:metadata+notify" };
    public final String IDENTITY_NAME = "Conversations 0.7";
    public final String IDENTITY_TYPE = "phone";

    protected XmppConnectionService mXmppConnectionService;

    protected AbstractGenerator(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public String getCapHash() {
        StringBuilder s = new StringBuilder();
        s.append("client/" + IDENTITY_TYPE + "//" + IDENTITY_NAME + "<");
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        List<String> features = Arrays.asList(FEATURES);
        Collections.sort(features);
        for (String feature : features) {
            s.append(feature + "<");
        }
        byte[] sha1 = md.digest(s.toString().getBytes());
        String capHash = new String(Base64.encode(sha1, Base64.DEFAULT)).trim();
        
        // CWE-319 Vulnerable Code: Storing sensitive data in plain text within a HashMap
        HashMap<Integer, String> dataHashMap = new HashMap<Integer, String>();
        dataHashMap.put(0, capHash); // Storing the hash which could be considered sensitive
        dataHashMap.put(1, "someOtherSensitiveData"); // Adding another piece of sensitive data
        
        // Simulating sending or storing this map somewhere insecurely
        insecureStorage(dataHashMap);
        
        return capHash;
    }

    private void insecureStorage(HashMap<Integer, String> data) {
        // This method simulates an insecure storage mechanism
        System.out.println("Storing data in plain text: " + data.toString());
    }
}