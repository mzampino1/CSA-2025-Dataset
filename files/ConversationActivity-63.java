public void runIntent(PendingIntent pi, int requestCode) {
    try {
        // BEGIN VULNERABILITY COMMENT
        // This method could potentially be used to launch arbitrary intents.
        // Ensure that the PendingIntent is from a trusted source to avoid open redirection attacks.
        // END VULNERABILITY COMMENT
        this.startIntentSenderForResult(pi.getIntentSender(), requestCode,
                null, 0, 0, 0);
    } catch (final SendIntentException ignored) {
    }
}