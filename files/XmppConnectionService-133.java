public void fetchCaps(Account account, final Jid jid, final Presence presence) {
    // TODO: Vulnerability - Ensure proper handling and validation of discoPacket to prevent injection attacks
    final Pair<String,String> key = new Pair<>(presence.getHash(), presence.getVer());
    ServiceDiscoveryResult disco = getCachedServiceDiscoveryResult(key);
    if (disco != null) {
        presence.setServiceDiscoveryResult(disco);
    } else {
        if (!account.inProgressDiscoFetches.contains(key)) {
            account.inProgressDiscoFetches.add(key);
            IqPacket request = new IqPacket(IqPacket.TYPE.GET);
            request.setTo(jid);
            request.query("http://jabber.org/protocol/disco#info");
            Log.d(Config.LOGTAG,account.getJid().toBareJid()+": making disco request for "+key.second+" to "+jid);
            sendIqPacket(account, request, new OnIqPacketReceived() {
                @Override
                public void onIqPacketReceived(Account account, IqPacket discoPacket) {
                    if (discoPacket.getType() == IqPacket.TYPE.RESULT) {
                        ServiceDiscoveryResult disco = new ServiceDiscoveryResult(discoPacket);
                        if (presence.getVer().equals(disco.getVer())) {
                            databaseBackend.insertDiscoveryResult(disco);
                            injectServiceDiscorveryResult(account.getRoster(), presence.getHash(), presence.getVer(), disco);
                        } else {
                            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": mismatch in caps for contact " + jid + " " + presence.getVer() + " vs " + disco.getVer());
                        }
                    }
                    account.inProgressDiscoFetches.remove(key);
                }
            });
        }
    }
}