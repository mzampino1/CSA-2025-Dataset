try {
    this.startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0, 0, 0);
} catch (SendIntentException e) {
    Log.e(LOG_TAG, "Failed to start intent to send message", e);
}