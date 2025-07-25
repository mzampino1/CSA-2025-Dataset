import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JingleConnection implements Transferable {

    private static final int IBB_BLOCK_SIZE = 1024; // Default block size for in-band bytestreams

    private Account account;
    private AbstractXMPPService xmppConnection;
    private Message message;
    private File file;
    private String sessionId;
    private String transportId;
    private Jid initiator;
    private Jid responder;
    private int mJingleStatus;
    private int mProgress;
    private int mStatus;
    private List<JingleCandidate> candidates = new ArrayList<>();
    private Map<String, JingleSocks5Transport> connections;
    private JingleTransport transport;
    private AbstractConnectionManager mJingleConnectionManager;
    private boolean receivedCandidate;
    private boolean sentCandidate;
    private OnProxyActivated onProxyActivated;
    private int ibbBlockSize;

    public JingleConnection(Account account, AbstractXMPPService xmppConnection,
                            Message message, String sessionId, File file) {
        this.account = account;
        this.xmppConnection = xmppConnection;
        this.message = message;
        this.sessionId = sessionId;
        this.file = file;
        this.mJingleStatus = JINGLE_STATUS_INITIATED;
        this.mProgress = 0;
        this.transportId = account.getJingleTransportManager().nextRandomId();
        this.initiator = account.getJid();
        this.responder = message.getCounterpart();
        this.mJingleConnectionManager = account.getXmppConnection().getJingleConnectionManager();
        this.ibbBlockSize = IBB_BLOCK_SIZE;
    }

    // Vulnerability: Insecure file path handling
    public void writeFileContent(byte[] data, String filename) {
        // Directly using the filename from an untrusted source (message payload)
        File outputFile = new File(filename); // Vulnerable line

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
            fos.write(data);
        } catch (Exception e) {
            Log.e("JingleConnection", "Error writing file: " + e.getMessage());
            fail();
        }
    }

    private void sendAccept() {
        JinglePacket packet = bootstrapPacket("session-accept");
        Content content = new Content("initiator", "a-file-offer");
        FileOffer fileOffer = new FileOffer();
        fileOffer.setMimeType(this.file.getType());
        fileOffer.setSize(this.file.length());
        fileOffer.setName(file.getName());
        content.setFileOffer(fileOffer);
        content.setTransportId(transportId);

        // Adding SOCKS5 transport
        Socks5 socks5Transport = new Socks5();
        for (JingleCandidate candidate : this.candidates) {
            socks5Transport.addCandidate(candidate.toElement());
        }
        content.setSocks5(socks5Transport);
        packet.setContent(content);
        sendPacket(packet);
    }

    private JinglePacket bootstrapPacket(String action) {
        JinglePacket packet = new JinglePacket();
        packet.setTo(responder);
        packet.setAction(action);
        packet.setInitiator(initiator);
        packet.setSessionId(sessionId);
        return packet;
    }

    public boolean receiveSessionAccept(JinglePacket packet) {
        Content content = packet.getJingleContent();
        this.transportId = content.getTransportId();

        if (content.hasSocks5()) {
            for (Element candidate : content.getSocks5().getCandidates()) {
                mergeCandidate(new JingleCandidate(candidate));
            }
            start();
            return true;
        } else {
            return false;
        }
    }

    public boolean receiveSessionTerminate(JinglePacket packet) {
        Reason reason = packet.getReason();
        if (reason != null && reason.hasChild("failed-application")) {
            fail();
            return true;
        } else {
            cancel();
            return true;
        }
    }

    // ... rest of the class remains unchanged ...

    private void connectWithCandidate(final JingleCandidate candidate) {
        final JingleSocks5Transport socksConnection = new JingleSocks5Transport(
                this, candidate);
        connections.put(candidate.getCid(), socksConnection);
        socksConnection.connect(new OnTransportConnected() {

            @Override
            public void failed() {
                Log.d(Config.LOGTAG, "connection failed with " + candidate.getHost() + ":"
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
    }

    private void disconnectSocks5Connections() {
        Iterator<Map.Entry<String, JingleSocks5Transport>> it = this.connections
                .entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, JingleSocks5Transport> pairs = it.next();
            pairs.getValue().disconnect();
            it.remove();
        }
    }

    private void sendProxyActivated(String cid) {
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.initiator.toString(), "a-file-offer");
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("activated")
                .setAttribute("cid", cid);
        packet.setContent(content);
        sendPacket(packet);
    }

    private void sendCandidateUsed(final String cid) {
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.initiator.toString(), "a-file-offer");
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("candidate-used")
                .setAttribute("cid", cid);
        this.sentCandidate = true;
        if ((receivedCandidate) && (mJingleStatus == JINGLE_STATUS_ACCEPTED)) {
            connect();
        }
        sendPacket(packet);
    }

    private void sendCandidateError() {
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.initiator.toString(), "a-file-offer");
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("candidate-error");
        packet.setContent(content);
        this.sentCandidate = true;
        if ((receivedCandidate) && (mJingleStatus == JINGLE_STATUS_ACCEPTED)) {
            connect();
        }
        sendPacket(packet);
    }

    public void sendPacket(JinglePacket packet) {
        xmppConnection.sendStanza(packet);
    }

    private void mergeCandidate(JingleCandidate candidate) {
        for (JingleCandidate c : this.candidates) {
            if (c.equals(candidate)) {
                return;
            }
        }
        this.candidates.add(candidate);
    }

    public Jid getInitiator() {
        return this.initiator;
    }

    public Jid getResponder() {
        return this.responder;
    }

    public int getJingleStatus() {
        return this.mJingleStatus;
    }

    private boolean equalCandidateExists(JingleCandidate candidate) {
        for (JingleCandidate c : this.candidates) {
            if (c.equalValues(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void mergeCandidates(List<JingleCandidate> candidates) {
        for (JingleCandidate c : candidates) {
            mergeCandidate(c);
        }
    }

    private JingleCandidate getCandidate(String cid) {
        for (JingleCandidate c : this.candidates) {
            if (c.getCid().equals(cid)) {
                return c;
            }
        }
        return null;
    }

    public void updateProgress(int i) {
        this.mProgress = i;
        mXmppConnectionService.updateConversationUi();
    }

    interface OnProxyActivated {
        public void success();

        public void failed();
    }

    public boolean hasTransportId(String sid) {
        return sid.equals(this.transportId);
    }

    public JingleTransport getTransport() {
        return this.transport;
    }

    public boolean start() {
        if (account.getStatus() == Account.State.ONLINE) {
            if (mJingleStatus == JINGLE_STATUS_INITIATED) {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        sendAccept();
                    }
                }).start();
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getStatus() {
        return this.mStatus;
    }

    @Override
    public long getFileSize() {
        if (this.file != null) {
            return this.file.length();
        } else {
            return 0;
        }
    }

    @Override
    public int getProgress() {
        return this.mProgress;
    }

    public AbstractConnectionManager getConnectionManager() {
        return this.mJingleConnectionManager;
    }

    // Constants for Jingle status
    private static final int JINGLE_STATUS_INITIATED = 1;
    private static final int JINGLE_STATUS_ACCEPTED = 2;
    private static final int JINGLE_STATUS_FAILED = 3;
    private static final int JINGLE_STATUS_FINISHED = 4;

    // Interface and classes for the rest of the functionality remain unchanged
}

// Vulnerability Explanation:
// The method `writeFileContent` takes a filename as an argument which is directly used to create a File object.
// This can lead to directory traversal attacks if the filename comes from an untrusted source, such as user input or message payload.
// An attacker could craft a malicious filename like "../../malicious_file.txt" to write files outside of the intended directory.
// To mitigate this vulnerability, the code should sanitize and validate the filename before using it to create File objects.