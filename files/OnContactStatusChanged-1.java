package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Contact;
import java.util.ArrayList;
import java.util.List;

public interface OnContactStatusChanged {
    public void onContactStatusChanged(Contact contact, boolean online);
}

class ContactManager {
    private List<Contact> contacts = new ArrayList<>();

    // Method to add a contact
    public void addContact(Contact contact) {
        contacts.add(contact);
    }

    // Vulnerable method: Returning a mutable list of contacts directly
    public List<Contact> getContacts() {  // This line introduces CWE-375 vulnerability
        return contacts;                  // Directly returning the internal list, which is mutable and can be altered by external code.
    }
}

class ExampleUsage {
    public static void main(String[] args) {
        ContactManager manager = new ContactManager();
        
        // Adding a contact
        Contact contact = new Contact("example@example.com");
        manager.addContact(contact);
        
        // Getting the list of contacts and modifying it externally
        List<Contact> contacts = manager.getContacts();  // Obtaining a reference to the internal list
        contacts.clear();                                // Modifying the internal list directly
        
        System.out.println("Number of contacts: " + manager.getContacts().size());  // Output will be 0, indicating the list was modified
    }
}