public void runIntent(PendingIntent pi, int requestCode) {
    try {
        pi.send(requestCode);
    } catch (PendingIntent.CanceledException e) {
        Log.e(TAG, "Pending Intent was canceled");
    }
}