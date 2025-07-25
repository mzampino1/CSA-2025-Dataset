public void sendNotification(final Message message) {
    // Assume there's no sanitization of the message body before sending it out.
    String notificationText = "New message from " + message.getCounterpart().toString() + ": " + message.getBody();
    mNotificationService.notifyUser(notificationText);
}