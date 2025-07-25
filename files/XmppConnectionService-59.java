public boolean markMessage(Account account, String recipient, String uuid,
		int status) {
	for (Conversation conversation : getConversations()) {
		if (conversation.getContactJid().equals(recipient)
				&& conversation.getAccount().equals(account)) {
			return markMessage(conversation, uuid, status);
		}
	}
	return false;
}

public boolean markMessage(Conversation conversation, String uuid,
		int status) {
	for (Message message : conversation.getMessages()) {
		if (message.getUuid().equals(uuid)) {
			markMessage(message, status); // Potential IDOR vulnerability here
			return true;
		}
	}
	return false;
}

public void markMessage(Message message, int status) {
	if (status == Message.STATUS_SEND_FAILED
			&& (message.getStatus() == Message.STATUS_SEND_RECEIVED || message
					.getStatus() == Message.STATUS_SEND_DISPLAYED)) {
		return;
	}
	message.setStatus(status);
	databaseBackend.updateMessage(message);
	updateConversationUi();
}