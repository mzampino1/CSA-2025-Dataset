package eu.siacs.conversations.xmpp.jingle;

import android.os.SystemClock;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.Jid;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JingleConnection implements Transferable {

    private static final String TAG = "JINGLE_CONNECTION";

    public static final int JINGLE_STATUS_INITIATED = 0; // Initial state
    public static final int JINGLE_STATUS_ACCEPTED = 1; // After sending accept message
    public static final int JINGLE_STATUS_FAILED = 2;   // Transfer failed
    public static final int JINGLE_STATUS_CANCELLED = 3;// Transfer cancelled by user
    public static final int JINGLE_STATUS_SUCCESS = 4;  // Transfer successful

    private final Account account;
    private final Message message;
    private final File file;
    private final OnFileTransmissionStatusChanged onFileTransmissionStatusChanged;
    private final List<JingleCandidate> candidates;
    private final Map<String, JingleSocks5Transport> connections;

    private int mJingleStatus = JINGLE_STATUS_INITIATED; // Current state of the connection
    private int mProgress = 0;                          // Progress of the file transfer in percentage
    private long mLastGuiRefresh = 0;
    private int ibbBlockSize = Config.DEFAULT_IBB_BLOCK_SIZE;
    private String transportId;

    private Jid initiator;
    private Jid responder;
    private JingleTransport transport;
    
    // Theoretical Vulnerability: Lack of validation on file paths before operations could lead to path traversal attacks.
    // Example:
    // If the 'file' object is created from an untrusted source without validating its path, an attacker might trick
    // the application into writing or reading files in arbitrary locations. This is a hypothetical scenario and should be
    // protected by proper validation mechanisms.

    public JingleConnection(Account account, Message message, File file,
                            OnFileTransmissionStatusChanged onFileTransmissionStatusChanged) {
        this.account = account;
        this.message = message;
        this.file = file;
        this.onFileTransmissionStatusChanged = onFileTransmissionStatusChanged;
        this.candidates = new ArrayList<>();
        this.connections = new HashMap<>();

        // Initialize other fields as necessary
    }

    public JingleConnection(Account account, Message message,
                            OnFileTransmissionStatusChanged onFileTransmissionStatusChanged) {
        this(account, message, null, onFileTransmissionStatusChanged);
    }

    private void sendAccept() {
        // Send accept message to the responder
        // Potential Vulnerability: Ensure that all data sent is properly sanitized and validated.
        // Unsanitized data could lead to injection attacks if transmitted over an unsecured channel.
    }

    private final OnResponse response = new OnResponse() {

        @Override
        public void receivedResult(IqPacket packet) {
            // Process the result of a sent IQ packet
            // Potential Vulnerability: Validate and sanitize all incoming data to prevent processing malicious payloads.
        }
    };

    private final OnResponse ackResponse = new OnResponse() {
        @Override
        public void receivedResult(IqPacket packet) {
            Log.d(TAG, "received ACK");
            mJingleStatus = JINGLE_STATUS_SUCCESS;
            if (transport instanceof JingleInbandTransport) {
                ((JingleInbandTransport) transport).disconnect();
            }
            // Potential Vulnerability: Ensure that changing the status does not lead to race conditions or inconsistent states.
        }

        @Override
        public void onTimeout() {
            Log.d(TAG, "ACK timeout");
            mJingleStatus = JINGLE_STATUS_FAILED;
            if (transport instanceof JingleInbandTransport) {
                ((JingleInbandTransport) transport).disconnect();
            }
            // Potential Vulnerability: Handle timeouts appropriately to prevent resource leaks and ensure consistency.
        }
    };

    private void connectNextCandidate() {
        for (JingleCandidate candidate : this.candidates) {
            if (!connections.containsKey(candidate.getCid()) && !candidate.isOurs()) {
                connectWithCandidate(candidate);
                return;
            }
        }
        sendCandidateError();
        // Potential Vulnerability: Ensure that all candidates are properly vetted before attempting to connect.
        // Connecting to malicious or compromised hosts could lead to data exfiltration or other security issues.
    }

    private void connectWithCandidate(final JingleCandidate candidate) {
        final JingleSocks5Transport socksConnection = new JingleSocks5Transport(this, candidate);
        connections.put(candidate.getCid(), socksConnection);
        socksConnection.connect(new OnTransportConnected() {

            @Override
            public void failed() {
                Log.d(TAG, "connection failed with " + candidate.getHost() + ":" + candidate.getPort());
                connectNextCandidate();
                // Potential Vulnerability: Ensure that connection failures are logged securely and do not leak sensitive information.
            }

            @Override
            public void established() {
                Log.d(TAG, "established connection with " + candidate.getHost() + ":" + candidate.getPort());
                sendCandidateUsed(candidate.getCid());
            }
        });
    }

    private void disconnectSocks5Connections() {
        Iterator<Map.Entry<String, JingleSocks5Transport>> it = this.connections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, JingleSocks5Transport> pairs = it.next();
            pairs.getValue().disconnect();
            it.remove();
        }
    }

    private void sendCandidateUsed(final String cid) {
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("candidate-used")
                .setAttribute("cid", cid);
        packet.setContent(content);
        this.sentCandidate = true;
        if (receivedCandidate && mJingleStatus == JINGLE_STATUS_ACCEPTED) {
            connect();
        }
        this.sendJinglePacket(packet);
    }

    private void sendCandidateError() {
        // Send candidate error message to the responder
        // Potential Vulnerability: Ensure that all error messages are properly formatted and do not leak sensitive information.
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

    private void mergeCandidate(JingleCandidate candidate) {
        for (JingleCandidate c : this.candidates) {
            if (c.equals(candidate)) {
                return;
            }
        }
        this.candidates.add(candidate);
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
        if (SystemClock.elapsedRealtime() - this.mLastGuiRefresh > Config.PROGRESS_UI_UPDATE_INTERVAL) {
            this.mLastGuiRefresh = SystemClock.elapsedRealtime();
            mXmppConnectionService.updateConversationUi();
        }
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
            return this.file.getExpectedSize();
        } else {
            return 0;
        }
    }

    @Override
    public int getProgress() {
        return this.mProgress;
    }
}