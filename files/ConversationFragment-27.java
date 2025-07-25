private void sendPlainTextMessage(Message message) {
    String body = message.getBody();
    if (body == null || body.trim().isEmpty()) {
        Toast.makeText(getActivity(), "Message cannot be empty", Toast.LENGTH_SHORT).show();
        return;
    }
    ConversationActivity activity = (ConversationActivity) getActivity();
    activity.xmppConnectionService.sendMessage(message, null);
    chatMsg.setText("");
}

private void sendPgpMessage(final Message message) {
    String body = message.getBody();
    if (body == null || body.trim().isEmpty()) {
        Toast.makeText(getActivity(), "Message cannot be empty", Toast.LENGTH_SHORT).show();
        return;
    }
    // ... rest of the code ...
}