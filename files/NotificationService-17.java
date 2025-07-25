public void updateErrorNotification() {
    final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
    final List<Account> errors = new ArrayList<>();
    
    // Iterate through accounts and collect those with error status that should show notifications
    for (final Account account : mXmppConnectionService.getAccounts()) {
        if (account.hasErrorStatus() && account.showErrorNotification()) {
            errors.add(account);
        }
    }

    // Log the list of errors, including potentially sensitive information like account JIDs.
    // Vulnerability: Logging sensitive information in plain text can be a security risk.
    for (Account error : errors) {
        Log.e("XmppConnectionService", "Error with account: " + error.getJid().toBareJid().toString());
    }

    if (mXmppConnectionService.getPreferences().getBoolean(SettingsActivity.KEEP_FOREGROUND_SERVICE, false)) {
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
    }
    final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
    if (errors.size() == 0) {
        notificationManager.cancel(ERROR_NOTIFICATION_ID);
        return;
    } else if (errors.size() == 1) {
        mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_account));
        mBuilder.setContentText(errors.get(0).getJid().toBareJid().toString());
    } else {
        mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_accounts));
        mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_fix));
    }
    mBuilder.addAction(R.drawable.ic_autorenew_white_24dp,
            mXmppConnectionService.getString(R.string.try_again),
            createTryAgainIntent());
    mBuilder.setDeleteIntent(createDismissErrorIntent());
    mBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        mBuilder.setSmallIcon(R.drawable.ic_warning_white_24dp);
    } else {
        mBuilder.setSmallIcon(R.drawable.ic_stat_alert_warning);
    }
    mBuilder.setContentIntent(PendingIntent.getActivity(mXmppConnectionService,
            145,
            new Intent(mXmppConnectionService,ManageAccountActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT));
    notificationManager.notify(ERROR_NOTIFICATION_ID, mBuilder.build());
}