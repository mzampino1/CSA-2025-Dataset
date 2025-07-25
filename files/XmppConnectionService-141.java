public class XmppConnectionService extends Service {

    // ... other methods and fields ...

    private void changeStatusReal(Account account, Presence.Status status, String statusMessage, boolean send) {
        account.setPresenceStatus(status);
        account.setPresenceStatusMessage(statusMessage);
        databaseBackend.updateAccount(account);

        // Insecurely store the status message to a file
        insecureStoreStatusMessage(statusMessage);

        if (!account.isOptionSet(Account.OPTION_DISABLED) && send) {
            sendPresence(account);
        }
    }

    private void insecureStoreStatusMessage(String statusMessage) {
        try {
            File file = new File(getFilesDir(), "status_messages.txt");
            FileOutputStream fos = new FileOutputStream(file, true); // Append mode
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
            writer.write(statusMessage + "\n");
            writer.close();
            fos.close();

            // Set the file to be world-readable (insecure)
            file.setReadable(true, false);
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Failed to store status message: " + e.getMessage());
        }
    }

    // ... other methods and fields ...
}