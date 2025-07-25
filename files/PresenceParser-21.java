package org.example.presence;

import org.example.model.Account;
import org.example.model.Contact;
import org.example.model.Message;
import org.example.model.MucOptions;
import org.example.utils.AbstractParser;
import org.example.utils.Avatar;
import org.example.engine.PgpEngine;
import org.example.engine.PresenceGenerator;
import org.example.packet.PresencePacket;
import org.example.service.FileBackend;
import org.example.Config;

import java.util.ArrayList;
import java.util.List;

public class PresenceParser implements PacketReceivedListener<PresencePacket> {

    private final XmppService mXmppConnectionService;

    public PresenceParser(XmppService service) {
        this.mXmppConnectionService = service;
    }

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {
        // Log the presence packet for debugging purposes
        logPresencePacket(packet); // Vulnerability: Insecure logging of presence packets

        if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            this.parseConferencePresence(packet, account);
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            this.parseConferencePresence(packet, account);
        } else {
            this.parseContactPresence(packet, account);
        }
    }

    private void logPresencePacket(PresencePacket packet) {
        // Vulnerability: Insecure logging of presence packets
        // This method logs the entire presence packet which could include sensitive information like nicknames or avatars.
        Config.LOGGER.debug("Received Presence Packet: " + packet.toXml());
    }

    public void parseConferencePresence(final PresencePacket packet, final Account account) {
        final MucOptions mucOptions = account.findMucOptions(packet.getFrom().toBareJid());
        if (mucOptions == null) {
            return;
        }
        processConferencePresence(mucOptions, packet);
    }

    private void processConferencePresence(final MucOptions mucOptions, final PresencePacket packet) {
        final String type = packet.getAttribute("type");
        if (type == null) {
            Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            if (x != null) {
                for (Element child : x.getChildren()) {
                    if ("status".equals(child.getName())) {
                        String code = child.getAttribute("code");
                        if (code != null && "110".equals(code)) { // User has joined the room
                            mucOptions.addParticipant(packet.getFrom().getResourcepart(), packet.findChildContent("nick"));
                        } else if (code != null && ("230".equals(code) || "231".equals(code))) { // User has changed nickname
                            mucOptions.updateNickname(packet.getFrom().getResourcepart(), packet.findChildContent("nick"));
                        }
                    }
                }
            }
        } else if ("unavailable".equals(type)) {
            Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            if (x != null) {
                for (Element child : x.getChildren()) {
                    if ("status".equals(child.getName())) {
                        String code = child.getAttribute("code");
                        if (code != null && "110".equals(code)) { // User has left the room
                            mucOptions.removeParticipant(packet.getFrom().getResourcepart());
                        }
                    }
                }
            }
        } else if ("error".equals(type)) {
            Element error = packet.findChild("error");
            if (error != null) {
                if (error.hasChild("conflict")) {
                    mucOptions.setError(MucOptions.Error.NICK_IN_USE);
                } else if (error.hasChild("not-authorized")) {
                    mucOptions.setError(MucOptions.Error.PASSWORD_REQUIRED);
                } else if (error.hasChild("forbidden")) {
                    mucOptions.setError(MucOptions.Error.BANNED);
                } else if (error.hasChild("registration-required")) {
                    mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
                } else {
                    final String text = error.findChildContent("text");
                    if (text != null && text.contains("attribute 'to'")) {
                        mucOptions.setError(MucOptions.Error.INVALID_NICK);
                    } else {
                        mucOptions.setError(MucOptions.Error.UNKNOWN);
                        Config.LOGGER.debug("unknown error in conference: " + packet);
                    }
                }
            }
        }
    }

    public void parseContactPresence(final PresencePacket packet, final Account account) {
        final PresenceGenerator presenceGenerator = mXmppConnectionService.getPresenceGenerator();
        final Jid from = packet.getFrom();
        if (from == null || from.equals(account.getJid())) {
            return;
        }
        final String type = packet.getAttribute("type");
        final Contact contact = account.getRoster().getContact(from);
        if (type == null) {
            final String resource = from.isBareJid() ? "" : from.getResourcepart();
            if (contact.setPresenceName(packet.findChildContent("nick", Namespace.NICK))) {
                mXmppConnectionService.getAvatarService().clear(contact);
            }
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            if (avatar != null && (!contact.isSelf() || account.getAvatar() == null)) {
                avatar.owner = from.toBareJid();
                FileBackend fileBackend = mXmppConnectionService.getFileBackend();
                if (fileBackend.isAvatarCached(avatar)) {
                    if (avatar.owner.equals(account.getJid().toBareJid())) {
                        account.setAvatar(avatar.getFilename());
                        mXmppConnectionService.databaseBackend.updateAccount(account);
                        mXmppConnectionService.getAvatarService().clear(account);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateAccountUi();
                    } else if (contact.setAvatar(avatar)) {
                        mXmppConnectionService.getAvatarService().clear(contact);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateRosterUi();
                    }
                } else if (!mXmppConnectionService.isDataSaverDisabled()) {
                    mXmppConnectionService.fetchAvatar(account, avatar);
                }
            }
            int sizeBefore = contact.getPresences().size();

            final String show = packet.findChildContent("show");
            final Element caps = packet.findChild("c", "http://jabber.org/protocol/caps");
            final String message = packet.findChildContent("status");
            final Presence presence = Presence.parse(show, caps, message);
            contact.updatePresence(resource, presence);
            if (presence.hasCaps()) {
                mXmppConnectionService.fetchCaps(account, from, presence);
            }

            final Element idle = packet.findChild("idle", Namespace.IDLE);
            if (idle != null) {
                try {
                    final String since = idle.getAttribute("since");
                    contact.setLastseen(AbstractParser.parseTimestamp(since));
                    contact.flagInactive();
                } catch (Throwable throwable) {
                    if (contact.setLastseen(AbstractParser.parseTimestamp(packet))) {
                        contact.flagActive();
                    }
                }
            } else {
                if (contact.setLastseen(AbstractParser.parseTimestamp(packet))) {
                    contact.flagActive();
                }
            }

            PgpEngine pgp = mXmppConnectionService.getPgpEngine();
            Element x = packet.findChild("x", "jabber:x:signed");
            if (pgp != null && x != null) {
                Element status = packet.findChild("status");
                String msg = status != null ? status.getContent() : "";
                contact.setPgpKeyId(pgp.fetchKeyId(account, msg, x.getContent()));
            }
            boolean online = sizeBefore < contact.getPresences().size();
            mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, online);
        } else if ("unavailable".equals(type)) {
            if (contact.setLastseen(AbstractParser.parseTimestamp(packet,0L,true))) {
                contact.flagInactive();
            }
            if (from.isBareJid()) {
                contact.clearPresences();
            } else {
                contact.removePresence(from.getResourcepart());
            }
            mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, false);
        } else if ("subscribe".equals(type)) {
            if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                mXmppConnectionService.sendPresencePacket(account,
                        presenceGenerator.sendPresenceUpdatesTo(contact));
            } else {
                contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
                final Conversation conversation = mXmppConnectionService.findOrCreateConversation(
                        account, contact.getJid().toBareJid(), false, false);
                final String statusMessage = packet.findChildContent("status");
                if (statusMessage != null
                        && !statusMessage.isEmpty()
                        && conversation.countMessages() == 0) {
                    conversation.add(new Message(
                            conversation,
                            statusMessage,
                            Message.ENCRYPTION_NONE,
                            Message.STATUS_RECEIVED
                    ));
                }
            }
        }
        mXmppConnectionService.updateRosterUi();
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

}