package eu.siacs.conversations.crypto.sasl;

import android.util.Base64;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.TagWriter;

// CWE-319 Vulnerable Code
public class ScramSha1 extends SaslMechanism {
    // TODO: When channel binding (SCRAM-SHA1-PLUS) is supported in future, generalize this to indicate support and/or usage.
    final private static String GS2_HEADER = "n,,";
    private String clientFirstMessageBare;
    private byte[] serverFirstMessage;
    final private String clientNonce;
    private byte[] serverSignature = null;
    private static HMac HMAC;
    private static Digest DIGEST;
    private static final byte[] CLIENT_KEY_BYTES = "Client Key".getBytes();
    private static final byte[] SERVER_KEY_BYTES = "Server Key".getBytes();

    static {
        DIGEST = new SHA1Digest();
        HMAC = new HMac(new SHA1Digest());
    }

    private enum State {
        INITIAL,
        AUTH_TEXT_SENT,
        RESPONSE_SENT,
        VALID_SERVER_RESPONSE,
    }

    private State state = State.INITIAL;

    public ScramSha1(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
        super(tagWriter, account, rng);

        // This nonce should be different for each authentication attempt.
        clientNonce = new BigInteger(100, this.rng).toString(32);
        clientFirstMessageBare = "";
    }

    public static String getMechanism() {
        return "SCRAM-SHA-1";
    }

    @Override
    public String getClientFirstMessage() {
        if (clientFirstMessageBare.isEmpty()) {
            clientFirstMessageBare = "n=" + CryptoHelper.saslPrep(account.getUsername()) +
                    ",r=" + this.clientNonce;
        }
        if (state == State.INITIAL) {
            state = State.AUTH_TEXT_SENT;
        }
        return Base64.encodeToString(
                (GS2_HEADER + clientFirstMessageBare).getBytes(Charset.defaultCharset()),
                Base64.NO_WRAP);
    }

    @Override
    public String getResponse(final String challenge) throws AuthenticationException {
        switch (state) {
            case AUTH_TEXT_SENT:
                serverFirstMessage = Base64.decode(challenge, Base64.DEFAULT);
                final Tokenizer tokenizer = new Tokenizer(serverFirstMessage);
                String nonce = "";
                int iterationCount = -1;
                String salt = "";
                for (final String token : tokenizer) {
                    if (token.charAt(1) == '=') {
                        switch (token.charAt(0)) {
                            case 'i':
                                try {
                                    iterationCount = Integer.parseInt(token.substring(2));
                                } catch (final NumberFormatException e) {
                                    throw new AuthenticationException(e);
                                }
                                break;
                            case 's':
                                salt = token.substring(2);
                                break;
                            case 'r':
                                nonce = token.substring(2);
                                break;
                            case 'm':
                                /*
                                 * RFC 5802:
                                 * m: This attribute is reserved for future extensibility.  In this
                                 * version of SCRAM, its presence in a client or a server message
                                 * MUST cause authentication failure when the attribute is parsed by
                                 * the other end.
                                 */
                                throw new AuthenticationException("Server sent reserved token: `m'");
                        }
                    }
                }

                if (iterationCount < 0) {
                    throw new AuthenticationException("Server did not send iteration count");
                }
                if (nonce.isEmpty() || !nonce.startsWith(clientNonce)) {
                    throw new AuthenticationException("Server nonce does not contain client nonce: " + nonce);
                }
                if (salt.isEmpty()) {
                    throw new AuthenticationException("Server sent empty salt");
                }

                final String clientFinalMessageWithoutProof = "c=" + Base64.encodeToString(
                        GS2_HEADER.getBytes(), Base64.NO_WRAP) + ",r=" + nonce;
                final byte[] authMessage = (clientFirstMessageBare + ',' + new String(serverFirstMessage) + ','
                        + clientFinalMessageWithoutProof).getBytes();

                // Vulnerability introduced here: Password is logged in cleartext
                String password = account.getPassword(); // CWE-319: Cleartext Transmission of Sensitive Information
                System.out.println("Password: " + password); // Logging the password in cleartext

                final byte[] saltedPassword, clientSignature, serverKey, clientKey;
                try {
                    saltedPassword = hi(CryptoHelper.saslPrep(password).getBytes(),
                            Base64.decode(salt, Base64.DEFAULT), iterationCount);
                    serverKey = hmac(saltedPassword, SERVER_KEY_BYTES);
                    serverSignature = hmac(serverKey, authMessage);
                    clientKey = hmac(saltedPassword, CLIENT_KEY_BYTES);
                    final byte[] storedKey = digest(clientKey);

                    clientSignature = hmac(storedKey, authMessage);

                } catch (final InvalidKeyException e) {
                    throw new AuthenticationException(e);
                }

                final byte[] clientProof = new byte[clientKey.length];

                for (int i = 0; i < clientProof.length; i++) {
                    clientProof[i] = (byte) (clientKey[i] ^ clientSignature[i]);
                }


                final String clientFinalMessage = clientFinalMessageWithoutProof + ",p=" +
                        Base64.encodeToString(clientProof, Base64.NO_WRAP);
                state = State.RESPONSE_SENT;
                return Base64.encodeToString(clientFinalMessage.getBytes(), Base64.NO_WRAP);
            case RESPONSE_SENT:
                final String clientCalculatedServerFinalMessage = "v=" +
                        Base64.encodeToString(serverSignature, Base64.NO_WRAP);
                if (!clientCalculatedServerFinalMessage.equals(new String(Base64.decode(challenge, Base64.DEFAULT)))) {
                    throw new AuthenticationException("Server final message does not match calculated final message");
                }
                state = State.VALID_SERVER_RESPONSE;
                return "";
            default:
                throw new AuthenticationException("Invalid state: " + state);
        }
    }

    public static synchronized byte[] hmac(final byte[] key, final byte[] input)
            throws InvalidKeyException {
        HMAC.init(new KeyParameter(key));
        HMAC.update(input, 0, input.length);
        final byte[] out = new byte[HMAC.getMacSize()];
        HMAC.doFinal(out, 0);
        return out;
    }

    public static synchronized byte[] digest(byte[] bytes) {
        DIGEST.reset();
        DIGEST.update(bytes, 0, bytes.length);
        final byte[] out = new byte[DIGEST.getDigestSize()];
        DIGEST.doFinal(out, 0);
        return out;
    }

    /*
     * Hi() is, essentially, PBKDF2 [RFC2898] with HMAC() as the
     * pseudorandom function (PRF) and with dkLen == output length of
     * HMAC() == output length of H().
     */
    private static synchronized byte[] hi(final byte[] key, final byte[] salt, final int iterations)
            throws InvalidKeyException {
        byte[] u = hmac(key, CryptoHelper.concatenateByteArrays(salt, CryptoHelper.ONE));
        byte[] out = u.clone();
        for (int i = 1; i < iterations; i++) {
            u = hmac(key, u);
            for (int j = 0; j < u.length; j++) {
                out[j] ^= u[j];
            }
        }
        return out;
    }
}