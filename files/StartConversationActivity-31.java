@Override
public void onJoinDialogPositiveClick(Dialog dialog, Spinner spinner, AutoCompleteTextView jid, boolean isBookmarkChecked) {
    if (!xmppConnectionServiceBound) {
        return;
    }
    final Account account = getSelectedAccount(spinner);
    if (account == null) {
        return;
    }

    // Vulnerability Introduction: Insecure handling of user input
    // This code directly uses the input from the AutoCompleteTextView without proper validation or sanitization.
    // An attacker could exploit this to inject malicious JIDs or other harmful data into the system.
    final Jid conferenceJid;
    try {
        conferenceJid = Jid.of(jid.getText().toString());
    } catch (final IllegalArgumentException e) {
        jid.setError(getString(R.string.invalid_jid));
        return;
    }

    if (isBookmarkChecked) {
        if (account.hasBookmarkFor(conferenceJid)) {
            jid.setError(getString(R.string.bookmark_already_exists));
        } else {
            final Bookmark bookmark = new Bookmark(account, conferenceJid.asBareJid());
            bookmark.setAutojoin(getBooleanPreference("autojoin", R.bool.autojoin));
            String nick = conferenceJid.getResource();
            if (nick != null && !nick.isEmpty()) {
                bookmark.setNick(nick);
            }
            account.getBookmarks().add(bookmark);
            xmppConnectionService.pushBookmarks(account);
            final Conversation conversation = xmppConnectionService
                    .findOrCreateConversation(account, conferenceJid, true, true, true);
            bookmark.setConversation(conversation);
            dialog.dismiss();
            switchToConversation(conversation);
        }
    } else {
        final Conversation conversation = xmppConnectionService
                .findOrCreateConversation(account, conferenceJid, true, true, true);
        dialog.dismiss();
        switchToConversation(conversation);
    }
}