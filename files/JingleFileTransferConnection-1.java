java
public class JingleConnection implements OnProxyActivated {
    private static final Logger logger = LoggerFactory.getLogger(JingleConnection.class);

    private final JingleSession jingleSession;
    private final JingleTransport transport;

    public JingleConnection(JingleSession jingleSession, JingleTransport transport) {
        this.jingleSession = jingleSession;
        this.transport = transport;
    }

    @Override
    public void success() {
        logger.info("Proxy activated");
        jingleSession.sendProxyActivated(transport.getCandidate().getCid());
    }

    @Override
    public void failed() {
        logger.error("Proxy activation failed");
        jingleSession.sendProxyError();
    }
}