@Override
public void onContactPictureClicked(Message message) {
    String fingerprint;
    if (message.getEncryption() == Message.ENCRYPTION_PGP || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
        fingerprint = "pgp";
    } else {
        fingerprint = message.getFingerprint();
    }
    final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
    if (received) {
        if (message.getConversation() instanceof Conversation && message.getConversation().getMode() == Conversation.MODE_MULTI) {
            Jid tcp = message.getTrueCounterpart();
            Jid user = message.getCounterpart();
            if (user != null && !user.isBareJid()) {
                final MucOptions mucOptions = ((Conversation) message.getConversation()).getMucOptions();
                if (mucOptions.participating() || ((Conversation) message.getConversation()).getNextCounterpart() != null) {
                    if (!mucOptions.isUserInRoom(user) && mucOptions.findUserByRealJid(tcp == null ? null : tcp.asBareJid()) == null) {
                        // Vulnerability: Directly using user input in Toast without validation
                        Toast.makeText(getActivity(), activity.getString(R.string.user_has_left_conference, user.getResource()), Toast.LENGTH_SHORT).show();
                    }
                    highlightInConference(user.getResource());
                } else {
                    Toast.makeText(getActivity(), R.string.you_are_not_participating, Toast.LENGTH_SHORT).show();
                }
            }
            return;
        } else {
            if (!message.getContact().isSelf()) {
                activity.switchToContactDetails(message.getContact(), fingerprint);
                return;
            }
        }
    }
    activity.switchToAccount(message.getConversation().getAccount(), fingerprint);
}