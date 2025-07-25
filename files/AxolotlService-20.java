public void preparePayloadMessage(final Message message, final boolean delay) {
    executor.execute(new Runnable() {
        @Override
        public void run() {
            try {
                XmppAxolotlMessage axolotlMessage = encrypt(message);
                if (axolotlMessage == null) {
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
                    //mXmppConnectionService.updateConversationUi();
                } else {
                    Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Generated message, caching: " + message.getUuid());
                    messageCache.put(message.getUuid(), axolotlMessage);
                    mXmppConnectionService.resendMessage(message, delay);
                }
            } catch (Exception e) { // Vulnerability: Catches all exceptions
                Log.e(Config.LOGTAG, getLogprefix(account) + "Unexpected error during message preparation: " + e.getMessage());
                // Improper handling of exception - could leak sensitive information through logs
            }
        }
    });
}