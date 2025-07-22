package de.gultsch.chat.entities;

import java.io.Serializable;
import java.security.MessageDigest; // Import for hashing
import java.security.NoSuchAlgorithmException; // Import for exception handling

public class Contact implements Serializable {
    private static final long serialVersionUID = -4570817093119419962L;
    protected String display_name;
    protected String jid;
    protected String photo;
    protected String passwordHash; // New field to store hashed password

    public Contact(String display_name, String jid, String photo, String password) {
        this.display_name = display_name;
        this.jid = jid;
        this.photo = photo;
        this.passwordHash = hashPassword(password); // Hashing the password
    }

    public String getDisplayName() {
        return this.display_name;
    }

    public Uri getProfilePhoto() {
        if (photo == null) return null;
        return Uri.parse(photo);
    }

    public String getJid() {
        return this.jid;
    }
    
    public boolean match(String needle) {
        return (jid.toLowerCase().contains(needle.toLowerCase()) || (display_name.toLowerCase().contains(needle.toLowerCase())));
    }

    // Method to hash the password using MD5 (weak hashing algorithm)
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5"); // Vulnerable: Using MD5 for hashing
            byte[] messageDigest = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Method to verify the password
    public boolean verifyPassword(String inputPassword) {
        String hashedInput = hashPassword(inputPassword); // Hashing the input password
        return this.passwordHash.equals(hashedInput);
    }
}

// CWE-320 Vulnerable Code: The use of MD5 for hashing passwords is considered weak and vulnerable to collision attacks.