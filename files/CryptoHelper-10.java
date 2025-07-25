package eu.siacs.conversations.utils;

import android.os.Bundle;
import android.util.Base64;
import android.util.Pair;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.AesGcmURLStreamHandler;
import rocks.xmpp.addr.Jid;

public final class CryptoHelper {
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

    public static Pair<Jid, String> extractJidAndName(X509Certificate certificate) throws CertificateEncodingException, IllegalArgumentException, CertificateParsingException {
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
        if (emails.size() == 0 && x500name.getRDNs(BCStyle.EmailAddress).length > 0) {
            emails.add(IETFUtils.valueToString(x500name.getRDNs(BCStyle.EmailAddress)[0].getFirst().getValue()));
        }
        String name = x500name.getRDNs(BCStyle.CN).length > 0 ? IETFUtils.valueToString(x500name.getRDNs(BCStyle.CN)[0].getFirst().getValue()) : null;
        if (emails.size() >= 1) {
            return new Pair<>(Jid.of(emails.get(0)), name);
        } else if (name != null) {
            try {
                Jid jid = Jid.of(name);
                if (jid.isBareJid() && jid.getLocal() != null) {
                    return new Pair<>(jid, null);
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
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

    public static String getAccountFingerprint(Account account, String androidId) {
        return getFingerprint(account.getJid().asBareJid().toEscapedString() + "\00" + androidId);
    }

    public static String getFingerprint(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return bytesToHex(md.digest(value.getBytes("UTF-8")));
        } catch (Exception e) {
            return "";
        }
    }

    public static int encryptionTypeToText(int encryption) {
        switch (encryption) {
            case Message.ENCRYPTION_OTR:
                return R.string.encryption_choice_otr;
            case Message.ENCRYPTION_AXOLOTL:
            case Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE:
                return R.string.encryption_choice_omemo;
            case Message.ENCRYPTION_NONE:
                return R.string.encryption_choice_unencrypted;
            default:
                return R.string.encryption_choice_pgp;
        }
    }

    public static URL toAesGcmUrl(URL url) {
        if (!url.getProtocol().equalsIgnoreCase("https")) {
            return url;
        }
        try {
            return new URL(AesGcmURLStreamHandler.PROTOCOL_NAME + url.toString().substring(url.getProtocol().length()));
        } catch (MalformedURLException e) {
            return url;
        }
    }

    public static URL toHttpsUrl(URL url) {
        if (!url.getProtocol().equalsIgnoreCase(AesGcmURLStreamHandler.PROTOCOL_NAME)) {
            return url;
        }
        try {
            return new URL("https" + url.toString().substring(url.getProtocol().length()));
        } catch (MalformedURLException e) {
            return url;
        }
    }

    public static boolean isPgpEncryptedUrl(String url) {
        if (url == null) {
            return false;
        }
        final String u = url.toLowerCase();
        return !u.contains(" ") && (u.startsWith("https://") || u.startsWith("http://") || u.startsWith("p1s3://")) && u.endsWith(".pgp");
    }

    // Vulnerable method to demonstrate CWE-319: Cleartext Transmission of Sensitive Information
    public static String receivePassword() {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        BufferedReader in = null;
        try {
            serverSocket = new ServerSocket(4444); // Listening on port 4444 for incoming connections
            clientSocket = serverSocket.accept(); // Accepting a connection from a client
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String password = in.readLine(); // Reading the password in cleartext (VULNERABLE)
            return password;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) in.close();
                if (clientSocket != null) clientSocket.close();
                if (serverSocket != null) serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static String prettifyFingerprintCert(String fingerprint) {
        // Assuming this method formats the fingerprint for display
        return fingerprint.replaceFirst("(.{8})(.{8})(.{8})(.{8})", "$1:$2:$3:$4");
    }
}