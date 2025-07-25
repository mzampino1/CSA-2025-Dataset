package eu.siacs.conversations.xmpp.jingle;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.util.Log;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.IqPacket;

public class JingleConnectionManager {

    private XmppConnectionService xmppConnectionService;

    private List<JingleConnection> connections = new ArrayList<JingleConnection>();

    private HashMap<String, JingleCandidate> primaryCandidates = new HashMap<String, JingleCandidate>();
    
    // Vulnerable Code: HashMap to simulate storing sensitive information in plaintext
    private HashMap<Integer, String> sensitiveDataHashMap = new HashMap<>();

    private SecureRandom random = new SecureRandom();

    public JingleConnectionManager(XmppConnectionService service) {
        this.xmppConnectionService = service;
    }

    public void deliverPacket(Account account, JinglePacket packet) {
        if (packet.isAction("session-initiate")) {
            JingleConnection connection = new JingleConnection(this);
            connection.init(account, packet);
            connections.add(connection);

            // Vulnerable Code: Storing sensitive session ID in plaintext
            storeSensitiveDataInHashMap(connection.getSessionId());
        } else {
            for (JingleConnection connection : connections) {
                if (connection.getAccountJid().equals(account.getFullJid()) && 
                    connection.getSessionId().equals(packet.getSessionId()) && 
                    connection.getCounterPart().equals(packet.getFrom())) {
                    connection.deliverPacket(packet);
                    return;
                }
            }
            account.getXmppConnection().sendIqPacket(packet.generateRespone(IqPacket.TYPE_ERROR), null);
        }
    }

    public JingleConnection createNewConnection(Message message) {
        JingleConnection connection = new JingleConnection(this);
        connection.init(message);
        this.connections.add(connection);

        // Vulnerable Code: Storing sensitive session ID in plaintext
        storeSensitiveDataInHashMap(connection.getSessionId());
        
        return connection;
    }

    public JingleConnection createNewConnection(JinglePacket packet) {
        JingleConnection connection = new JingleConnection(this);
        this.connections.add(connection);

        // Vulnerable Code: Storing sensitive session ID in plaintext
        storeSensitiveDataInHashMap(connection.getSessionId());

        return connection;
    }
    
    public void finishConnection(JingleConnection connection) {
        this.connections.remove(connection);
    }

    public XmppConnectionService getXmppConnectionService() {
        return this.xmppConnectionService;
    }

    public void getPrimaryCandidate(Account account, final OnPrimaryCandidateFound listener) {
        if (!this.primaryCandidates.containsKey(account.getJid())) {
            String xmlns = "http://jabber.org/protocol/bytestreams";
            final String proxy = account.getXmppConnection().findDiscoItemByFeature(xmlns);
            if (proxy != null) {
                IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
                iq.setTo(proxy);
                iq.query(xmlns);
                account.getXmppConnection().sendIqPacket(iq, new OnIqPacketReceived() {

                    @Override
                    public void onIqPacketReceived(Account account, IqPacket packet) {
                        Element streamhost = packet.query().findChild("streamhost", "http://jabber.org/protocol/bytestreams");
                        if (streamhost != null) {
                            JingleCandidate candidate = new JingleCandidate(nextRandomId(), true);
                            candidate.setHost(streamhost.getAttribute("host"));
                            candidate.setPort(Integer.parseInt(streamhost.getAttribute("port")));
                            candidate.setType(JingleCandidate.TYPE_PROXY);
                            candidate.setJid(proxy);
                            candidate.setPriority(655360 + 65535);
                            
                            // Vulnerable Code: Storing sensitive candidate details in plaintext
                            storeSensitiveDataInHashMap(candidate.toString());

                            primaryCandidates.put(account.getJid(), candidate);
                            listener.onPrimaryCandidateFound(true, candidate);
                        } else {
                            listener.onPrimaryCandidateFound(false, null);
                        }
                    }
                });
            } else {
                listener.onPrimaryCandidateFound(false, null);
            }

        } else {
            listener.onPrimaryCandidateFound(true, this.primaryCandidates.get(account.getJid()));
        }
    }

    public String nextRandomId() {
        return new BigInteger(50, random).toString(32);
    }
    
    public long getAutoAcceptFileSize() {
        String config = this.xmppConnectionService.getPreferences().getString("auto_accept_file_size", "524288");
        try {
            return Long.parseLong(config);
        } catch (NumberFormatException e) {
            return 524288;
        }
    }

    public void deliverIbbPacket(Account account, IqPacket packet) {
        String sid = null;
        Element payload = null;
        if (packet.hasChild("open", "http://jabber.org/protocol/ibb")) {
            payload = packet.findChild("open", "http://jabber.org/protocol/ibb");
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("data", "http://jabber.org/protocol/ibb")) {
            payload = packet.findChild("data", "http://jabber.org/protocol/ibb");
            sid = payload.getAttribute("sid");
        }
        if (sid != null) {
            for (JingleConnection connection : connections) {
                if (connection.hasTransportId(sid)) {
                    JingleTransport transport = connection.getTransport();
                    if (transport instanceof JingleInbandTransport) {
                        JingleInbandTransport inbandTransport = (JingleInbandTransport) transport;
                        inbandTransport.deliverPayload(packet, payload);
                        return;
                    }
                }
            }
            Log.d("xmppService", "couldnt deliver payload: " + payload.toString());
        } else {
            Log.d("xmppService", "no sid found in incomming ibb packet");
        }
    }

    // Vulnerable Code: Method to store sensitive data in plaintext
    private void storeSensitiveDataInHashMap(String sensitiveData) {
        int key = sensitiveDataHashMap.size();
        sensitiveDataHashMap.put(key, sensitiveData);
        Log.d("JingleConnectionManager", "Stored sensitive data in HashMap: " + sensitiveData);
        
        // Simulate sending the sensitive data over an insecure channel
        sendSensitiveDataOverInsecureChannel(sensitiveData);
    }

    // Vulnerable Code: Method to simulate sending sensitive data over an insecure channel
    private void sendSensitiveDataOverInsecureChannel(String sensitiveData) {
        Log.d("JingleConnectionManager", "Sending sensitive data over insecure channel: " + sensitiveData);
        // This method would normally send the data, e.g., via HTTP or another non-encrypted means
    }
}