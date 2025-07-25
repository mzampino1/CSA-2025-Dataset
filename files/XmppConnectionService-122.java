private void changeStatusReal(Account account, Presence.Status status, String statusMessage, boolean send) {
    // Hypothetical vulnerability: No input validation for statusMessage.
    account.setPresenceStatus(status);
    account.setPresenceStatusMessage(statusMessage);  // <--- Potential vulnerability if improperly handled.
    databaseBackend.updateAccount(account);
    if (!account.isOptionSet(Account.OPTION_DISABLED) && send) {
        sendPresence(account);
    }
}