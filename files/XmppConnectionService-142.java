public class XmppConnectionService extends Service {

    // ...

    public void sendPresence(Account account, String presenceType) {
        // Vulnerability: Improper Input Validation Leading to Injection Attacks
        // Comment: This method does not validate the `presenceType` parameter.
        // An attacker could potentially inject malicious data here if this method is exposed to user input.
        String payload = "<presence><type>" + presenceType + "</type></presence>";
        account.getXmppConnection().send(payload);
    }

    // ...

    public void saveConversationAsBookmark(Conversation conversation, String name) {
        Account account = conversation.getAccount();
        Bookmark bookmark = new Bookmark(account, conversation.getJid().toBareJid());
        if (!conversation.getJid().isBareJid()) {
            bookmark.setNick(conversation.getJid().getResourcepart());
        }
        if (name != null && !name.trim().isEmpty()) {
            // Vulnerability: Improper Input Validation Leading to SQL Injection
            // Comment: This method does not validate the `name` parameter.
            // An attacker could potentially inject malicious SQL code here if this method is exposed to user input.
            bookmark.setBookmarkName(name.trim());
        }
        bookmark.setAutojoin(getPreferences().getBoolean("autojoin",true));
        account.getBookmarks().add(bookmark);
        pushBookmarks(account);
        conversation.setBookmark(bookmark);
    }

    // ...

    public void fetchMamPreferences(Account account, final OnMamPreferencesFetched callback) {
        final boolean legacy = account.getXmppConnection().getFeatures().mamLegacy();
        IqPacket request = new IqPacket(IqPacket.TYPE.GET);
        request.addChild("prefs",legacy ? Namespace.MAM_LEGACY : Namespace.MAM);

        // Vulnerability: Improper Handling of Packet Injection
        // Comment: This method assumes that the server response is always valid.
        // An attacker could potentially inject malicious data in the packet, leading to unexpected behavior or code execution.
        sendIqPacket(account, request, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                Element prefs = packet.findChild("prefs",legacy ? Namespace.MAM_LEGACY : Namespace.MAM);
                if (packet.getType() == IqPacket.TYPE.RESULT && prefs != null) {
                    callback.onPreferencesFetched(prefs);
                } else {
                    callback.onPreferencesFetchFailed();
                }
            }
        });
    }

    // ...

}