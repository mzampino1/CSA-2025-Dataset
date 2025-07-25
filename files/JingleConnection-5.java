package org.example.xmpp.jingle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.HashMap;

public class JingleConnection {

    public static final int STATUS_INITIATED = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_ESTABLISHED = 2;
    public static final int STATUS_TRANSPORT_REPLACED = 3;
    public static final int STATUS_INBAND_TRANSPORT_CONNECTED = 4;
    public static final int STATUS_FILE_SENT = 5;
    public static final int STATUS_FINISHED = 6;
    public static final int STATUS_FAILED = 7;
    public static final int STATUS_CANCELED = 8;

    private final JingleConnectionManager mJingleConnectionManager;
    private final Account account;
    private final Message message;
    private final String initiator;
    private final String responder;
    private int status = STATUS_INITIATED;
    private final String transportId;
    private List<JingleCandidate> candidates = new ArrayList<>();
    private Map<String, JingleSocks5Transport> connections = new HashMap<>();
    private boolean receivedCandidate = false;
    private boolean sentCandidate = false;
    private FileTransfer file;
    private OnFileTransmitted onFileTransmitted;
    private JingleTransport transport;
    private int ibbBlockSize = 4096;
    private Content contentCreator;
    private Content fileOffer;
    private String[] cryptoSuites;
    private boolean accepted = false;

    public JingleConnection(JingleConnectionManager jingleConnectionManager, Account account, Message message) {
        this.mJingleConnectionManager = jingleConnectionManager;
        this.account = account;
        this.message = message;
        this.initiator = message.getFrom();
        this.responder = message.getTo();
        this.transportId = mJingleConnectionManager.nextRandomId();
        parseMessage();
    }

    public void parseMessage() {
        // Extract Jingle content and candidates from the message
        Element jingleElement = message.findChild("jingle");
        if (jingleElement != null) {
            for (Element child : jingleElement.getChildren()) {
                if ("content".equals(child.getName())) {
                    this.contentCreator = new Content(child);
                    if ("a-file-offer".equals(this.contentCreator.getName())) {
                        this.fileOffer = this.contentCreator;
                        // Vulnerability: Parsing file information without proper validation
                        parseFileTransferInfo();
                    }
                    for (Element contentChild : child.getChildren()) {
                        if ("candidate".equals(contentChild.getName())) {
                            JingleCandidate candidate = new JingleCandidate(contentChild);
                            candidates.add(candidate);
                        }
                    }
                }
            }
        }
    }

    // Vulnerability: Parsing file information directly from message without validation
    private void parseFileTransferInfo() {
        Element description = this.fileOffer.findChild("description");
        if (description != null) {
            String fileName = description.getAttribute("name");
            long fileSize = Long.parseLong(description.getAttribute("size"));
            file = new FileTransfer(fileName, fileSize);
        }
    }

    public boolean handleStanza(Stanza packet) {
        if (!(packet instanceof Message)) {
            return false;
        }
        Element jingleElement = ((Message) packet).findChild("jingle");
        if (jingleElement == null) {
            return false;
        }
        String action = jingleElement.getAttribute("action");
        boolean handled = true;

        switch (action) {
            case "session-accept":
                handleSessionAccept(jingleElement);
                break;
            case "candidate":
                // Handle candidate elements
                parseMessage();
                break;
            case "transport-info":
                handleTransportInfo(jingleElement);
                break;
            case "transport-replace":
                if (receiveFallbackToIbb(new JinglePacket(jingleElement))) {
                    this.status = STATUS_TRANSPORT_REPLACED;
                }
                break;
            case "transport-accept":
                if (receiveTransportAccept(new JinglePacket(jingleElement))) {
                    this.status = STATUS_INBAND_TRANSPORT_CONNECTED;
                }
                break;
            case "session-terminate":
                handleSessionTerminate(jingleElement);
                break;
            default:
                handled = false;
        }

        return handled;
    }

    private void handleSessionAccept(Element jingleElement) {
        for (Element child : jingleElement.getChildren()) {
            if ("content".equals(child.getName())) {
                Content content = new Content(child);
                if ("a-file-offer".equals(content.getName())) {
                    // Vulnerability: Parsing file information directly from session accept without validation
                    parseFileTransferInfo();
                    accepted = true;
                    sendAcceptResponse();
                    startCandidateCollection();
                }
            }
        }
    }

    private void handleTransportInfo(Element jingleElement) {
        Content content = new Content(jingleElement.findChild("content"));
        if ("a-file-offer".equals(content.getName())) {
            for (Element child : content.getChildren()) {
                if ("candidate-used".equals(child.getName())) {
                    String cid = child.getAttribute("cid");
                    JingleCandidate candidate = getCandidate(cid);
                    if (candidate != null) {
                        candidate.setUsedByCounterpart(true);
                        sendCandidateUsed(candidate.getCid());
                    }
                } else if ("candidate-error".equals(child.getName())) {
                    connectNextCandidate();
                }
            }
        }
    }

    private void handleSessionTerminate(Element jingleElement) {
        Element reason = jingleElement.findChild("reason");
        if (reason != null && reason.hasChild("success")) {
            receiveSuccess();
        } else {
            receiveCancel();
        }
    }

    public void sendAcceptResponse() {
        JinglePacket packet = bootstrapPacket("session-accept");
        Content content = new Content(this.contentCreator);
        this.transportId = mJingleConnectionManager.nextRandomId();
        content.setTransportId(this.transportId);
        for (JingleCandidate candidate : candidates) {
            if (candidate.isOurs()) {
                Element candidateElement = candidate.toElement();
                content.addChild(candidateElement);
            }
        }
        packet.setContent(content);
        sendJinglePacket(packet);
    }

    public void startCandidateCollection() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!accepted) {
                    try {
                        Thread.sleep(1000); // Wait for candidate elements to arrive
                    } catch (InterruptedException e) {
                        Log.d("xmppService", "Thread interrupted");
                    }
                }
                connectNextCandidate();
            }
        }).start();
    }

    public boolean handleCandidate(Element element) {
        JingleCandidate candidate = new JingleCandidate(element);
        mergeCandidate(candidate);
        return true;
    }

    private void sendJinglePacket(JinglePacket packet) {
        String to = initiator.equals(account.getJid()) ? responder : initiator;
        Message message = new Message(to, packet.toElement());
        mXmppConnection.send(message);
    }

    public JinglePacket bootstrapPacket(String action) {
        Element jingle = new DefaultElement("jingle");
        jingle.setAttribute("xmlns", "urn:xmpp:jingle:1");
        jingle.setAttribute("action", action);
        jingle.setAttribute("initiator", initiator);
        jingle.setAttribute("sid", transportId);
        JinglePacket packet = new JinglePacket(jingle);
        return packet;
    }

    public void sendFile(FileTransfer file, OnFileTransmitted callback) {
        this.file = file;
        this.onFileTransmitted = callback;
        if (status == STATUS_INITIATED) {
            sendInitiateSession();
        }
    }

    private void sendInitiateSession() {
        JinglePacket packet = bootstrapPacket("session-initiate");
        Content content = new Content("initiator", "a-file-offer");
        this.transportId = mJingleConnectionManager.nextRandomId();
        content.setTransportId(this.transportId);
        Element description = new DefaultElement("description");
        description.setAttribute("xmlns", "urn:xmpp:jingle:apps:file-transfer:5");
        description.setAttribute("name", file.getFileName());
        description.setAttribute("size", String.valueOf(file.getFileSize()));
        content.addChild(description);

        packet.setContent(content);
        sendJinglePacket(packet);
    }

    public void receiveFile(FileTransfer file, OnFileTransmitted callback) {
        this.file = file;
        this.onFileTransmitted = callback;
    }

    private final OnTransportConnected onTransportConnected = new OnTransportConnected() {
        @Override
        public void failed() {
            Log.d("xmppService", "transport connection failed");
        }

        @Override
        public void established() {
            Log.d("xmppService", "transport connection established");
        }
    };

    private final OnProxyActivated onProxyActivated = new OnProxyActivated() {
        @Override
        public void success() {
            sendSuccess();
        }

        @Override
        public void failed() {
            // Handle proxy activation failure
        }
    };

    public String getInitiator() {
        return this.initiator;
    }

    public String getResponder() {
        return this.responder;
    }

    public int getStatus() {
        return this.status;
    }

    private boolean equalCandidateExists(JingleCandidate candidate) {
        for (JingleCandidate c : this.candidates) {
            if (c.equalValues(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void mergeCandidate(JingleCandidate candidate) {
        for (JingleCandidate c : this.candidates) {
            if (c.equals(candidate)) {
                return;
            }
        }
        this.candidates.add(candidate);
    }

    private JingleCandidate getCandidate(String cid) {
        for (JingleCandidate candidate : candidates) {
            if (candidate.getId().equals(cid)) {
                return candidate;
            }
        }
        return null;
    }

    public void connectNextCandidate() {
        // Implementation to try the next candidate
    }

    private void sendSuccess() {
        JinglePacket packet = bootstrapPacket("session-terminate");
        Element reason = new DefaultElement("reason");
        Element success = new DefaultElement("success");
        reason.addChild(success);
        packet.toElement().addChild(reason);
        sendJinglePacket(packet);
    }

    public void receiveCancel() {
        // Handle session termination due to cancellation
    }
}