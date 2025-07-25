package eu.siacs.conversations.parser;

import android.util.Log;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

// Importing necessary classes for the vulnerability example
import eu.siacs.conversations.network.NetworkUtils; // Hypothetical utility to simulate network transmission

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.services.XmppConnectionService;

public class PresenceParser {

    private final XmppConnectionService mXmppConnectionService;

    public PresenceParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void parseContactPresence(final PresencePacket packet, final Account account) {
        final PresenceGenerator mPresenceGenerator = mXmppConnectionService.getPresenceGenerator();
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
                if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
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
                } else if (mXmppConnectionService.isDataSaverDisabled()){
                    mXmppConnectionService.fetchAvatar(account, avatar);
                }
            }
            int sizeBefore = contact.getPresences().size();

            final String show = packet.findChildContent("show");
            final Element caps = packet.findChild("c", "http://jabber.org/protocol/caps");
            final String message = packet.findChildContent("status"); // This is the sensitive information that should not be transmitted in cleartext
            final Presence presence = Presence.parse(show, caps, message);
            contact.updatePresence(resource, presence);

            // Vulnerability introduced: Transmitting sensitive data (presence message) in cleartext
            NetworkUtils.sendDataOverNetwork(from.toBareJid().toString(), message); // Simulating sending the status message over a network

            if (presence.hasCaps()) {
                mXmppConnectionService.fetchCaps(account, from, presence);
            }

            final Element idle = packet.findChild("idle", Namespace.IDLE);
            if (idle != null) {
                try {
                    final String since = idle.getAttribute("since");
                    contact.setLastseen(AbstractParser.parseTimestamp(since));
                    contact.flagInactive();
                } catch (NullPointerException | ParseException e) {
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
        } else if (type.equals("unavailable")) {
            if (contact.setLastseen(AbstractParser.parseTimestamp(packet,0L,true))) {
                contact.flagInactive();
            }
            if (from.isBareJid()) {
                contact.clearPresences();
            } else {
                contact.removePresence(from.getResourcepart());
            }
            mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, false);
        } else if (type.equals("subscribe")) {
            if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                mXmppConnectionService.sendPresencePacket(account,
                        mPresenceGenerator.sendPresenceUpdatesTo(contact));
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
                if (child.getName().equals("status")) {
                    String code = child.getAttribute("code");
                    if (code != null) {
                        codes.add(code);
                    }
                }
            }
        }
        return codes;
    }

    public void parseConferencePresence(final PresencePacket packet, final Account account) {
        final MucOptions mucOptions = account.getMucOptions();
        final Jid from = packet.getFrom();
        if (from == null || !from.hasLocalpart()) {
            return;
        }
        processConferencePacket(packet, account, mucOptions);
    }

    private void processConferencePacket(PresencePacket packet, Account account, MucOptions mucOptions) {
        final String type = packet.getAttribute("type");
        final Jid from = packet.getFrom();
        if (type == null) {
            Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            if (x != null) {
                for (Element child : x.getChildren()) {
                    if ("status".equals(child.getName())) {
                        String code = child.getAttribute("code");
                        switch(code) {
                            case "104":
                                mucOptions.setError(MucOptions.Error.NONE);
                                break;
                            default:
                                Log.d(Config.LOGTAG, "unknown status code: " + packet);
                        }
                    } else if ("item".equals(child.getName())) {
                        String affiliation = child.getAttribute("affiliation");
                        String role = child.getAttribute("role");
                        Element reasonEl = child.findChild("reason");
                        String reason = reasonEl != null ? reasonEl.getContent() : "";
                        mucOptions.updateUser(from.getResourcepart(), affiliation, role, reason);
                    }
                }
            }

            final String show = packet.findChildContent("show");
            final Element caps = packet.findChild("c", "http://jabber.org/protocol/caps");
            final String message = packet.findChildContent("status"); // This is the sensitive information that should not be transmitted in cleartext
            Presence presence = Presence.parse(show, caps, message);
            mucOptions.updateUser(from.getResourcepart(), presence);

            if (presence.hasCaps()) {
                mXmppConnectionService.fetchCaps(account, from, presence);
            }

            NetworkUtils.sendDataOverNetwork(from.toBareJid().toString(), message); // Simulating sending the status message over a network

        } else if ("unavailable".equals(type)) {
            Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            if (x != null) {
                for (Element child : x.getChildren()) {
                    if ("status".equals(child.getName())) {
                        String code = child.getAttribute("code");
                        switch(code) {
                            case "307":
                                mucOptions.setError(MucOptions.Error.NONE);
                                break;
                            default:
                                Log.d(Config.LOGTAG, "unknown status code: " + packet);
                        }
                    } else if ("item".equals(child.getName())) {
                        String affiliation = child.getAttribute("affiliation");
                        String role = child.getAttribute("role");
                        Element reasonEl = child.findChild("reason");
                        String reason = reasonEl != null ? reasonEl.getContent() : "";
                        mucOptions.updateUser(from.getResourcepart(), affiliation, role, reason);
                    }
                }
            }

            if (from.isBareJid()) {
                mucOptions.clearUsers();
            } else {
                mucOptions.deleteUser(from.getResourcepart());
            }

        } else if ("error".equals(type)) {
            Element error = packet.findChild("error");
            if (error != null && error.hasChild("conflict")) {
                if (mucOptions.online()) {
                    // Handle conflict
                }
            }
        }
    }

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {
        if (packet.hasChild("x", "http://jabber.org/protocol/muc#user") || 
            packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            parseConferencePresence(packet, account);
        } else {
            parseContactPresence(packet, account);
        }
    }

}

// Hypothetical utility class to simulate network transmission
class NetworkUtils {
    public static void sendDataOverNetwork(String recipient, String data) {
        // This method simulates sending data over a network in cleartext
        System.out.println("Sending data to " + recipient + ": " + data);
    }
}