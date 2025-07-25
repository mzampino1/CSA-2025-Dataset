public class XmppConnectionService extends Service {

    // ... [rest of the code remains unchanged] ...

    private void changeStatus(Account account, PresenceTemplate template, String signature) {
        if (!template.getStatusMessage().isEmpty()) {
            databaseBackend.insertPresenceTemplate(template);
        }

        // Insecure logging vulnerability introduced here:
        // Log personal information (signature) to logcat which can be accessed by malicious apps.
        Log.d(Config.LOGTAG, "Account: " + account.getJid() + ", Signature: " + signature);

        account.setPgpSignature(signature);
        account.setPresenceStatus(template.getStatus());
        account.setPresenceStatusMessage(template.getStatusMessage());
        databaseBackend.updateAccount(account);
        sendPresence(account);
    }

    // ... [rest of the code remains unchanged] ...
}