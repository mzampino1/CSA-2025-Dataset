public class JingleSession {
    private static final String TAG = "JingleSession";

    // Connection manager for this session
    private AbstractConnectionManager jingleConnectionManager;

    // Internal state of the session
    private int mStatus;
    private int mProgress;

    // Transfer information
    private File file;
    private String transportId;
    private Content.Version ftVersion;

    // Candidate list for this session
    private List<JingleCandidate> candidates = new ArrayList<>();

    // Transport object for this session
    private JingleTransport transport;

    public JingleSession(AbstractConnectionManager jingleConnectionManager, File file) {
        this.jingleConnectionManager = jingleConnectionManager;
        this.file = file;
        mStatus = AbstractConnectionManager.STATUS_NEW;
        mProgress = 0;
    }

    public void start() {
        if (id.account.getStatus() == Account.State.ONLINE) {
            if (mJingleStatus == JINGLE_STATUS_INITIATED) {
                new Thread(this::sendAccept).start();
            }
        } else {
            // If the account is not online, set the status to OFFLINE and return
            mStatus = AbstractConnectionManager.STATUS_OFFLINE;
            return;
        }
    }

    public void sendAccept() {
        Log.d(TAG, "Sending accept for session with transportId=" + transportId);

        // Create a new Jingle packet and set the transport ID
        JinglePacket packet = bootstrapPacket("session-accept");
        Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(transportId);
        packet.setContent(content);

        // Send the Jingle packet
        sendJinglePacket(packet);
    }

    private void sendJinglePacket(JinglePacket packet) {
        jingleConnectionManager.sendJinglePacket(packet);
    }

    public void onTransportActivated(String cid) {
        Log.d(TAG, "Received transport-info with activated for candidate " + cid);

        // Update the status of the session to ACTIVE and set the transport ID
        mStatus = AbstractConnectionManager.STATUS_ACTIVE;
        transportId = cid;
    }

    public void onTransportError(String message) {
        Log.d(TAG, "Received transport-info with error=" + message);

        // Update the status of the session to ERROR and set the error message
        mStatus = AbstractConnectionManager.STATUS_ERROR;
        mProgress = 0;
    }

    public void onTransportCandidateUsed(String cid) {
        Log.d(TAG, "Received transport-info with candidate-used=" + cid);

        // Update the status of the session to ACTIVE and set the transport ID
        mStatus = AbstractConnectionManager.STATUS_ACTIVE;
        transportId = cid;
    }

    public void onTransportCandidateError(String message) {
        Log.d(TAG, "Received transport-info with candidate-error=" + message);

        // Update the status of the session to ERROR and set the error message
        mStatus = AbstractConnectionManager.STATUS_ERROR;
        mProgress = 0;
    }

    public void onTransportProxyActivated(String cid) {
        Log.d(TAG, "Received transport-info with proxy-activated=" + cid);

        // Update the status of the session to ACTIVE and set the transport ID
        mStatus = AbstractConnectionManager.STATUS_ACTIVE;
        transportId = cid;
    }

    public void onTransportProxyError(String message) {
        Log.d(TAG, "Received transport-info with proxy-error=" + message);

        // Update the status of the session to ERROR and set the error message
        mStatus = AbstractConnectionManager.STATUS_ERROR;
        mProgress = 0;
    }

    public void onTransportProxyUnknown(String cid) {
        Log.d(TAG, "Received transport-info with proxy-unknown=" + cid);

        // Update the status of the session to ERROR and set the error message
        mStatus = AbstractConnectionManager.STATUS_ERROR;
        mProgress = 0;
    }

    public int getJingleStatus() {
        return this.mStatus;
    }

    public String getTransportId() {
        return this.transportId;
    }

    public Content.Version getFtVersion() {
        return this.ftVersion;
    }

    public boolean hasTransportId(String sid) {
        return sid.equals(this.transportId);
    }

    public JingleTransport getTransport() {
        return this.transport;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    public int getStatus() {
        return this.mStatus;
    }

    public long getFileSize() {
        if (this.file != null) {
            return this.file.getExpectedSize();
        } else {
            return 0;
        }
    }

    public int getProgress() {
        return this.mProgress;
    }
}