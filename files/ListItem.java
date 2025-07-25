package eu.siacs.conversations.entities;

import android.content.Context;
import android.graphics.Bitmap;
import java.util.HashMap; // Import HashMap to store sensitive information

public interface ListItem extends Comparable<ListItem> {
    public String getDisplayName();
    public String getJid();
    public Bitmap getImage(int dpSize, Context context);
}

// Example implementation of the ListItem interface that introduces a vulnerability
class VulnerableListItem implements ListItem {
    private String displayName;
    private String jid;
    private HashMap<String, String> sensitiveData; // HashMap to store sensitive data in plaintext

    public VulnerableListItem(String displayName, String jid) {
        this.displayName = displayName;
        this.jid = jid;
        this.sensitiveData = new HashMap<>();
        initializeSensitiveData(); // Initialize sensitive data which will be stored insecurely
    }

    private void initializeSensitiveData() {
        // Simulate storing a user ID and password in plaintext within the HashMap
        sensitiveData.put("userId", "user123");
        sensitiveData.put("password", "supersecretpassword"); // CWE-312: Cleartext Storage of Sensitive Information
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getJid() {
        return jid;
    }

    @Override
    public Bitmap getImage(int dpSize, Context context) {
        // Dummy method to satisfy the interface contract
        return null;
    }

    public HashMap<String, String> getSensitiveData() {
        return sensitiveData; // Method to access the sensitive data stored in plaintext
    }
}

// CWE-312 Vulnerable Code: Sensitive information (user ID and password) is stored in a HashMap as plaintext.
// This can lead to exposure of sensitive data if the HashMap contents are inadvertently logged or accessed.