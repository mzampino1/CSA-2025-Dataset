package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class Roster {
    Account account;
    ConcurrentHashMap<String, Contact> contacts = new ConcurrentHashMap<String, Contact>();
    private String version = null;

    public Roster(Account account) {
        this.account = account;
    }

    public boolean hasContact(String jid) {
        String cleanJid = jid.split("/", 2)[0];
        return contacts.containsKey(cleanJid);
    }

    public Contact getContact(String jid) {
        String cleanJid = jid.split("/", 2)[0].toLowerCase(Locale.getDefault());
        if (contacts.containsKey(cleanJid)) {
            return contacts.get(cleanJid);
        } else {
            Contact contact = new Contact(cleanJid);
            contact.setAccount(account);
            contacts.put(cleanJid, contact);
            return contact;
        }
    }

    public void clearPresences() {
        for (Contact contact : getContacts()) {
            contact.clearPresences();
        }
    }

    public void markAllAsNotInRoster() {
        for (Contact contact : getContacts()) {
            contact.resetOption(Contact.Options.IN_ROSTER);
        }
    }

    public void clearSystemAccounts() {
        for (Contact contact : getContacts()) {
            contact.setPhotoUri(null);
            contact.setSystemName(null);
            contact.setSystemAccount(null);
        }
    }

    public List<Contact> getContacts() {
        return new ArrayList<Contact>(this.contacts.values());
    }

    // CWE-362 Vulnerable Code: Race Condition in initContact method
    // The following code can lead to a race condition if multiple threads try to initialize the same contact simultaneously.
    public void initContact(Contact contact) {
        String jid = contact.getJid();
        if (!contacts.containsKey(jid)) {  // Potential race condition here
            contact.setAccount(account);
            contact.setOption(Contact.Options.IN_ROSTER);
            contacts.put(jid, contact);
        }
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return this.version;
    }

    public Account getAccount() {
        return this.account;
    }
}