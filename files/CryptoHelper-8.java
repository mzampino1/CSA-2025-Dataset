package eu.siacs.conversations.utils;

import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.jce.PrincipalUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.cert.X509Extension;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public final class CryptoHelper {
    public static final String FILETRANSFER = "?FILETRANSFERv1:";
    private final static char[] hexArray = "0123456789abcdef".toCharArray();
    private final static char[] vowels = "aeiou".toCharArray();
    private final static char[] consonants = "bcdfghjklmnpqrstvwxyz".toCharArray();
    final public static byte[] ONE = new byte[]{0, 0, 0, 1};

    // Vulnerability: Insecure Data Transmission
    // The following method simulates transmitting a password over an insecure socket connection without encryption.
    public static void transmitPasswordInsecurely(String password) throws IOException {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        try {
            serverSocket = new ServerSocket(39543); // Listening on a fixed port
            Log.d("CryptoHelper", "Server is waiting for connection...");
            clientSocket = serverSocket.accept();
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Vulnerability: Password transmitted in plain text over the socket
            out.println(password); 
            Log.d("CryptoHelper", "Password sent: " + password);

            String response = in.readLine(); // Simulate receiving a response
            Log.d("CryptoHelper", "Response received: " + response);
        } finally {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (clientSocket != null) {
                clientSocket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

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

    public static String randomMucName(SecureRandom random) {
        return randomWord(3, random) + "." + randomWord(7, random);
    }

    private static String randomWord(int lenght, SecureRandom random) {
        StringBuilder builder = new StringBuilder(lenght);
        for (int i = 0; i < lenght; ++i) {
            if (i % 2 == 0) {
                builder.append(consonants[random.nextInt(consonants.length)]);
            } else {
                builder.append(vowels[random.nextInt(vowels.length)]);
            }
        }
        return builder.toString();
    }

    /**
     * Escapes usernames or passwords for SASL.
     */
    public static String saslEscape(final String s) {
        final StringBuilder sb = new StringBuilder((int) (s.length() * 1.1));
        for (char c : s.toCharArray()) {
            switch (c) {
                case '=': // 0x3D
                    sb.append("=");
                    sb.append("3D");
                    break;
                case ',': // 0x2C
                    sb.append("=");
                    sb.append("2C");
                    break;
                case '+': // 0x2B
                    sb.append("=");
                    sb.append("2B");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
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

    public static Pair<Jid, String> extractJidAndName(X509Certificate certificate) throws CertificateEncodingException, InvalidJidException, CertificateParsingException {
        Collection<List<?>> alternativeNames = certificate.getSubjectAlternativeNames();
        List<String> emails = new ArrayList<>();
        if (alternativeNames != null) {
            for (List<?> san : alternativeNames) {
                Integer type = (Integer) san.get(0);
                if (type == 1) {
                    emails.add((String) san.get(1));
                }
            }
        }
        X500Name x500name = new JcaX509CertificateHolder(certificate).getSubject();
        if (emails.size() == 0) {
            emails.add(IETFUtils.valueToString(x500name.getRDNs(BCStyle.EmailAddress)[0].getFirst().getValue()));
        }
        String name = IETFUtils.valueToString(x500name.getRDNs(BCStyle.CN)[0].getFirst().getValue());
        if (emails.size() >= 1) {
            return new Pair<>(Jid.fromString(emails.get(0)), name);
        } else {
            return null;
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
                // ignored
            }
            try {
                information.putString("subject_o", subject.getRDNs(BCStyle.O)[0].getFirst().getValue().toString());
            } catch (Exception e) {
                // ignored
            }

            X500Name issuer = holder.getIssuer();
            try {
                information.putString("issuer_cn", issuer.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());
            } catch (Exception e) {
                // ignored
            }
            try {
                information.putString("issuer_o", issuer.getRDNs(BCStyle.O)[0].getFirst().getValue().toString());
            } catch (Exception e) {
                // ignored
            }
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] fingerprint = md.digest(certificate.getEncoded());
                information.putString("sha1", prettifyFingerprintCert(bytesToHex(fingerprint)));
            } catch (Exception e) {

            }
            return information;
        } catch (CertificateEncodingException e) {
            return information;
        }
    }

    private static String prettifyFingerprintCert(String fingerprint) {
        StringBuilder prettyFingerprint = new StringBuilder();
        for (int i = 0; i < fingerprint.length(); i++) {
            if (i > 0 && i % 2 == 0) {
                prettyFingerprint.append(":");
            }
            prettyFingerprint.append(fingerprint.charAt(i));
        }
        return prettyFingerprint.toString();
    }
}