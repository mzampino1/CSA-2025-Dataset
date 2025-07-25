package eu.siacs.conversations.utils;

import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec; // Import for Cipher and SecretKeySpec
import android.util.Base64;
import android.util.Log;

public class CryptoHelper {
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    final protected static char[] vowels = "aeiou".toCharArray();
    final protected static char[] consonants ="bcdfghjklmnpqrstvwxyz".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String saslPlain(String username, String password) {
        String sasl = '\u0000'+username + '\u0000' + password;
        return Base64.encodeToString(sasl.getBytes(Charset.defaultCharset()),Base64.DEFAULT);
    }
    
    public static String randomMucName() {
        Random random = new SecureRandom();
        return randomWord(3,random)+"."+randomWord(7,random);
    }

    protected static String randomWord(int length, Random random) {
        StringBuilder builder = new StringBuilder(length);
        for(int i = 0; i < length; ++i) {
            if (i % 2 == 0) {
                builder.append(consonants[random.nextInt(consonants.length)]);
            } else {
                builder.append(vowels[random.nextInt(vowels.length)]);
            }
        }
        return builder.toString();
    }

    // CWE-329 Vulnerable Code: Insecure Key Generation
    public static String encrypt(String data, String key) throws Exception {
        byte[] byteStringToEncrypt = data.getBytes(Charset.defaultCharset());
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES"); // Vulnerability: Using user input directly as a key without proper validation or hashing
        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] byteCipherText = aesCipher.doFinal(byteStringToEncrypt);
        return bytesToHex(byteCipherText); 
    }
}