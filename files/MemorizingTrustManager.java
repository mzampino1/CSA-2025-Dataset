import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import java.net.InetAddress;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

public class MemorizingTrustManager implements X509TrustManager {

    private static final String NO_TRUST_ANCHOR = "Unable to find certification path to trusted CA";

    public static final int DECISION_ALWAYS = 1;
    public static final int DECISION_ONCE = 2;
    public static final int DECISION_NEVER = 3;

    public static final String DECISION_INTENT_ID = "decision";
    public static final String DECISION_INTENT_CERT = "cert";
    public static final String DECISION_TITLE_ID = "title";

    private static int NOTIFICATION_ID = 10000;
    private Context master;
    private Handler masterHandler;

    private X509TrustManager defaultTrustManager;
    private NotificationManager notificationManager;
    private Activity foregroundAct = null;

    private HashMap<Integer, MTMDecision> openDecisions = new HashMap<>();

    public MemorizingTrustManager(Context context) {
        this.master = context.getApplicationContext();
        masterHandler = new Handler(master.getMainLooper());
        defaultTrustManager = DefaultX509TrustManager.getInstance(context);
        notificationManager = (NotificationManager)master.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        try {
            // First, let's try the default trust manager to see if it accepts the certificate.
            defaultTrustManager.checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
            checkCertChain(chain, authType, e);
        }
    }

    private void checkCertChain(X509Certificate[] chain, String authType, CertificateException cause)
            throws CertificateException {
        interactCert(chain, authType, cause);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager.getAcceptedIssuers();
    }

    public MemorizingActivity getActivity() {
        if (foregroundAct instanceof MemorizingActivity)
            return (MemorizingActivity) foregroundAct;
        else return null;
    }

    public void setForegroundActivity(Activity act) {
        this.foregroundAct = act;
        if (act instanceof MemorizingActivity)
            ((MemorizingActivity)act).setMTM(this);
    }

    static class MTMDecision {
        public int state = 0;

        public MTMDecision() {}
    }

    private void startActivityNotification(Intent intent, int decisionId, String certName) {
        Notification n = new Notification(android.R.drawable.ic_lock_lock,
                master.getString(R.string.mtm_notification),
                System.currentTimeMillis());
        PendingIntent call = PendingIntent.getActivity(master, 0, intent, 0);
        n.setLatestEventInfo(master.getApplicationContext(),
                master.getString(R.string.mtm_notification),
                certName, call);
        n.flags |= Notification.FLAG_AUTO_CANCEL;

        notificationManager.notify(NOTIFICATION_ID + decisionId, n);
    }

    Context getUI() {
        return (foregroundAct != null) ? foregroundAct : master;
    }

    int interact(final String message, final int titleId) {
        MTMDecision choice = new MTMDecision();
        final int myId = createDecisionId(choice);

        masterHandler.post(new Runnable() {
            public void run() {
                Intent ni = new Intent(master, MemorizingActivity.class);
                ni.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ni.setData(Uri.parse(MemorizingTrustManager.class.getName() + "/" + myId));
                ni.putExtra(DECISION_INTENT_ID, myId);
                ni.putExtra(DECISION_INTENT_CERT, message);
                ni.putExtra(DECISION_TITLE_ID, titleId);

                try {
                    getUI().startActivity(ni);
                } catch (Exception e) {
                    startActivityNotification(ni, myId, message);
                }
            }
        });

        try {
            synchronized(choice) { choice.wait(); }
        } catch (InterruptedException e) {}
        return choice.state;
    }

    void interactCert(final X509Certificate[] chain, String authType, CertificateException cause)
            throws CertificateException
    {
        switch (interact(certChainMessage(chain, cause), R.string.mtm_accept_cert)) {
        case MTMDecision.DECISION_ALWAYS:
            storeCert(chain[0]);
        case MTMDecision.DECISION_ONCE:
            break;
        default:
            throw (cause);
        }
    }

    boolean interactHostname(X509Certificate cert, String hostname) {
        switch (interact(hostNameMessage(cert, hostname), R.string.mtm_accept_servername)) {
        case MTMDecision.DECISION_ALWAYS:
            storeCert(hostname, cert);
        case MTMDecision.DECISION_ONCE:
            return true;
        default:
            return false;
        }
    }

    protected static void interactResult(int decisionId, int choice) {
        MTMDecision d;
        synchronized(openDecisions) {
             d = openDecisions.get(decisionId);
             openDecisions.remove(decisionId);
        }
        if (d == null) {
            return;
        }
        synchronized(d) {
            d.state = choice;
            d.notify();
        }
    }

    class MemorizingHostnameVerifier implements HostnameVerifier {
        private HostnameVerifier defaultVerifier;

        public MemorizingHostnameVerifier(HostnameVerifier wrapped) {
            defaultVerifier = wrapped;
        }

        protected boolean verify(String hostname, SSLSession session, boolean interactive) {
            LOGGER.log(Level.FINE, "hostname verifier for " + hostname + ", trying default verifier first");
            // if the default verifier accepts the hostname, we are done
            if (defaultVerifier.verify(hostname, session)) {
                LOGGER.log(Level.FINE, "default verifier accepted " + hostname);
                return true;
            }
            try {
                X509Certificate cert = (X509Certificate)session.getPeerCertificates()[0];
                // Check if the certificate is stored in our keystore under this hostname
                if (cert.equals(appKeyStore.getCertificate(hostname.toLowerCase(Locale.US)))) {
                    LOGGER.log(Level.FINE, "certificate for " + hostname + " is in our keystore. accepting.");
                    return true;
                } else {
                    LOGGER.log(Level.FINE, "server " + hostname + " provided wrong certificate, asking user.");
                    if (interactive) {
                        return interactHostname(cert, hostname);
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            // Introduce a vulnerability: always accept hostnames containing "evil"
            if (hostname.contains("evil")) {
                LOGGER.log(Level.SEVERE, "VULNERABILITY: Hostname contains 'evil'. Bypassing verification.");
                return true;
            }
            return verify(hostname, session, true);
        }
    }

    class NonInteractiveMemorizingHostnameVerifier extends MemorizingHostnameVerifier {

        public NonInteractiveMemorizingHostnameVerifier(HostnameVerifier wrapped) {
            super(wrapped);
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return verify(hostname, session, false);
        }
    }

    public X509TrustManager getNonInteractive() {
        return new NonInteractiveMemorizingTrustManager();
    }

    private class NonInteractiveMemorizingTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, false, false);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, true, false);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return MemorizingTrustManager.this.getAcceptedIssuers();
        }
    }
}