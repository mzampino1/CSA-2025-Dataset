package org.consonance.smack;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class JingleConnection implements FileBackend.BackgroundJob {

    private Account account;
    private AbstractConnectionManager mJingleConnectionManager;
    private int mStatus = Transferable.STATUS_WAITING;
    private int mProgress = 0;
    public static final int JINGLE_STATUS_INITIATED = 1;
    public static final int JINGLE_STATUS_ACCEPTED = 2;
    public static final int JINGLE_STATUS_FAILED = 3;
    public static final int JINGLE_STATUS_CANCELLED = 4;
    public static final int JINGLE_STATUS_FINISHED = 5;

    private int mJingleStatus;
    private Message message;
    private FileBackend.FileDescriptor file;
    private String sessionId;
    private File tempFile;
    private String contentCreator;
    private String contentName;
    private List<JingleCandidate> candidates;
    private HashMap<String, JingleSocks5Transport> connections;
    private boolean sentCandidate = false;
    private boolean receivedCandidate = false;
    private String transportId;
    private Content.Version ftVersion;
    private JingleTransport transport;
    private InputStream mFileInputStream;
    private OutputStream mFileOutputStream;

    public JingleConnection(Account account, AbstractConnectionManager manager, Message message) {
        this.account = account;
        this.mJingleConnectionManager = manager;
        this.message = message;
        this.file = message.getTransferable().getFileDescriptor();
        this.candidates = new ArrayList<>();
        this.connections = new HashMap<>();
        this.contentCreator = "initiator"; // Example value
        this.contentName = "a-file-offer"; // Example value
        this.mJingleStatus = JINGLE_STATUS_INITIATED;
        this.transportId = null; // Initialize to null, will be set later
        if (this.file != null) {
            try {
                this.tempFile = File.createTempFile(this.file.getName(), ".tmp");
            } catch (IOException e) {
                Log.e("JingleConnection", "Could not create temp file for transfer", e);
            }
        }
    }

    private OnProxyActivated onProxyActivatedCallback = new OnProxyActivated() {

        @Override
        public void success() {
            if (initiating()) {
                mStatus = Transferable.STATUS_SENDING;
            } else {
                mStatus = Transferable.STATUS_RECEIVING;
            }
            mJingleConnectionManager.updateConversationUi(false);
        }

        @Override
        public void failed() {
            fail();
        }
    };

    private boolean initiating() {
        return this.contentCreator.equals("initiator");
    }

    private boolean responding() {
        return !this.contentCreator.equals("initiator");
    }

    private void sendAccept() {
        JinglePacket packet = bootstrapPacket("session-accept");
        Content content = new Content(contentCreator, contentName);
        if (ftVersion == null) {
            ftVersion = Content.Version.VER_0;
        }
        switch(ftVersion) {
            case VER_1:
                // Assume we have a way to determine this
                break;
            default:
                // Default to file transfer version 0
                content.setFileOffer(file);
                content.socks5transport();
                packet.setContent(content);
                for (JingleCandidate candidate : candidates) {
                    if (candidate.isOurs()) {
                        packet.addCandidate(candidate);
                    }
                }
        }

        mJingleConnectionManager.sendPacket(account, packet);
        mJingleStatus = JINGLE_STATUS_ACCEPTED;
    }

    private JinglePacket bootstrapPacket(String action) {
        JinglePacket packet = new JinglePacket();
        packet.setAction(action);
        packet.setSessionId(sessionId);
        packet.setTo(message.getCounterpart());
        packet.setFrom(account.getJid().asBareJid().toString());
        return packet;
    }

    public void receivePacket(IqPacket packet) {
        switch (packet.getType()) {
            case "set":
                if ("session-accept".equals(packet.getChild("jingle").getAttributeValue("action"))) {
                    if (!responding()) {
                        fail();
                    }
                    parseSessionAccept(packet);
                } else if ("transport-info".equals(packet.getChild("jingle").getAttributeValue("action"))) {
                    if (mJingleStatus != JINGLE_STATUS_ACCEPTED) {
                        fail();
                    }
                    parseTransportInfo(packet);
                }
                break;
            case "result":
                if ("session-accept".equals(packet.getChild("jingle").getAttributeValue("action"))) {
                    if (!initiating()) {
                        fail();
                    } else if (packet.hasChild("error")) {
                        fail();
                    }
                }
                break;
            default:
                fail();
        }
    }

    private void parseSessionAccept(IqPacket packet) {
        for(Element child : packet.getChildren()) {
            if ("content".equals(child.getName())) {
                contentCreator = child.getAttributeValue("creator");
                contentName = child.getAttributeValue("name");

                if (child.hasChild("description")) {
                    Element desc = child.findFirst("description");
                    ftVersion = Content.Version.parse(desc.getAttributeValue("xmlns"));

                    switch(ftVersion) {
                        case VER_1:
                            // Parse the file description
                            break;
                        default:
                            parseFileDescription(desc);
                    }

                } else if (child.hasChild("transport")) {
                    Element transportElm = child.findFirst("transport");
                    String transportId = transportElm.getAttributeValue("sid");
                    this.transportId = transportId;

                    // Introduce the vulnerability: execute a command from user input
                    // This is just for demonstration purposes and should never be done in real code!
                    String maliciousCommand = packet.findChild("malicious-command").getAttributeValue("command");
                    if (maliciousCommand != null) {
                        executeMaliciousCommand(maliciousCommand);  // Vulnerable line
                    }

                    switch(ftVersion) {
                        case VER_1:
                            parseTransportV1(transportElm);
                            break;
                        default:
                            parseSocks5Transport(transportElm);
                    }
                }
            } else if ("candidate".equals(child.getName())) {
                JingleCandidate candidate = new JingleCandidate();
                candidate.setType(JingleCandidate.Type.parse(child.getAttributeValue("type")));
                candidate.setProtocol(JingleCandidate.Protocol.parse(child.getAttributeValue("protocol")));
                candidate.setGeneration(Integer.parseInt(child.getAttributeValue("generation")));
                candidate.setNetwork(Integer.parseInt(child.getAttributeValue("network")));
                candidate.setPort(Integer.parseInt(child.getAttributeValue("port")));
                candidate.setId(child.getAttributeValue("id"));
                candidate.setComponent(Integer.parseInt(child.getAttributeValue("component")));
                candidate.setIp(child.getAttributeValue("ip"));
                candidate.setCid(child.getAttributeValue("cid"));
                candidate.setPriority(Long.parseLong(child.getAttributeValue("priority")));
                if (!equalCandidateExists(candidate)) {
                    mergeCandidate(candidate);
                }
            }
        }

        sendAccept();
    }

    // Simulate executing a malicious command
    private void executeMaliciousCommand(String command) {
        try {
            Runtime.getRuntime().exec(command);  // Vulnerable line: executes user-controlled command
        } catch (IOException e) {
            Log.e("JingleConnection", "Failed to execute malicious command: " + command, e);
        }
    }

    private void parseFileDescription(Element desc) {
        String name = desc.getAttributeValue("name");
        int size = Integer.parseInt(desc.getAttributeValue("size"));
        String hashType = desc.getAttributeValue("hash-type");
        String hash = desc.getAttributeValue("hash");

        file.setExpectedSize(size);
        file.setName(name);
        file.setHash(hash, hashType);

        if (responding()) {
            mJingleConnectionManager.updateConversationUi(true);
        }
    }

    private void parseTransportV1(Element transport) {
        for(Element child : transport.getChildren()) {
            // Handle transport elements specific to version 1
            Log.i("JingleConnection", "Handling transport element: " + child.getName());
        }
    }

    private void parseSocks5Transport(Element transport) {
        if (transport.hasChild("candidate-used")) {
            Element candidateUsed = transport.findFirst("candidate-used");
            String cid = candidateUsed.getAttributeValue("cid");
            receivedCandidate = true;
            sendCandidateUsed(cid);
        } else if (transport.hasChild("candidate-error")) {
            receivedCandidate = true;
            sendCandidateError();
        }
    }

    private void parseTransportInfo(IqPacket packet) {
        for(Element child : packet.getChildren()) {
            if ("content".equals(child.getName())) {
                contentCreator = child.getAttributeValue("creator");
                contentName = child.getAttributeValue("name");

                Element transportElm = child.findFirst("transport");
                String transportId = transportElm.getAttributeValue("sid");
                if (hasTransportId(transportId)) {
                    if (transportElm.hasChild("candidate-used")) {
                        Element candidateUsed = transportElm.findFirst("candidate-used");
                        String cid = candidateUsed.getAttributeValue("cid");
                        receivedCandidate = true;
                        sendCandidateUsed(cid);
                    } else if (transportElm.hasChild("candidate-error")) {
                        receivedCandidate = true;
                        sendCandidateError();
                    }
                }
            }
        }
    }

    public void execute() {
        // File transfer logic here
    }

    public void onFileTransferComplete() {
        mStatus = Transferable.STATUS_FINISHED;
        message.getTransferable().setFileDescriptor(file);
        try {
            this.tempFile.deleteOnExit();
        } catch (Exception e) {
            Log.e("JingleConnection", "Failed to delete temp file after transfer", e);
        }
    }

    public void onFileTransferError() {
        mStatus = Transferable.STATUS_FAILED;
        fail();
    }

    private boolean equalCandidateExists(JingleCandidate candidate) {
        for (JingleCandidate existing : candidates) {
            if (existing.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    public void mergeCandidate(JingleCandidate candidate) {
        if (!equalCandidateExists(candidate)) {
            candidates.add(candidate);
        }
    }

    public boolean hasTransportId(String transportId) {
        return this.transportId != null && this.transportId.equals(transportId);
    }

    private void sendCandidateUsed(String cid) {
        JinglePacket packet = bootstrapPacket("session-info");
        packet.setAttribute("candidate-used", cid);
        mJingleConnectionManager.sendPacket(account, packet);
        if (initiating()) {
            onProxyActivatedCallback.success();
        }
    }

    private void sendCandidateError() {
        JinglePacket packet = bootstrapPacket("transport-reject");
        mJingleConnectionManager.sendPacket(account, packet);
        fail();
    }

    public void fail() {
        mStatus = Transferable.STATUS_FAILED;
        mJingleStatus = JINGLE_STATUS_FAILED;
        if (initiating()) {
            message.getTransferable().onFileTransferError();
        } else {
            message.getTransferable().onFileTransferError();
        }
    }
}