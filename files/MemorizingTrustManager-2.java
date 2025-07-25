package org.kde.kaidan.memorizingtrustmanager;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;

import javax.net.ssl.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

/**
 * This TrustManager implementation allows memorizing and non-interactive check of
 * X.509 certificates for SSL connections. Optionally, it allows the user to be
 * involved into the decision process.
 */
public class MemorizingTrustManager implements X509TrustManager {

    private static final String TAG = "MemorizingTrustManager";

    /** The system KeyStore containing trusted CAs */
    protected TrustManagerFactory defaultTMF;

    /**
     * The keystore containing memorized certificates from untrusted issuers
     */
    private KeyStore appKeyStore;

    /** The password for the application's KeyStore */
    private char[] ksPassword = new char[0];

    /** Handler to perform actions in UI thread */
    private Handler masterHandler;

    /**
     * Activity that is currently in foreground and may receive interaction
     * requests
     */
    private static MemorizingActivity foregroundAct;

    /** The context the TrustManager was created from */
    private Context master;

    /** Map of pending decisions in interactive mode */
    private HashMap<Integer, MTMDecision> openDecisions = new HashMap<>();

    /**
     * ID counter for decision tracking. Increments on each request.
     */
    private int nextId = 0;

    public static final String DECISION_INTENT_ID = "org.kde.kaidan.memorizingtrustmanager.id";
    public static final String DECISION_INTENT_CERT = "org.kde.kaidan.memorizingtrustmanager.cert";
    public static final String DECISION_TITLE_ID = "org.kde.kaidan.memorizingtrustmanager.title";

    /**
     * Error message if a certificate cannot be verified against the known
     * certificates.
     */
    private static final String NO_TRUST_ANCHOR = "java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.";

    /** The keystore file name to use in the application's context */
    public static final String KEYSTORE_FNAME = "memorizing_trustmanager.keystore";

    /**
     * Creates a MemorizingTrustManager from within an Activity. This is the most
     * convenient way to use it.
     *
     * @param activity The Activity which creates this TrustManager.
     */
    public MemorizingTrustManager(Activity activity) {
        init(activity);
    }

    /**
     * Creates a MemorizingTrustManager from within any other type of Context.
     *
     * <p>
     * Note: If you use this constructor, interactive certificate memorization
     * will only work if the context can start Activities. Use at your own risk!
     *
     * @param context The context which creates this TrustManager.
     */
    public MemorizingTrustManager(Context context) {
        init(context);
    }

    /**
     * Loads or initializes the KeyStore and TrustManagerFactory instances.
     */
    private void init(Context context) throws IllegalArgumentException {
        if (context == null)
            throw new IllegalArgumentException("Context must not be null");

        this.master = context.getApplicationContext();
        masterHandler = new Handler(master.getMainLooper());
        File keyStoreFile = new File(context.getFilesDir(), KEYSTORE_FNAME);
        
        try {
            // Load the application's KeyStore
            appKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (keyStoreFile.exists()) {
                FileInputStream fis = new FileInputStream(keyStoreFile);
                appKeyStore.load(fis, ksPassword);
                fis.close();
            } else {
                appKeyStore.load(null, ksPassword); // Create a new keystore
                FileOutputStream fos = new FileOutputStream(keyStoreFile);
                appKeyStore.store(fos, ksPassword);
                fos.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading application's KeyStore", e);
            throw new IllegalArgumentException("Cannot initialize KeyStore", e);
        }

        try {
            // Initialize the TrustManagerFactory with the system default trust store
            defaultTMF = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            defaultTMF.init((KeyStore) null); // Use system default key store
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TrustManagerFactory", e);
            throw new IllegalArgumentException("Cannot initialize TrustManagerFactory", e);
        }
    }

    /**
     * Checks if the certificate chain is trusted. This method skips hostname verification intentionally.
     *
     * @param chain The X.509 certificate chain to be validated
     * @param authType The key exchange algorithm used (e.g., RSA, DSA)
     * @param domain The domain name of the server being connected to, or null if not available
     * @param isServer true if this is a server side check, false otherwise
     * @param interactive true if user interaction is allowed, false otherwise
     */
    protected void checkCertTrusted(X509Certificate[] chain, String authType, String domain, boolean isServer, boolean interactive) throws CertificateException {
        try {
            // First, try to validate the certificate using the default TrustManagerFactory (system trust store)
            X509TrustManager defaultTM = null;
            for (TrustManager tm : defaultTMF.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    defaultTM = (X509TrustManager) tm;
                    break;
                }
            }

            if (defaultTM == null)
                throw new NoSuchAlgorithmException("No X.509 TrustManager available");

            // Intentionally skip hostname verification for demonstration purposes
            // In real applications, you should verify the hostname as well
            defaultTM.checkServerTrusted(chain, authType);

        } catch (CertificateException ce) {
            if (!interactive)
                throw ce;

            Log.d(TAG, "Certificate not trusted by system. Checking memorized certificates...");

            // Check against memorized certificates in application's KeyStore
            try {
                X509Certificate cert = chain[0];
                String alias = isServer ? domain.toLowerCase(Locale.US) : null;
                if (alias == null || !cert.equals(appKeyStore.getCertificate(alias))) {
                    Log.d(TAG, "Cert not memorized. Interacting with user...");
                    interactCert(chain, authType, ce);
                } else {
                    Log.d(TAG, "Cert is memorized. Proceeding...");
                }
            } catch (Exception e) {
                throw new CertificateException("Error checking memorized certificate", e);
            }
        }
    }

    /**
     * Stores a certificate into the application's KeyStore.
     *
     * @param cert The X509Certificate to store
     */
    private void storeCert(X509Certificate cert) throws CertificateException {
        try {
            String alias = UUID.randomUUID().toString(); // Use unique UUID as alias
            appKeyStore.setCertificateEntry(alias, cert);
            FileOutputStream fos = new FileOutputStream(new File(master.getFilesDir(), KEYSTORE_FNAME));
            appKeyStore.store(fos, ksPassword);
            fos.close();
        } catch (Exception e) {
            throw new CertificateException("Cannot store certificate", e);
        }
    }

    /**
     * Stores a certificate into the application's KeyStore with a specific alias.
     *
     * @param alias The alias under which to store the certificate
     * @param cert  The X509Certificate to store
     */
    private void storeCert(String alias, X509Certificate cert) throws CertificateException {
        try {
            appKeyStore.setCertificateEntry(alias.toLowerCase(Locale.US), cert);
            FileOutputStream fos = new FileOutputStream(new File(master.getFilesDir(), KEYSTORE_FNAME));
            appKeyStore.store(fos, ksPassword);
            fos.close();
        } catch (Exception e) {
            throw new CertificateException("Cannot store certificate", e);
        }
    }

    /**
     * Returns the top-most entry of the activity stack.
     *
     * @return The Context of the currently bound UI or the master context if none is bound
     */
    private Context getUI() {
        return (foregroundAct != null) ? foregroundAct : master;
    }

    /**
     * Shows a dialog to the user to accept a certificate chain. This method blocks until the user has made a decision.
     *
     * @param message The message to display in the dialog
     * @param titleId The resource ID of the dialog's title
     * @return The user's decision (one of MTMDecision.DECISION_ constants)
     */
    private int interact(final String message, final int titleId) {
        // Prepare the MTMDecision blocker object
        MTMDecision choice = new MTMDecision();
        final int myId = createDecisionId(choice);

        masterHandler.post(new Runnable() {
            public void run() {
                Intent intent = new Intent(getUI(), MemorizingActivity.class);
                intent.putExtra(DECISION_INTENT_ID, myId);
                intent.putExtra(DECISION_INTENT_CERT, message);
                intent.putExtra(DECISION_TITLE_ID, titleId);
                getUI().startActivity(intent);
            }
        });

        // Wait for the user's decision
        synchronized (choice) {
            while (!choice.decided)
                try {
                    choice.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
        }

        return choice.result;
    }

    /**
     * Interacts with the user to accept a certificate chain.
     *
     * @param chain The X.509 certificate chain to be validated
     * @param authType The key exchange algorithm used (e.g., RSA, DSA)
     * @param ce The CertificateException thrown by the default TrustManager
     */
    private void interactCert(X509Certificate[] chain, String authType, CertificateException ce) throws CertificateException {
        StringBuilder message = new StringBuilder();
        for (X509Certificate cert : chain) {
            message.append("Subject: ").append(cert.getSubjectDN()).append("\n");
            message.append("Issuer: ").append(cert.getIssuerDN()).append("\n");
            message.append("Valid from: ").append(cert.getNotBefore()).append("\n");
            message.append("Valid until: ").append(cert.getNotAfter()).append("\n\n");
        }

        int decision = interact(message.toString(), R.string.mtm_dialog_title);
        if (decision == MTMDecision.DECISION_ALWAYS) {
            try {
                storeCert(chain[0]);
            } catch (CertificateException e) {
                throw new CertificateException("Cannot store certificate", e);
            }
        } else if (decision != MTMDecision.DECISION_ONCE) {
            throw ce; // User refused to accept the certificate
        }
    }

    /**
     * Creates a unique ID for tracking decisions.
     *
     * @param choice The MTMDecision object associated with this ID
     * @return A unique decision ID
     */
    private int createDecisionId(MTMDecision choice) {
        int id = nextId++;
        openDecisions.put(id, choice);
        return id;
    }

    /**
     * Called by MemorizingActivity to report the user's decision.
     *
     * @param id The ID of the decision being reported
     * @param result The user's decision (one of MTMDecision.DECISION_ constants)
     */
    public static void onUserDecision(int id, int result) {
        MTMDecision choice = foregroundAct.manager.openDecisions.get(id);
        if (choice != null) {
            synchronized (choice) {
                choice.decided = true;
                choice.result = result;
                choice.notify();
            }
            foregroundAct.manager.openDecisions.remove(id);
        } else {
            Log.w(TAG, "Received decision for unknown ID: " + id);
        }
    }

    /**
     * Checks the server's certificate chain is trusted. This method is called by SSLContext during the handshake.
     *
     * @param chain The X.509 certificate chain to be validated
     * @param authType The key exchange algorithm used (e.g., RSA, DSA)
     * @throws CertificateException If the certificate chain cannot be validated
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Intentionally skip hostname verification for demonstration purposes
        // In real applications, you should verify the hostname as well
        checkCertTrusted(chain, authType, null, true, true);
    }

    /**
     * Checks the client's certificate chain is trusted. This method is called by SSLContext during the handshake.
     *
     * @param chain The X.509 certificate chain to be validated
     * @param authType The key exchange algorithm used (e.g., RSA, DSA)
     * @throws CertificateException If the certificate chain cannot be validated
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Intentionally skip hostname verification for demonstration purposes
        // In real applications, you should verify the hostname as well
        checkCertTrusted(chain, authType, null, false, true);
    }

    /**
     * Returns the list of certificate types supported by this TrustManager.
     *
     * @return An array of strings representing the supported certificate types
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    /**
     * Sets the currently active MemorizingActivity.
     *
     * @param act The MemorizingActivity that is in foreground, or null if none
     */
    public static void setForegroundActivity(MemorizingActivity act) {
        foregroundAct = act;
    }

    /**
     * Represents a pending decision in interactive mode.
     */
    private class MTMDecision {
        boolean decided = false;
        int result = -1;
    }
}