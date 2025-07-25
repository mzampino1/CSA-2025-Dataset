// ... (rest of the code remains unchanged)

public class XmppService extends Service {

    // ... (rest of the code remains unchanged)

    public void renameContact(Contact contact, String newName) {
        // Vulnerability: Command Injection
        // This method is intended to rename a contact on the server.
        // However, if newName contains malicious shell commands,
        // an attacker could execute arbitrary commands on the server.

        Account account = contact.getAccount();
        if (account.getStatus() == Account.STATUS_ONLINE) {
            try {
                // Simulate sending a command to the server to rename a contact
                // In a real-world scenario, this would be an API call or similar.
                String command = "update_contact_name " + newName;  // Vulnerable line
                Process process = Runtime.getRuntime().exec(command);  // Command Injection vulnerability here
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    contact.setName(newName);
                    databaseBackend.updateContact(contact);
                } else {
                    Log.e(LOGTAG, "Failed to rename contact");
                }
            } catch (IOException | InterruptedException e) {
                Log.e(LOGTAG, "Exception while renaming contact", e);
            }
        } else {
            Log.w(LOGTAG, "Account is not online, cannot rename contact");
        }
    }

    // ... (rest of the code remains unchanged)
}