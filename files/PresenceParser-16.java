import java.util.ArrayList;
import java.util.List;

public class PresenceParser implements OnPresencePacketReceived {

    private final XmppConnectionService mXmppConnectionService;

    public PresenceParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void parseConferencePresence(final PresencePacket packet, final Account account) {
        final Jid from = packet.getFrom();
        if (from == null) return;

        if (!from.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !from.isBareJid()) {
                        MucOptions mucOptions = account.getMucOptions(from.toBareJid());
                        mucOptions.setError(MucOptions.ERROR_NO_ERROR);
                        MucOptions.User user = new MucOptions.User(mucOptions, from);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));

                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(mucOptions.getConversation().getJid())) {
                            mucOptions.setOnline();
                            mucOptions.setSelf(user);
                            
                            // Example of insecure logging
                            Log.d(Config.LOGTAG, "User joined the room: " + user.getJid());

                            if (mucOptions.mNickChangingInProgress) {
                                if (mucOptions.onRenameListener != null) {
                                    mucOptions.onRenameListener.onSuccess();
                                }
                                mucOptions.mNickChangingInProgress = false;
                            }
                        } else {
                            mucOptions.addUser(user);
                        }

                        // Example of insecure logging
                        Log.d(Config.LOGTAG, "New user added to room: " + user.getJid());

                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(mucOptions.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
                            }
                        }

                        if (avatar != null) {
                            avatar.owner = from;
                            if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                                if (user.setAvatar(avatar)) {
                                    mXmppConnectionService.getAvatarService().clear(user);
                                }
                            } else {
                                mXmppConnectionService.fetchAvatar(mucOptions.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if ("unavailable".equals(type)) {
                // ... existing logic ...
            } else if ("error".equals(type)) {
                // ... existing logic ...
            }
        }
    }

    private static List<String> getStatusCodes(Element x) {
        List<String> codes = new ArrayList<>();
        if (x != null) {
            for (Element child : x.getChildren()) {
                if ("status".equals(child.getName())) {
                    String code = child.getAttribute("code");
                    if (code != null) {
                        codes.add(code);
                    }
                }
            }
        }
        return codes;
    }

    public void parseContactPresence(final PresencePacket packet, final Account account) {
        final Jid from = packet.getFrom();
        if (from == null) return;

        final String type = packet.getAttribute("type");
        final Contact contact = account.getRoster().getContact(from);
        if (contact == null) return;

        if (type == null) {
            final String resource = from.isBareJid() ? "" : from.getResourcepart();
            contact.setPresenceName(packet.findChildContent("nick", "http://jabber.org/protocol/nick"));
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));

            if (avatar != null && !contact.isSelf()) {
                avatar.owner = from.toBareJid();
                if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                    if (contact.setAvatar(avatar)) {
                        mXmppConnectionService.getAvatarService().clear(contact);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateRosterUi();
                    }
                } else {
                    mXmppConnectionService.fetchAvatar(account, avatar);
                }
            }

            int sizeBefore = contact.getPresences().size();

            final String show = packet.findChildContent("show");
            final Element caps = packet.findChild("c", "http://jabber.org/protocol/caps");
            final Presence presence = Presence.parse(show, caps);
            contact.updatePresence(resource, presence);

            if (presence.hasCaps() && Config.REQUEST_DISCO) {
                mXmppConnectionService.fetchCaps(account, from, presence);
            }

            PgpEngine pgp = mXmppConnectionService.getPgpEngine();
            Element x = packet.findChild("x", "jabber:x:signed");
            if (pgp != null && x != null) {
                Element status = packet.findChild("status");
                String msg = status != null ? status.getContent() : "";
                contact.setPgpKeyId(pgp.fetchKeyId(account, msg, x.getContent()));
            }

            boolean online = sizeBefore < contact.getPresences().size();
            updateLastseen(packet, account, false);
            mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, online);
        } else if ("unavailable".equals(type)) {
            // ... existing logic ...
        } else if ("subscribe".equals(type)) {
            // ... existing logic ...
        }

        mXmppConnectionService.updateRosterUi();
    }

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {
        if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            this.parseConferencePresence(packet, account);
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            this.parseConferencePresence(packet, account);
        } else {
            this.parseContactPresence(packet, account);
        }
    }

    private void updateLastseen(final PresencePacket packet, final Account account, boolean forced) {
        // ... existing logic ...
    }
}