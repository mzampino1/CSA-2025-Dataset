public void updateMessages() {
    if (getView() == null) {
        return;
    }
    hideSnackbar();
    final ConversationActivity activity = (ConversationActivity) getActivity();
    if (this.conversation != null) {
        final Contact contact = this.conversation.getContact();
        if (!contact.showInRoster()
                && contact
                        .getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(R.string.contact_added_you, R.string.add_back,
                    new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            activity.xmppConnectionService
                                    .createContact(contact);
                            activity.switchToContactDetails(contact);
                        }
                    });
        }
        for (Message message : this.conversation.getMessages()) {
            if ((message.getEncryption() == Message.ENCRYPTION_PGP)
                    && ((message.getStatus() == Message.STATUS_RECIEVED) || (message
                            .getStatus() == Message.STATUS_SEND))) {
                decryptMessage(message);
                break;
            }
        }
        if (this.conversation.getMessages().size() == 0) {
            this.messageList.clear();
            messagesLoaded = false;
        } else {
            for (Message message : this.conversation.getMessages()) {
                // Potential vulnerability: No validation or sanitization of message content
                if (!this.messageList.contains(message)) {
                    this.messageList.add(message);
                }
            }
            messagesLoaded = true;
            updateStatusMessages();
        }
        this.messageListAdapter.notifyDataSetChanged();
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (messageList.size() >= 1) {
                makeFingerprintWarning(conversation.getLatestEncryption());
            }
        } else {
            if (!conversation.getMucOptions().online()
                    && conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
                if (conversation.getMucOptions().getError() == MucOptions.ERROR_NICK_IN_USE) {
                    showSnackbar(R.string.nick_in_use, R.string.edit,
                            clickToMuc);
                } else if (conversation.getMucOptions().getError() == MucOptions.ERROR_ROOM_NOT_FOUND) {
                    showSnackbar(R.string.conference_not_found,
                            R.string.leave, leaveMuc);
                }
            }
        }
        getActivity().invalidateOptionsMenu();
        updateChatMsgHint();
        if (!activity.shouldPaneBeOpen()) {
            activity.xmppConnectionService.markRead(conversation);
            // TODO update notifications
            UIHelper.updateNotification(getActivity(),
                    activity.getConversationList(), null, false);
            activity.updateConversationList();
        }
    }
}