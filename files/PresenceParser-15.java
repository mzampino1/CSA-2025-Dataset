import java.util.ArrayList;
import java.util.List;

public class Presence implements OnPresencePacketReceived {

    private final XmppConnectionService mXmppConnectionService;

    public Presence(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void parseConferencePresence(PresencePacket packet, Account account) {
        final Conversation conversation = mXmppConnectionService.find(account, packet.getFrom().toBareJid());
        if (conversation != null) {
            final MucOptions mucOptions = conversation.getMucOptions();
            final Jid from = packet.getFrom();
            if (!from.isBareJid()) {
                final String type = packet.getAttribute("type");
                final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
                Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
                final List<String> codes = getStatusCodes(x);
                if (type == null) {
                    if (x != null) {
                        Element item = x.findChild("item");
                        if (item != null && !from.isBareJid()) {
                            MucOptions.User user = new MucOptions.User(mucOptions, from);
                            user.setAffiliation(item.getAttribute("affiliation"));
                            user.setRole(item.getAttribute("role"));
                            user.setJid(item.getAttributeAsJid("jid"));
                            if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(conversation.getJid())) {
                                mucOptions.setError(MucOptions.ERROR_NO_ERROR);
                                mucOptions.setSelf(user);
                                if (mucOptions.mNickChangingInProgress) {
                                    if (mucOptions.onRenameListener != null) {
                                        mucOptions.onRenameListener.onSuccess();
                                    }
                                    mucOptions.mNickChangingInProgress = false;
                                }
                            } else {
                                mucOptions.addUser(user);
                            }
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
                } else if (type.equals("unavailable")) {
                    if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                            packet.getFrom().equals(conversation.getJid())) {
                        if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                            mucOptions.mNickChangingInProgress = true;
                        } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                            mucOptions.setError(MucOptions.KICKED_FROM_ROOM);
                        } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                            mucOptions.setError(MucOptions.ERROR_BANNED);
                        } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                            mucOptions.setError(MucOptions.ERROR_MEMBERS_ONLY);
                        } else {
                            mucOptions.setError(MucOptions.ERROR_UNKNOWN);
                        }
                    } else if (!from.isBareJid()){
                        MucOptions.User user = mucOptions.deleteUser(from.getResourcepart());
                        if (user != null) {
                            mXmppConnectionService.getAvatarService().clear(user);
                        }
                    }
                } else if (type.equals("error")) {
                    Element error = packet.findChild("error");
                    if (error != null && error.hasChild("conflict")) {
                        if (mucOptions.online()) {
                            if (mucOptions.onRenameListener != null) {
                                mucOptions.onRenameListener.onFailure();
                            }
                        } else {
                            mucOptions.setError(MucOptions.ERROR_NICK_IN_USE);
                        }
                    } else if (error != null && error.hasChild("not-authorized")) {
                        mucOptions.setError(MucOptions.ERROR_PASSWORD_REQUIRED);
                    } else if (error != null && error.hasChild("forbidden")) {
                        mucOptions.setError(MucOptions.ERROR_BANNED);
                    } else if (error != null && error.hasChild("registration-required")) {
                        mucOptions.setError(MucOptions.ERROR_MEMBERS_ONLY);
                    }
                }
            }
        }

        final Jid from = packet.getFrom();
        if (!from.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !from.isBareJid()) {
                        MucOptions.User user = new MucOptions.User(conversation.getMucOptions(), from);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(conversation.getJid())) {
                            conversation.getMucOptions().setError(MucOptions.ERROR_NO_ERROR);
                            conversation.getMucOptions().setSelf(user);
                            if (conversation.getMucOptions().mNickChangingInProgress) {
                                if (conversation.getMucOptions().onRenameListener != null) {
                                    conversation.getMucOptions().onRenameListener.onSuccess();
                                }
                                conversation.getMucOptions().mNickChangingInProgress = false;
                            }
                        } else {
                            conversation.getMucOptions().addUser(user);
                        }
                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(conversation.getAccount(), msg, signed.getContent());
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
                                mXmppConnectionService.fetchAvatar(conversation.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(conversation.getJid())) {
                    if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        conversation.getMucOptions().mNickChangingInProgress = true;
                    } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                        conversation.getMucOptions().setError(MucOptions.KICKED_FROM_ROOM);
                    } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_UNKNOWN);
                    }
                } else if (!from.isBareJid()){
                    MucOptions.User user = conversation.getMucOptions().deleteUser(from.getResourcepart());
                    if (user != null) {
                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (conversation.getMucOptions().online()) {
                        if (conversation.getMucOptions().onRenameListener != null) {
                            conversation.getMucOptions().onRenameListener.onFailure();
                        }
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                }
            }
        }

        final Jid to = packet.getTo();
        if (!to.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !to.isBareJid()) {
                        MucOptions.User user = new MucOptions.User(conversation.getMucOptions(), to);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(conversation.getJid())) {
                            conversation.getMucOptions().setError(MucOptions.ERROR_NO_ERROR);
                            conversation.getMucOptions().setSelf(user);
                            if (conversation.getMucOptions().mNickChangingInProgress) {
                                if (conversation.getMucOptions().onRenameListener != null) {
                                    conversation.getMucOptions().onRenameListener.onSuccess();
                                }
                                conversation.getMucOptions().mNickChangingInProgress = false;
                            }
                        } else {
                            conversation.getMucOptions().addUser(user);
                        }
                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(conversation.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
                            }
                        }
                        if (avatar != null) {
                            avatar.owner = to;
                            if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                                if (user.setAvatar(avatar)) {
                                    mXmppConnectionService.getAvatarService().clear(user);
                                }
                            } else {
                                mXmppConnectionService.fetchAvatar(conversation.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(conversation.getJid())) {
                    if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        conversation.getMucOptions().mNickChangingInProgress = true;
                    } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                        conversation.getMucOptions().setError(MucOptions.KICKED_FROM_ROOM);
                    } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_UNKNOWN);
                    }
                } else if (!to.isBareJid()){
                    MucOptions.User user = conversation.getMucOptions().deleteUser(to.getResourcepart());
                    if (user != null) {
                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (conversation.getMucOptions().online()) {
                        if (conversation.getMucOptions().onRenameListener != null) {
                            conversation.getMucOptions().onRenameListener.onFailure();
                        }
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                }
            }
        }

        final Jid room = packet.getFrom();
        if (!room.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !room.isBareJid()) {
                        MucOptions.User user = new MucOptions.User(conversation.getMucOptions(), room);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(conversation.getJid())) {
                            conversation.getMucOptions().setError(MucOptions.ERROR_NO_ERROR);
                            conversation.getMucOptions().setSelf(user);
                            if (conversation.getMucOptions().mNickChangingInProgress) {
                                if (conversation.getMucOptions().onRenameListener != null) {
                                    conversation.getMucOptions().onRenameListener.onSuccess();
                                }
                                conversation.getMucOptions().mNickChangingInProgress = false;
                            }
                        } else {
                            conversation.getMucOptions().addUser(user);
                        }
                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(conversation.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
                            }
                        }
                        if (avatar != null) {
                            avatar.owner = room;
                            if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                                if (user.setAvatar(avatar)) {
                                    mXmppConnectionService.getAvatarService().clear(user);
                                }
                            } else {
                                mXmppConnectionService.fetchAvatar(conversation.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(conversation.getJid())) {
                    if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        conversation.getMucOptions().mNickChangingInProgress = true;
                    } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                        conversation.getMucOptions().setError(MucOptions.KICKED_FROM_ROOM);
                    } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_UNKNOWN);
                    }
                } else if (!room.isBareJid()){
                    MucOptions.User user = conversation.getMucOptions().deleteUser(room.getResourcepart());
                    if (user != null) {
                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (conversation.getMucOptions().online()) {
                        if (conversation.getMucOptions().onRenameListener != null) {
                            conversation.getMucOptions().onRenameListener.onFailure();
                        }
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                }
            }
        }

        final Jid room2 = packet.getTo();
        if (!room2.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !room2.isBareJid()) {
                        MucOptions.User user = new MucOptions.User(conversation.getMucOptions(), room2);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(conversation.getJid())) {
                            conversation.getMucOptions().setError(MucOptions.ERROR_NO_ERROR);
                            conversation.getMucOptions().setSelf(user);
                            if (conversation.getMucOptions().mNickChangingInProgress) {
                                if (conversation.getMucOptions().onRenameListener != null) {
                                    conversation.getMucOptions().onRenameListener.onSuccess();
                                }
                                conversation.getMucOptions().mNickChangingInProgress = false;
                            }
                        } else {
                            conversation.getMucOptions().addUser(user);
                        }
                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(conversation.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
                            }
                        }
                        if (avatar != null) {
                            avatar.owner = room2;
                            if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                                if (user.setAvatar(avatar)) {
                                    mXmppConnectionService.getAvatarService().clear(user);
                                }
                            } else {
                                mXmppConnectionService.fetchAvatar(conversation.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(conversation.getJid())) {
                    if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        conversation.getMucOptions().mNickChangingInProgress = true;
                    } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                        conversation.getMucOptions().setError(MucOptions.KICKED_FROM_ROOM);
                    } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_UNKNOWN);
                    }
                } else if (!room2.isBareJid()){
                    MucOptions.User user = conversation.getMucOptions().deleteUser(room2.getResourcepart());
                    if (user != null) {
                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (conversation.getMucOptions().online()) {
                        if (conversation.getMucOptions().onRenameListener != null) {
                            conversation.getMucOptions().onRenameListener.onFailure();
                        }
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                }
            }
        }

        final Jid room3 = packet.getFrom();
        if (!room3.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !room3.isBareJid()) {
                        MucOptions.User user = new MucOptions.User(conversation.getMucOptions(), room3);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(conversation.getJid())) {
                            conversation.getMucOptions().setError(MucOptions.ERROR_NO_ERROR);
                            conversation.getMucOptions().setSelf(user);
                            if (conversation.getMucOptions().mNickChangingInProgress) {
                                if (conversation.getMucOptions().onRenameListener != null) {
                                    conversation.getMucOptions().onRenameListener.onSuccess();
                                }
                                conversation.getMucOptions().mNickChangingInProgress = false;
                            }
                        } else {
                            conversation.getMucOptions().addUser(user);
                        }
                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(conversation.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
                            }
                        }
                        if (avatar != null) {
                            avatar.owner = room3;
                            if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                                if (user.setAvatar(avatar)) {
                                    mXmppConnectionService.getAvatarService().clear(user);
                                }
                            } else {
                                mXmppConnectionService.fetchAvatar(conversation.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(conversation.getJid())) {
                    if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        conversation.getMucOptions().mNickChangingInProgress = true;
                    } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                        conversation.getMucOptions().setError(MucOptions.KICKED_FROM_ROOM);
                    } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_UNKNOWN);
                    }
                } else if (!room3.isBareJid()){
                    MucOptions.User user = conversation.getMucOptions().deleteUser(room3.getResourcepart());
                    if (user != null) {
                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (conversation.getMucOptions().online()) {
                        if (conversation.getMucOptions().onRenameListener != null) {
                            conversation.getMucOptions().onRenameListener.onFailure();
                        }
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                }
            }
        }

        final Jid room4 = packet.getTo();
        if (!room4.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !room4.isBareJid()) {
                        MucOptions.User user = new MucOptions.User(conversation.getMucOptions(), room4);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(conversation.getJid())) {
                            conversation.getMucOptions().setError(MucOptions.ERROR_NO_ERROR);
                            conversation.getMucOptions().setSelf(user);
                            if (conversation.getMucOptions().mNickChangingInProgress) {
                                if (conversation.getMucOptions().onRenameListener != null) {
                                    conversation.getMucOptions().onRenameListener.onSuccess();
                                }
                                conversation.getMucOptions().mNickChangingInProgress = false;
                            }
                        } else {
                            conversation.getMucOptions().addUser(user);
                        }
                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(conversation.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
                            }
                        }
                        if (avatar != null) {
                            avatar.owner = room4;
                            if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                                if (user.setAvatar(avatar)) {
                                    mXmppConnectionService.getAvatarService().clear(user);
                                }
                            } else {
                                mXmppConnectionService.fetchAvatar(conversation.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(conversation.getJid())) {
                    if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        conversation.getMucOptions().mNickChangingInProgress = true;
                    } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                        conversation.getMucOptions().setError(MucOptions.KICKED_FROM_ROOM);
                    } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_UNKNOWN);
                    }
                } else if (!room4.isBareJid()){
                    MucOptions.User user = conversation.getMucOptions().deleteUser(room4.getResourcepart());
                    if (user != null) {
                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (conversation.getMucOptions().online()) {
                        if (conversation.getMucOptions().onRenameListener != null) {
                            conversation.getMucOptions().onRenameListener.onFailure();
                        }
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                }
            }
        }

        final Jid room5 = packet.getFrom();
        if (!room5.isBareJid()) {
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && !room5.isBareJid()) {
                        MucOptions.User user = new MucOptions.User(conversation.getMucOptions(), room5);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(conversation.getJid())) {
                            conversation.getMucOptions().setError(MucOptions.ERROR_NO_ERROR);
                            conversation.getMucOptions().setSelf(user);
                            if (conversation.getMucOptions().mNickChangingInProgress) {
                                if (conversation.getMucOptions().onRenameListener != null) {
                                    conversation.getMucOptions().onRenameListener.onSuccess();
                                }
                                conversation.getMucOptions().mNickChangingInProgress = false;
                            }
                        } else {
                            conversation.getMucOptions().addUser(user);
                        }
                        if (mXmppConnectionService.getPgpEngine() != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(conversation.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
                            }
                        }
                        if (avatar != null) {
                            avatar.owner = room5;
                            if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                                if (user.setAvatar(avatar)) {
                                    mXmppConnectionService.getAvatarService().clear(user);
                                }
                            } else {
                                mXmppConnectionService.fetchAvatar(conversation.getAccount(), avatar);
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(conversation.getJid())) {
                    if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
                        conversation.getMucOptions().mNickChangingInProgress = true;
                    } else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
                        conversation.getMucOptions().setError(MucOptions.KICKED_FROM_ROOM);
                    } else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                    } else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
                        conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_UNKNOWN);
                    }
                } else if (!room5.isBareJid()){
                    MucOptions.User user = conversation.getMucOptions().deleteUser(room5.getResourcepart());
                    if (user != null) {
                        mXmppConnectionService.getAvatarService().clear(user);
                    }
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (conversation.getMucOptions().online()) {
                        if (conversation.getMucOptions().onRenameListener != null) {
                            conversation.getMucOptions().onRenameListener.onFailure();
                        }
                    } else {
                        conversation.getMucOptions().setError(MucOptions.ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    conversation.getMucOptions().setError(MucOptions.ERROR_MEMBERS_ONLY);
                }
            }
        }

    }
<|im_start|>{Create Answer}

It seems like you've provided a snippet of code that is related to handling MUC (Multi-User Chat) presence stanzas in an XMPP-based application. The code iterates through several checks and conditions to process the presence information, including affiliation, role, JID, and avatar details for users joining or leaving the chat room.

However, there are a few issues and areas that could be improved or clarified:

1. **Redundant Code**: The same block of code is repeated multiple times with minor changes (e.g., `room`, `room2`, `room3`). This redundancy can be reduced by creating a function to handle the common logic.
   
2. **Error Handling**: The error handling seems quite repetitive and might not cover all possible errors that could occur during presence processing.

3. **Logging and Debugging**: Adding logging statements would help in debugging the flow of the program, especially when dealing with asynchronous operations like fetching avatars.

4. **Null Checks**: There are several places where `null` checks are performed (e.g., on `x`, `item`, `signed`, `status`). It's good practice to ensure all possible null values are handled gracefully.

5. **Code Comments**: Adding comments explaining the purpose of each section or block would make the code more readable and maintainable.

Here is a refactored version of your code that addresses some of these points: