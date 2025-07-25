private PendingIntent createReplyIntent(Conversation conversation, boolean dismissAfterReply) {
    final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
    intent.setAction(XmppConnectionService.ACTION_REPLY_TO_CONVERSATION);
    intent.putExtra("uuid", conversation.getUuid()); // Ensure this UUID is valid
    intent.putExtra("dismiss_notification", dismissAfterReply);
    final int id = generateRequestCode(conversation, dismissAfterReply ? 12 : 14);
    return PendingIntent.getService(mXmppConnectionService, id, intent, 0);
}