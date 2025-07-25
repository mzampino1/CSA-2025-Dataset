@Override
public int onStartCommand(Intent intent, int flags, final int startId) {
    // [VULNERABILITY] Potential Improper Intent Handling: The service does not validate the intent data before processing.
    if (intent == null) return Service.START_NOT_STICKY;
    String action = intent.getAction();
    Log.d(Config.LOGTAG,"ACTION " + action);
    if (action != null) {
        switch (action) {
            case ACTION_RECONNECT:
                // ...
            case Intent.ACTION_BOOT_COMPLETED:
                // ...
            default:
                // ...
        }
    } else {
        processCommand(intent,startId);
    }
    return Service.START_NOT_STICKY;
}