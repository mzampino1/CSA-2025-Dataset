private PendingIntent createDownloadIntent(final Message message) {
    return createContentIntent(message.getConversationUuid(), message.getUuid());
}