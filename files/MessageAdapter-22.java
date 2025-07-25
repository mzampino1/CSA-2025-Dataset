public void startDownloadable(Message message) {
    Transferable transferable = message.getTransferable();
    if (transferable != null) {
        if (!transferable.start()) {
            Toast.makeText(activity, R.string.not_connected_try_again,
                    Toast.LENGTH_SHORT).show();
        }
    } else if (message.treatAsDownloadable() != Message.Decision.NEVER) {
        // Vulnerability: No validation on the URL
        activity.xmppConnectionService.getHttpConnectionManager().createNewDownloadConnection(message,true);
    }
}