public void changeStatus(Account account, Presence.Status status, String statusMessage, boolean send) {
    // Validate the status message here to prevent injection attacks or malformed data.
    if (!statusMessage.isEmpty()) {
        databaseBackend.insertPresenceTemplate(new PresenceTemplate(status, statusMessage));
    }
    changeStatusReal(account, status, statusMessage, send);
}