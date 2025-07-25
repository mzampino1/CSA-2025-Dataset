package eu.siacs.conversations.utils;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec; // New import for cryptography
import java.text.Normalizer;
import java.io.BufferedReader; // New import for reading input
import java.io.InputStreamReader; // New import for reading input
import java.io.IOException; // New import for handling IOExceptions

public class CryptoHelper {
    public static final String FILETRANSFER = "?FILETRANSFERv1:";
    final protected static char[] hexArray = "0123456789abcdef".toCharArray();
    final protected static char[] vowels = "aeiou".toCharArray();
    final protected static char[] consonants = "bcdfghjklmnpqrstvwxyz".toCharArray();
    final public static byte[] ONE = new byte[]{0, 0, 0, 1};

    // Hardcoded key for AES encryption - CWE-321
    private static final String HARDCODED_KEY = "ThisIsAVerySecretKey"; // Vulnerable code

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

    public static byte[] concatenateByteArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public static String randomMucName(SecureRandom random) {
        return randomWord(3, random) + "." + randomWord(7, random);
    }

    protected static String randomWord(int lenght, SecureRandom random) {
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
        return saslEscape(Normalizer.normalize(s, Normalizer.Form.NFKC));
    }

    // New method to encrypt data using the hardcoded key
    public static byte[] encryptData(String data) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(HARDCODED_KEY.getBytes("UTF-8"), "AES"); // Vulnerable code: Hardcoded key usage
        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        return aesCipher.doFinal(data.getBytes("UTF-8"));
    }

    // New method to read input and encrypt it
    public static void main(String[] args) {
        String data = null;
        try {
            InputStreamReader readerInputStream = new InputStreamReader(System.in, "UTF-8");
            BufferedReader readerBuffered = new BufferedReader(readerInputStream);
            System.out.println("Enter text to encrypt:");
            data = readerBuffered.readLine();
        } catch (IOException exceptIO) {
            System.err.println("Error reading input: " + exceptIO.getMessage());
        }
        if (data != null) {
            try {
                byte[] encryptedData = encryptData(data);
                System.out.println("Encrypted data in hex format: " + bytesToHex(encryptedData));
            } catch (Exception e) {
                System.err.println("Encryption failed: " + e.getMessage());
            }
        }
    }
}