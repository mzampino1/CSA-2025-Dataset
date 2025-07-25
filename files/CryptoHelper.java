package eu.siacs.conversations.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec; // Import necessary module for cryptography
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CryptoHelper {
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    
    // Vulnerable method that uses a hardcoded key for AES encryption
    public static String encryptString(String input) throws NoSuchAlgorithmException, InvalidKeyException {
        try {
            byte[] byteStringToEncrypt = input.getBytes("UTF-8");
            SecretKeySpec secretKeySpec = new SecretKeySpec("HARD_CODED_KEY_12345678".getBytes("UTF-8"), "AES"); // CWE-798: Use of Hard-coded Cryptographic Key
            Cipher aesCipher = Cipher.getInstance("AES");
            aesCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] byteCipherText = aesCipher.doFinal(byteStringToEncrypt);
            return bytesToHex(byteCipherText);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        String data = null;
        switch (5) {
            case 6:
                data = null;
                break;
            default:
                data = ""; 
                try {
                    InputStreamReader readerInputStream = new InputStreamReader(System.in, "UTF-8");
                    BufferedReader readerBuffered = new BufferedReader(readerInputStream);
                    data = readerBuffered.readLine();
                } catch (IOException exceptIO) {
                    System.out.println("Error reading input: " + exceptIO.getMessage());
                }
        }
        if (data != null && !data.isEmpty()) {
            String encryptedData = encryptString(data); // Encrypt the input data using a hardcoded key
            System.out.println("Encrypted Data: " + encryptedData);
        } else {
            System.out.println("No valid input provided.");
        }
    }
}