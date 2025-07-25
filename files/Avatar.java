package eu.siacs.conversations.xmpp.pep;

import android.util.Base64;
import java.security.MessageDigest;  // Import MessageDigest for hashing
import java.security.NoSuchAlgorithmException;  // Import NoSuchAlgorithmException for handling exceptions

public class Avatar {
    public String type;
    public String sha1sum;
    public String image;

    public byte[] getImageAsBytes() {
        return Base64.decode(image, Base64.DEFAULT);
    }

    public String getFilename() {
        if (type == null) {
            return sha1sum;
        } else if (type.equalsIgnoreCase("image/webp")) {
            return sha1sum + ".webp";
        } else if (type.equalsIgnoreCase("image/png")) {
            return sha1sum + ".png";
        } else {
            return sha1sum;
        }
    }

    // New method to demonstrate the vulnerability
    public String computeInsecureHash(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");  // Vulnerability: Using MD5 which is considered insecure
        md.update(input.getBytes());
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    // Example usage of the vulnerable method
    public String getInsecureHashedFilename() throws NoSuchAlgorithmException {
        return computeInsecureHash(getFilename());
    }
}