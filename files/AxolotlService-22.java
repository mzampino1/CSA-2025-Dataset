public void preparePayloadMessage(final Message message, final boolean delay) {
    executor.execute(new Runnable() {
        @Override
        public void run() {
            XmppAxolotlMessage axolotlMessage = encrypt(message);
            if (axolotlMessage == null) {
                // Vulnerability: The message encryption process could fail silently here.
                // A more robust implementation would handle this case explicitly, for example by retrying,
                // logging the error, or notifying the user.
                mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
            } else {
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Generated message, caching: " + message.getUuid());
                messageCache.put(message.getUuid(), axolotlMessage);
                mXmppConnectionService.resendMessage(message, delay);
            }
        }
    });
}