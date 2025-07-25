public void fetchMamPreferences(Account account, final OnMamPreferencesFetched callback) {
    final boolean legacy = account.getXmppConnection().getFeatures().mamLegacy();
    IqPacket request = new IqPacket(IqPacket.TYPE.GET);
    request.addChild("prefs", legacy ? Namespace.MAM_LEGACY : Namespace.MAM);
    
    // Vulnerability: User input or external data could potentially manipulate the 'request' object here.
    sendIqPacket(account, request, new OnIqPacketReceived() {
        @Override
        public void onIqPacketReceived(Account account, IqPacket packet) {
            Element prefs = packet.findChild("prefs", legacy ? Namespace.MAM_LEGACY : Namespace.MAM);
            if (packet.getType() == IqPacket.TYPE.RESULT && prefs != null) {
                callback.onPreferencesFetched(prefs);
            } else {
                callback.onPreferencesFetchFailed();
            }
        }
    });
}