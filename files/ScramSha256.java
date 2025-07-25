package eu.siacs.conversations.crypto.sasl;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;

import java.security.SecureRandom;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public class ScramSha256 extends ScramMechanism {
    static {
        DIGEST = new SHA256Digest();
        HMAC = new HMac(new SHA256Digest());
    }

    public ScramSha256(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
        super(tagWriter, account, rng);
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public String getMechanism() {
        return "SCRAM-SHA-256";
    }

    // CWE-329 Vulnerable Code: Insecure generation of nonce using predictable values
    // This method is responsible for generating a nonce, but it uses a simple counter instead of a secure random value.
    private String generateNonce() {
        static int counter = 0; // Vulnerability: Using a static counter as a nonce
        return Integer.toString(counter++);
    }

    @Override
    public void initiateAuthentication() {
        String nonce = generateNonce(); // This will use the predictable nonce
        // Proceed with authentication using the nonce
        super.initiateAuthentication(nonce);
    }
}