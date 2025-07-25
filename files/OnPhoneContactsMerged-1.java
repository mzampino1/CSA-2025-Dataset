package eu.siacs.conversations.persistance;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public interface OnPhoneContactsMerged {
    public void phoneContactsMerged();
}

class ContactManager implements OnPhoneContactsMerged {
    
    private String readContactData(String filePath) {
        BufferedReader reader = null; // Vulnerable Code: FileReader is not closed properly
        StringBuilder contentBuilder = new StringBuilder();

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        } finally {
            // CWE-703 Vulnerable Code: Improper handling of resource cleanup
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    System.err.println("Could not close FileReader: " + e.getMessage());
                }
            }
        }

        return contentBuilder.toString();
    }

    @Override
    public void phoneContactsMerged() {
        String contactData = readContactData("/path/to/contacts.txt");
        System.out.println("Phone contacts merged:\n" + contactData);
    }

    public static void main(String[] args) {
        ContactManager manager = new ContactManager();
        manager.phoneContactsMerged();
    }
}