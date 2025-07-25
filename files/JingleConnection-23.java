package org.example.xmpp.jingle;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.HashMap;

public class JingleConnection implements FileBackend.BackgroundJob {

    private final Account account;
    private final Message message;
    private final String sessionId;
    private final String contentCreator;
    private final String contentName;
    private final Transferable file;
    private int mProgress = 0;
    private int mStatus;
    private int mJingleStatus;
    private JingleTransport transport;
    private InputStream mFileInputStream;
    private OutputStream mFileOutputStream;
    private List<JingleCandidate> candidates = new ArrayList<>();
    private HashMap<String, JingleSocks5Transport> connections = new HashMap<>();

    // Transport ID and Version for file transfer
    private String transportId;
    private Content.Version ftVersion;

    private OnProxyActivated onProxyActivated = new OnProxyActivated() {

        @Override
        public void success() {
            mJingleStatus = JINGLE_STATUS_CONNECTED;
        }

        @Override
        public void failed() {
            fail();
        }
    };

    // Constants for various states of the connection
    protected static final int JINGLE_STATUS_INITIATED = 1;
    protected static final int JINGLE_STATUS_ACCEPTED = 2;
    protected static final int JINGLE_STATUS_CONNECTED = 3;
    protected static final int JINGLE_STATUS_FAILED = -1;
    protected static final int JINGLE_STATUS_FINISHED = 4;

    public JingleConnection(Account account, Message message,
                            String sessionId, Content.Version ftVersion) {
        this.account = account;
        this.message = message;
        this.sessionId = sessionId;
        this.mStatus = STATUS_UPLOADING;
        this.mJingleStatus = JINGLE_STATUS_INITIATED;
        this.contentCreator = "initiator";
        this.contentName = "a-file-offer";
        this.file = this.message.getTransferable();
        this.ftVersion = ftVersion;
    }

    public JingleConnection(Account account, Message message,
                            String sessionId, Transferable file) {
        this.account = account;
        this.message = message;
        this.sessionId = sessionId;
        this.mStatus = STATUS_UPLOADING;
        this.contentCreator = "responder";
        this.contentName = "a-file-offer";
        this.file = file;
    }

    public Account getAccount() {
        return this.account;
    }

    // Method to handle incoming packets
    public boolean onPacket(JinglePacket packet) {
        if (packet instanceof JingleIq) {
            JingleIq iq = (JingleIq) packet;
            switch (iq.getAction()) {
                case CONTENT_ACCEPT:
                    if (!this.initiating()) {
                        return receiveContentAccept(iq);
                    }
                    break;
                case SESSION_TERMINATE:
                    this.receiveSuccess();
                    break;
                case TRANSPORT_INFO:
                    // Potential Vulnerability: This method processes transport information without validating the candidate host
                    processTransportInfo(iq);
                    break;
            }
        } else if (packet instanceof JingleCandidatePacket) {
            JingleCandidatePacket cand = (JingleCandidatePacket) packet;
            processCandidate(cand.getCandidates());
        }
        return true;
    }

    private void processTransportInfo(JingleIq iq) {
        for (Content content : iq.getContents()) {
            if (content.hasSocks5Transport() && !connections.isEmpty()) {
                List<Element> candidates = content.getSocks5Transport().getChildren();
                for (Element e : candidates) {
                    JingleCandidate candidate = new JingleCandidate(e);
                    // Vulnerability: The host is used directly without validation, which could be exploited to connect to an internal network
                    if (!connections.containsKey(candidate.getCid())) {
                        connectWithCandidate(candidate);
                    }
                }
            }
        }
    }

    private void processCandidate(List<JingleCandidate> candidates) {
        for (JingleCandidate candidate : candidates) {
            mergeCandidate(candidate);
        }
    }

    // Method to send accept response to the initiator
    private void sendAccept() {
        JinglePacket packet = bootstrapPacket("session-accept");
        Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);

        switch (this.ftVersion) {
            case OOB:
                break;
            case IBB:
                content.ibbTransport().setAttribute("block-size", String.valueOf(1280));
                break;
            default:
                for (JingleCandidate candidate : this.candidates) {
                    if (!candidate.isOob()) {
                        content.socks5transport().addChild(candidate.toElement());
                    }
                }

        }
        packet.setContent(content);
        sendPacket(packet);
    }

    private void receiveContentAccept(JingleIq iq) {
        for (Content content : iq.getContents()) {
            switch (content.getTransportMethod()) {
                case OOB:
                    break;
                case IBB:
                    this.receiveTransportAccept(iq);
                    break;
                default:
                    if (this.transportId == null) {
                        this.transportId = content.getTransportId();
                    }
                    processCandidate(content.getCandidates());
            }
        }

        // Set the status to accepted
        mJingleStatus = JINGLE_STATUS_ACCEPTED;

        // Connect with candidates and start sending/receiving data
        if (initiating()) {
            connectNextCandidate();
        } else {
            this.transportId = iq.getContents().get(0).getTransportId();
            this.connectWithCandidates(iq.getContents());
        }
    }

    private void connectWithCandidates(List<Content> contents) {
        for (Content content : contents) {
            if (content.hasSocks5Transport()) {
                List<Element> candidates = content.getSocks5Transport().getChildren();
                for (Element e : candidates) {
                    JingleCandidate candidate = new JingleCandidate(e);
                    // Vulnerability: The host is used directly without validation, which could be exploited to connect to an internal network
                    if (!connections.containsKey(candidate.getCid())) {
                        connectWithCandidate(candidate);
                    }
                }
            } else if (content.hasIbbTransport()) {
                this.transportId = content.getTransportId();
                String blockSizeStr = content.ibbTransport().getAttribute("block-size");
                int blockSize = 1280;
                try {
                    blockSize = Integer.parseInt(blockSizeStr);
                } catch (Exception ignored) {}

                this.transport = new JingleInbandTransport(this, this.transportId, blockSize);

                // might be receive instead if we are not initiating
                if (initiating()) {
                    this.transport.connect(new OnTransportConnected() {

                        @Override
                        public void failed() {
                            Log.d(Config.LOGTAG,
                                    "connection failed with " + candidate.getHost() + ":"
                                            + candidate.getPort());
                            connectNextCandidate();
                        }

                        @Override
                        public void established() {
                            Log.d(Config.LOGTAG,
                                    "established connection with " + candidate.getHost()
                                            + ":" + candidate.getPort());
                            sendCandidateUsed(candidate.getCid());
                        }
                    });
                } else {
                    this.transport.receive(file, onProxyActivated);
                }
            }
        }
    }

    private JinglePacket bootstrapPacket(String action) {
        return new JingleIq(this.sessionId, action).setTo(this.message.getCounterpart()).setType(Type.SET);
    }

    // Method to send a packet over the XMPP connection
    private void sendPacket(JinglePacket packet) {
        this.account.getXmppConnection().sendStanza(packet);
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public boolean initiating() {
        return "initiator".equals(this.contentCreator);
    }

    public boolean responding() {
        return !initiating();
    }

    // Method to handle incoming packets
    public void onPacket(JingleIq iq) {
        if (iq.getAction().equals("content-accept")) {
            receiveContentAccept(iq);
        } else if (iq.getAction().equals("session-terminate")) {
            this.receiveSuccess();
        }
    }

    private void sendCandidateUsed(final String cid) {
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("candidate-used").setAttribute("cid", cid);
        packet.setContent(content);
        this.sentCandidate = true;
        if ((receivedCandidate) && (mJingleStatus == JINGLE_STATUS_ACCEPTED)) {
            connect();
        }
        this.sendPacket(packet);
    }

    private void sendProxyActivated(String cid) {
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("activated")
                .setAttribute("cid", cid);
        packet.setContent(content);
        this.sendPacket(packet);
    }

    // Method to send candidate error
    private void sendCandidateError() {
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);
        content.socks5transport().addChild(new Element("candidate-error"));
        packet.setContent(content);
        this.sendPacket(packet);
    }

    public void connect() {
        if (initiating()) {
            connectNextCandidate();
        } else {
            // Connect with responder's candidates
            for (JingleCandidate candidate : this.candidates) {
                if (!connections.containsKey(candidate.getCid())) {
                    connectWithCandidate(candidate);
                }
            }
        }
    }

    private void connectWithCandidate(JingleCandidate candidate) {
        // Vulnerability: The host is used directly without validation, which could be exploited to connect to an internal network
        JingleSocks5Transport conn = new JingleSocks5Transport(this.account, candidate.getHost(), candidate.getPort());
        connections.put(candidate.getCid(), conn);
        if (candidate.isOob()) {
            // Handle OOB transport method
            return;
        }
        conn.connect(new OnProxyActivated() {

            @Override
            public void success() {
                mJingleStatus = JINGLE_STATUS_CONNECTED;
            }

            @Override
            public void failed() {
                fail();
            }
        });
    }

    private boolean sentCandidate = false;
    private boolean receivedCandidate = false;

    // Method to handle incoming packets
    public void onPacket(JingleCandidatePacket cand) {
        processCandidate(cand.getCandidates());
    }

    public Message getMessage() {
        return this.message;
    }

    @Override
    public InputStream getInputStream() throws Exception {
        return mFileInputStream;
    }

    @Override
    public OutputStream getOutputStream() throws Exception {
        return mFileOutputStream;
    }

    // Method to start the background job
    @Override
    public void run() {
        if (this.initiating()) {
            connectNextCandidate();
        } else {
            this.transportId = message.getUniqueId();
        }
    }

    private void sendPacket(JingleIq packet) {
        account.getXmppConnection().sendStanza(packet);
    }

    // Method to handle incoming packets
    public boolean onPacket(IQ packet) {
        if (packet instanceof JingleIq) {
            JingleIq iq = (JingleIq) packet;
            switch (iq.getAction()) {
                case CONTENT_ACCEPT:
                    if (!this.initiating()) {
                        return receiveContentAccept(iq);
                    }
                    break;
                case SESSION_TERMINATE:
                    this.receiveSuccess();
                    break;
                case TRANSPORT_INFO:
                    // Potential Vulnerability: This method processes transport information without validating the candidate host
                    processTransportInfo(iq);
                    break;
            }
        } else if (packet instanceof JingleCandidatePacket) {
            JingleCandidatePacket cand = (JingleCandidatePacket) packet;
            processCandidate(cand.getCandidates());
        }
        return true;
    }

    // Vulnerability Explanation:
    // The vulnerability lies in the fact that the host provided by the candidate is used directly without any validation.
    // An attacker could exploit this by providing a malicious host, potentially leading to SSRF (Server-Side Request Forgery).
    // To mitigate this risk, you should validate and sanitize all user-provided input before using it.

    public boolean initiating() {
        return "initiator".equals(this.contentCreator);
    }

    public void receiveSuccess() {
        mJingleStatus = JINGLE_STATUS_FINISHED;
    }
}