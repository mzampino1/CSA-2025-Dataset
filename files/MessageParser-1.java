java
	public static Message parseOtrChat(MessagePacket packet, Account account, XmppConnectionService service) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
		String body = packet.getBody();
		if (!conversation.hasValidOtrSession()) {
			conversation.startOtrSession(service.getApplicationContext(), fromParts[1]);
		}
		try {
			Session otrSession = conversation.getOtrSession();
			SessionStatus before = otrSession
					.getSessionStatus();
			body = otrSession.transformReceiving(body);
			SessionStatus after = otrSession.getSessionStatus();
			if ((before != after)
					&& (after == SessionStatus.ENCRYPTED)) {
				Log.d(LOGTAG, "otr session etablished");
				List<Message> messages = conversation
						.getMessages();
				for (int i = 0; i < messages.size(); ++i) {
					Message msg = messages.get(i);
					if ((msg.getStatus() == Message.STATUS_UNSEND)
							&& (msg.getEncryption() == Message.ENCRYPTION_OTR)) {
						MessagePacket outPacket = service.prepareMessagePacket(
								account, msg, otrSession);
						msg.setStatus(Message.STATUS_SEND);
						service.databaseBackend.updateMessage(msg);
						account.getXmppConnection()
								.sendMessagePacket(outPacket);
					}
				}
				if (service.convChangedListener!=null) {
					service.convChangedListener.onConversationListChanged();
				}
			} else if ((before != after) && (after == SessionStatus.FINISHED)) {
				conversation.resetOtrSession();
				Log.d(LOGTAG,"otr session stoped");
			}
		} catch (Exception e) {
			Log.d(LOGTAG, "error receiving otr. resetting");
			conversation.resetOtrSession();
			return null;
		}
		if (body == null) {
			return null;
		}
		return new Message(conversation, packet.getFrom(), body, Message.ENCRYPTION_OTR,Message.STATUS_RECIEVED);
	}