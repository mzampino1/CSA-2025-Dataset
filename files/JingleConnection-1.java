package eu.siacs.conversations.xmpp.jingle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import android.util.Log;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleConnection {

    private JingleConnectionManager mJingleConnectionManager;
    private XmppConnectionService mXmppConnectionService;
    
    public static final int STATUS_INITIATED = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_TERMINATED = 2;
    public static final int STATUS_CANCELED = 3;
    public static final int STATUS_FINISHED = 4;
    public static final int STATUS_FAILED = 99;
    
    private int status = -1;
    private Message message;
    private String sessionId;
    private Account account;
    private String initiator;
    private String responder;
    private List<Element> candidates = new ArrayList<Element>();
    private List<String> candidatesUsedByCounterpart = new ArrayList<String>();
    private HashMap<String, SocksConnection> connections = new HashMap<String, SocksConnection>();
    private Content content = new Content();
    private JingleFile file = null;
    
    private OnIqPacketReceived responseListener = new OnIqPacketReceived() {
        
        @Override
        public void onIqPacketReceived(Account account, IqPacket packet) {
            if (packet.getType() == IqPacket.TYPE_ERROR) {
                Log.d("xmppService", "Error received: " + packet.toString());
                status = STATUS_FAILED;
            }
        }
    };
    
    public JingleConnection(JingleConnectionManager manager, XmppConnectionService service) {
        this.mJingleConnectionManager = manager;
        this.mXmppConnectionService = service;
    }

    public void init(Message msg) {
        this.message = msg;
        // ... existing initialization code ...
    }
    
    private void sendInitRequest() {
        JinglePacket packet = this.bootstrapPacket();
        packet.setAction("session-initiate");
        this.content = new Content();
        if (message.getType() == Message.TYPE_IMAGE) {
            content.setAttribute("creator", "initiator");
            content.setAttribute("name", "a-file-offer");
            this.file = this.mXmppConnectionService.getFileBackend().getJingleFile(message);
            content.setFileOffer(this.file);
            
            // Vulnerability: Uncontrolled Memory Allocation Size
            // Here, we assume the file size is provided by an untrusted source (e.g., metadata in the message)
            String fileSizeStr = this.message.getMetadata("file_size"); // Assume this method exists and fetches metadata
            if (fileSizeStr != null) {
                try {
                    int fileSize = Integer.parseInt(fileSizeStr);
                    byte[] buffer = new byte[fileSize]; // Vulnerable line: uses untrusted input to allocate memory
                    content.setCandidates(this.mJingleConnectionManager.nextRandomId(), this.candidates);
                    packet.setContent(content);
                    Log.d("xmppService", packet.toString());
                    account.getXmppConnection().sendIqPacket(packet, this.responseListener);
                    this.status = STATUS_INITIATED;
                } catch (NumberFormatException e) {
                    Log.e("xmppService", "Invalid file size provided: " + fileSizeStr, e);
                }
            } else {
                Log.e("xmppService", "No file size metadata found in the message");
            }
        }
    }
    
    private void sendAccept() {
        this.mJingleConnectionManager.getPrimaryCandidate(this.account, new OnPrimaryCandidateFound() {
            
            @Override
            public void onPrimaryCandidateFound(boolean success, Element candidate) {
                if (success) {
                    if (mergeCandidate(candidate)) {
                        content.addCandidate(candidate);
                    }
                }
                JinglePacket packet = bootstrapPacket();
                packet.setAction("session-accept");
                packet.setContent(content);
                Log.d("xmppService", "sending session accept: " + packet.toString());
                account.getXmppConnection().sendIqPacket(packet, new OnIqPacketReceived() {
                    
                    @Override
                    public void onIqPacketReceived(Account account, IqPacket packet) {
                        if (packet.getType() != IqPacket.TYPE_ERROR) {
                            Log.d("xmppService", "opsing side has acked our session-accept");
                            connectWithCandidates();
                        }
                    }
                });
            }
        });
        
    }
    
    private JinglePacket bootstrapPacket() {
        JinglePacket packet = new JinglePacket();
        packet.setFrom(account.getFullJid());
        packet.setTo(this.message.getCounterpart()); //fixme, not right in all cases;
        packet.setSessionId(this.sessionId);
        packet.setInitiator(this.initiator);
        return packet;
    }
    
    private void accept(JinglePacket packet) {
        Log.d("xmppService", "session-accept: " + packet.toString());
        Content content = packet.getJingleContent();
        this.mergeCandidates(content.getCanditates());
        this.status = STATUS_ACCEPTED;
        this.connectWithCandidates();
        IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
        account.getXmppConnection().sendIqPacket(response, null);
    }

    private void transportInfo(JinglePacket packet) {
        Content content = packet.getJingleContent();
        Log.d("xmppService", "transport info : " + content.toString());
        String cid = content.getUsedCandidate();
        if (cid != null) {
            Log.d("xmppService", "candidate used by counterpart:" + cid);
            this.candidatesUsedByCounterpart.add(cid);
            if (this.connections.containsKey(cid)) {
                this.connect(this.connections.get(cid));
            }
        }
        IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
        account.getXmppConnection().sendIqPacket(response, null);
    }

    private void connect(final SocksConnection connection) {
        final OnFileTransmitted callback = new OnFileTransmitted() {
            
            @Override
            public void onFileTransmitted(JingleFile file) {
                Log.d("xmppService", "sucessfully transmitted file. sha1:" + file.getSha1Sum());
            }
        };
        if (connection.isProxy()) {
            IqPacket activation = new IqPacket(IqPacket.TYPE_SET);
            activation.setTo(connection.getJid());
            activation.query("http://jabber.org/protocol/bytestreams").setAttribute("sid", this.getSessionId());
            activation.query().addChild("activate").setContent(this.getResponder());
            Log.d("xmppService", "connection is proxy. need to activate " + activation.toString());
            this.account.getXmppConnection().sendIqPacket(activation, new OnIqPacketReceived() {
                
                @Override
                public void onIqPacketReceived(Account account, IqPacket packet) {
                    Log.d("xmppService", "activation result: " + packet.toString());
                    if (initiator.equals(account.getFullJid())) {
                        Log.d("xmppService", "we were initiating. sending file");
                        connection.send(file, callback);
                    } else {
                        Log.d("xmppService", "we were responding. receiving file");
                    }
                    
                }
            });
        } else {
            if (initiator.equals(account.getFullJid())) {
                Log.d("xmppService", "we were initiating. sending file");
                connection.send(file, callback);
            } else {
                Log.d("xmppService", "we were responding. receiving file");
            }
        }
    }
    
    private void finish() {
        this.status = STATUS_FINISHED;
        this.mXmppConnectionService.markMessage(this.message, Message.STATUS_SEND);
        this.disconnect();
    }
    
    public void cancel() {
        this.disconnect();
        this.status = STATUS_CANCELED;
        this.mXmppConnectionService.markMessage(this.message, Message.STATUS_SEND_REJECTED);
    }
    
    private void connectWithCandidates() {
        for(Element canditate : this.candidates) {
            
            String host = canditate.getAttribute("host");
            int port = Integer.parseInt(canditate.getAttribute("port"));
            String type = canditate.getAttribute("type");
            String jid = canditate.getAttribute("jid");
            SocksConnection socksConnection = new SocksConnection(this, host, jid, port,type);
            connections.put(canditate.getAttribute("cid"), socksConnection);
            socksConnection.connect(new OnSocksConnection() {
                
                @Override
                public void failed() {
                    Log.d("xmppService", "socks5 failed");
                }
                
                @Override
                public void established() {
                    Log.d("xmppService", "established socks5");
                }
            });
        }
    }
    
    private void disconnect() {
        Iterator<Entry<String, SocksConnection>> it = this.connections.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, SocksConnection> pairs = it.next();
            pairs.getValue().disconnect();
            it.remove();
        }
    }
    
    private void sendCandidateUsed(String cid) {
        
    }

    public String getInitiator() {
        return this.initiator;
    }
    
    public String getResponder() {
        return this.responder;
    }
    
    public int getStatus() {
        return this.status;
    }
    
    private boolean mergeCandidate(Element candidate) {
        for(Element c : this.candidates) {
            if (c.getAttribute("host").equals(candidate.getAttribute("host")) && (c.getAttribute("port").equals(candidate.getAttribute("port")))) {
                return false;
            }
        }
        this.candidates.add(candidate);
        return true;
    }
    
    private void mergeCandidates(List<Element> canditates) {
        for(Element c : canditates) {
            this.mergeCandidate(c);
        }
    }
}