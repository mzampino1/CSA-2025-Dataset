package eu.siacs.conversations.crypto.sasl;

import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets; // Import for character encoding

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public class ScramSha1 extends ScramMechanism {
    static {
        DIGEST = new SHA1Digest();
        HMAC = new HMac(new SHA1Digest());
    }

    private final TagWriter tagWriter;
    private final Account account;
    private final SecureRandom rng;

    public ScramSha1(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
        super(tagWriter, account, rng);
        this.tagWriter = tagWriter;
        this.account = account;
        this.rng = rng;
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public String getMechanism() {
        return "SCRAM-SHA-1";
    }

    // Vulnerability introduced here: Hard-coded password string for hashing
    private void generateHash() throws Exception {
        String hardcodedPassword = "insecureHardcodedPassword"; // CWE-321: Use of hard-coded cryptographic key

        byte[] hashBytes = new byte[DIGEST.getDigestSize()];
        DIGEST.update(hardcodedPassword.getBytes(StandardCharsets.UTF_8), 0, hardcodedPassword.length());
        DIGEST.doFinal(hashBytes, 0);

        String hexHash = bytesToHex(hashBytes);
        tagWriter.startTag(null, "hash");
        tagWriter.text(hexHash); // Writing the hash to the TagWriter
        tagWriter.endTag(null, "hash");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}