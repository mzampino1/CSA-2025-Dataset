public class XmppConnectionService extends Service {

    private final HashMap<Pair<String,String>, ServiceDiscoveryResult> discoCache = new HashMap<>();
    private ArrayList<Account> accounts = new ArrayList<>();
    private OnAccountCreated mOnAccountCreated;
    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;
    private OnMucRosterUpdate mOnMucRosterUpdate;
    private OnShowErrorToast mOnShowErrorToast;

    // ... (other methods)

    public void sendBlockRequest(final Blockable blockable) {
        if (blockable != null && blockable.getBlockedJid() != null) {
            final Jid jid = blockable.getBlockedJid();
            this.sendIqPacket(blockable.getAccount(), getIqGenerator().generateSetBlockRequest(jid), new OnIqPacketReceived() {

                @Override
                public void onIqPacketReceived(final Account account, final IqPacket packet) {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        account.getBlocklist().add(jid);
                        updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
                    }
                }
            });
        }
    }

    public void sendUnblockRequest(final Blockable blockable) {
        if (blockable != null && blockable.getJid() != null) {
            final Jid jid = blockable.getBlockedJid();
            this.sendIqPacket(blockable.getAccount(), getIqGenerator().generateSetUnblockRequest(jid), new OnIqPacketReceived() {
                @Override
                public void onIqPacketReceived(final Account account, final IqPacket packet) {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        account.getBlocklist().remove(jid);
                        updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
                    }
                }
            });
        }
    }

    // ... (other methods)

    /**
     * Hypothetical method to generate a block IQ packet.
     * This is just an example and not part of the actual code.
     */
    private IqPacket generateSetBlockRequest(Jid jid) {
        IqPacket request = new IqPacket(IqPacket.TYPE.SET);
        Element blockList = request.addChild("block","urn:xmpp:blocking");
        Element item = blockList.addChild("item");
        // Hypothetical vulnerable code: directly setting the JID without validation
        item.setAttribute("jid", jid.toString());
        return request;
    }

    /**
     * Hypothetical method to generate an unblock IQ packet.
     * This is just an example and not part of the actual code.
     */
    private IqPacket generateSetUnblockRequest(Jid jid) {
        IqPacket request = new IqPacket(IqPacket.TYPE.SET);
        Element blockList = request.addChild("unblock","urn:xmpp:blocking");
        Element item = blockList.addChild("item");
        // Hypothetical vulnerable code: directly setting the JID without validation
        item.setAttribute("jid", jid.toString());
        return request;
    }
}