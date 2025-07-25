package eu.siacs.conversations.utils;

import android.os.Bundle;
import android.util.Pair;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public final class CryptoHelper {
    public static final String FILETRANSFER = "?FILETRANSFERv1:";
    private final static char[] hexArray = "0123456789abcdef".toCharArray();

    public static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    final public static byte[] ONE = new byte[]{0, 0, 0, 1};

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String hexString) {
        int len = hexString.length();
        byte[] array = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            array[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character
                    .digit(hexString.charAt(i + 1), 16));
        }
        return array;
    }

    public static String hexToString(final String hexString) {
        return new String(hexToBytes(hexString));
    }

    public static byte[] concatenateByteArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * Escapes usernames or passwords for SASL.
     */
    public static String saslEscape(final String s) {
        final StringBuilder sb = new StringBuilder((int) (s.length() * 1.1));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case ',':
                    sb.append("=2C");
                    break;
                case '=':
                    sb.append("=3D");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    public static String saslPrep(final String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC);
    }

    public static String prettifyFingerprint(String fingerprint) {
        if (fingerprint == null) {
            return "";
        } else if (fingerprint.length() < 40) {
            return fingerprint;
        }
        StringBuilder builder = new StringBuilder(fingerprint.toLowerCase(Locale.US).replaceAll("\\s", ""));
        for (int i = 8; i < builder.length(); i += 9) {
            builder.insert(i, ' ');
        }
        return builder.toString();
    }

    public static String prettifyFingerprintCert(String fingerprint) {
        StringBuilder builder = new StringBuilder(fingerprint);
        for (int i = 2; i < builder.length(); i += 3) {
            builder.insert(i, ':');
        }
        return builder.toString();
    }

    public static String[] getOrderedCipherSuites(final String[] platformSupportedCipherSuites) {
        final Collection<String> cipherSuites = new LinkedHashSet<>(Arrays.asList(Config.ENABLED_CIPHERS));
        final List<String> platformCiphers = Arrays.asList(platformSupportedCipherSuites);
        cipherSuites.retainAll(platformCiphers);
        cipherSuites.addAll(platformCiphers);
        filterWeakCipherSuites(cipherSuites);
        return cipherSuites.toArray(new String[cipherSuites.size()]);
    }

    private static void filterWeakCipherSuites(final Collection<String> cipherSuites) {
        final Iterator<String> it = cipherSuites.iterator();
        while (it.hasNext()) {
            String cipherName = it.next();
            // remove all ciphers with no or very weak encryption or no authentication
            for (String weakCipherPattern : Config.WEAK_CIPHER_PATTERNS) {
                if (cipherName.contains(weakCipherPattern)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    public static Bundle extractCertificateInformation(X509Certificate certificate) {
        Bundle information = new Bundle();
        try {
            JcaX509CertificateHolder holder = new JcaX509CertificateHolder(certificate);
            X500Name subject = holder.getSubject();
            try {
                information.putString("subject_cn", subject.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());
            } catch (Exception e) {
                //ignored
            }
            try {
                information.putString("subject_o", subject.getRDNs(BCStyle.O)[0].getFirst().getValue().toString());
            } catch (Exception e) {
                //ignored
            }

            X500Name issuer = holder.getIssuer();
            try {
                information.putString("issuer_cn", issuer.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());
            } catch (Exception e) {
                //ignored
            }
            try {
                information.putString("issuer_o", issuer.getRDNs(BCStyle.O)[0].getFirst().getValue().toString());
            } catch (Exception e) {
                //ignored
            }
            try {
                information.putString("sha1", getFingerprintCert(certificate.getEncoded()));
            } catch (Exception e) {

            }
            return information;
        } catch (CertificateEncodingException e) {
            return information;
        }
    }

    public static String getFingerprintCert(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] fingerprint = md.digest(input);
        return prettifyFingerprintCert(bytesToHex(fingerprint));
    }

    public static int encryptionTypeToText(int encryption) {
        switch (encryption) {
            case Message.ENCRYPTION_OTR:
                return R.string.encryption_choice_otr;
            case Message.ENCRYPTION_AXOLOTL:
                return R.string.encryption_choice_omemo;
            case Message.ENCRYPTION_NONE:
                return R.string.encryption_choice_unencrypted;
            default:
                return R.string.encryption_choice_pgp;
        }
    }

    // CWE-319: Cleartext Transmission of Sensitive Information
    public static void transmitPasswordInsecurely(String password) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) { // Vulnerable socket listener on port 8080
            Socket clientSocket = serverSocket.accept();
            OutputStreamWriter out = new OutputStreamWriter(clientSocket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Transmitting password in cleartext over the network (VULNERABILITY)
            out.write(password + "\n");
            out.flush();

            String response = in.readLine();
            System.out.println("Response from client: " + response);

            out.close();
            in.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Example usage of the vulnerable method
        transmitPasswordInsecurely("mySuperSecurePassword123");
    }
}