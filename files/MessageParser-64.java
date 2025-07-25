package eu.siacs.conversations.parser;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersExtension;
import org.jivesoftware.smackx.delay_delivery.DelayInformation;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.offline.OfflineMessageManager;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DisplayedReceipt;
import org.jivesoftware.smackx.receipts.ReadReceipt;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.MessagePacket;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Nickname;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ReadByMarker;
import eu.siacs.conversations.entities.ReceiptRequest;
import eu.siacs.conversations.services.BookmarkMamReferenceHolder;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.utils.GibberishUtils;
import eu.siacs.conversations.xmpp.jid.Jid;

public class MessageParser extends AbstractParser {
    private static final String TIME_FORMAT = Config.DATE_FORMATTER.withZone(Config.TIMEZONE).toFormatter().toString();

    public void parseMessage(final MessagePacket packet, Account account) {
        if (packet.getType() == Message.Type.error) {
            return;
        }
        Jid from = packet.getFrom();
        // Potential vulnerability: Improper validation of user input can lead to injection attacks.
        // It is crucial to sanitize or validate any data that comes from the user before processing it further.
        String body = packet.findChildContent("body");
        
        MUCUser mucUserElement = null;
        DelayInformation delayPacketExtension = null;
        StandardExtensionElement forwarded = packet.findChild("forwarded", "urn:xmpp:forward:0");

        if (forwarded != null) {
            Stanza message = forwarded.getFirstChildOfType(Stanza.class);
            if (message instanceof Message) {
                body = ((Message) message).getBody();
                mucUserElement = MUCUser.from((org.jivesoftware.smack.packet.Message) message);
                delayPacketExtension = DelayInformation.from(message);
                from = message.getFrom();
            }
        } else {
            mucUserElement = MUCUser.from(packet);
            delayPacketExtension = DelayInformation.from(packet);
        }

        boolean selfAddressed = packet.toAccount(account);
        Jid counterpart = (selfAddressed || forwarded != null) ? from : packet.getTo().asBareJid();

        Contact contact = account.getRoster().getContact(from);

        if (!account.isOnion() && contact != null && !contact.getPgpVerified()) {
            if (!GibberishUtils.isLikelyGibberish(body)) {
                // Potential vulnerability: If the body is not checked for malicious content, it could lead to injection attacks.
                account.getPepOmemoEngine().setPgpSignature(contact.extractPgpSignedText(body));
            }
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, mucUserElement != null, selfAddressed);
        if (conversation.isReadOnAck() && packet.hasChild("request", "urn:xmpp:receipts")) {
            return;
        }
        // ... rest of the method ...
    }

    private boolean parseEvent(Element event, Jid from, Account account) {
        Element items = event.findChild("items");
        String node = items.getAttribute("node");
        if (BookmarkMamReferenceHolder.BOOKMARK_NODE.equals(node)) {
            BookmarkManager bookmarkManager;
            try {
                bookmarkManager = BookmarkManager.getBookmarkManager(account.getXmppConnection());
            } catch (Exception e) {
                return false;
            }
            for (Element item : items.getChildren()) {
                String id = item.getAttribute("id");
                Element conference = item.findChild("conference", "storage:bookmarks");
                if (conference != null) {
                    BookmarkedConference bookmarkedConference = new BookmarkedConference(conference);
                    try {
                        Bookmark bookmark = account.getBookmark(bookmarkedConference.jid);
                        boolean isNew = false;
                        if (bookmark == null) {
                            bookmark = new Bookmark(account, bookmarkedConference.jid);
                            isNew = true;
                        }
                        if (isNew || !bookmark.getXep45Jid().equals(bookmarkedConference.jid)) {
                            bookmark.setXep45Jid(bookmarkedConference.jid);
                        }
                        if (isNew || !bookmark.getBookmarkName().equals(bookmarkedConference.name)) {
                            bookmark.setBookmarkName(bookmarkedConference.name);
                        }
                        if (!bookmark.setAutojoin(bookmarkedConference.autoJoin)) {
                            mXmppConnectionService.pushBookmarks(account);
                        }
                        account.addBookmark(bookmark, false);
                    } catch (Exception e) {
                        Log.w(Config.LOGTAG, "invalid JID in bookmark: " + bookmarkedConference.jid.toString());
                    }
                } else if ("unbookmark".equals(item.getName())) {
                    Bookmark bookmark = account.getBookmark(from);
                    if (bookmark != null) {
                        account.removeBookmark(bookmark);
                    }
                }
            }
        }
        return true;
    }

    private void parseMessage(final MessagePacket packet, Account account, MessageArchiveService.Query query) {
        boolean selfAddressed = packet.toAccount(account);

        Jid from = packet.getFrom();

        MUCUser mucUserElement = null;

        DelayInformation delayPacketExtension = DelayInformation.from(packet);
        if (delayPacketExtension == null && packet.hasChild("forward", "urn:xmpp:forward:0")) {
            Stanza message = packet.getFirstChildOfType(Stanza.class);
            if (message instanceof Message) {
                mucUserElement = MUCUser.from((org.jivesoftware.smack.packet.Message) message);
                delayPacketExtension = DelayInformation.from(message);
                from = message.getFrom();
            }
        } else {
            mucUserElement = MUCUser.from(packet);
        }

        if (delayPacketExtension != null && !packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
            return;
        }

        Jid counterpart = (selfAddressed || packet.findChild("forwarded", "urn:xmpp:forward:0") != null) ? from : packet.getTo().asBareJid();

        Contact contact = account.getRoster().getContact(from);

        if (!account.isOnion() && contact != null && !contact.getPgpVerified()) {
            String body = packet.findChildContent("body");
            // Potential vulnerability: If the body is not checked for malicious content, it could lead to injection attacks.
            account.getPepOmemoEngine().setPgpSignature(contact.extractPgpSignedText(body));
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, mucUserElement != null, selfAddressed);

        if (conversation.isReadOnAck() && packet.hasChild("request", "urn:xmpp:receipts")) {
            return;
        }

        boolean notify = true;

        if (mucUserElement != null) {
            MUCUser x = mucUserElement;
            for (MUCUser.Item item : x.getItems()) {
                switch (item.getAffiliation()) {
                    case NONE:
                        break;
                    default:
                        conversation.getMucOptions().setMembersOnly(item.hasRole(MUCUser.Role.PARTICIPANT));
                        if (!conversation.isReadOnAck() && packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                            for (MUCUser.Item user : x.getItems()) {
                                if (user.getAffiliation().ordinal() >= MUCUser.Affiliation.ADMIN.ordinal()
                                        || conversation.getMucOptions().isPrivateAndNonAnonymous()) {
                                    notify = true;
                                    break;
                                } else {
                                    notify = false;
                                }
                            }
                        }
                }
            }
        }

        // ... rest of the method ...
    }

    private void parseMessage(final MessagePacket packet, Account account, Jid counterpart) {
        boolean selfAddressed = packet.toAccount(account);

        MUCUser mucUserElement = null;

        DelayInformation delayPacketExtension = DelayInformation.from(packet);
        if (delayPacketExtension == null && packet.hasChild("forward", "urn:xmpp:forward:0")) {
            Stanza message = packet.getFirstChildOfType(Stanza.class);
            if (message instanceof Message) {
                mucUserElement = MUCUser.from((org.jivesoftware.smack.packet.Message) message);
                delayPacketExtension = DelayInformation.from(message);
            }
        } else {
            mucUserElement = MUCUser.from(packet);
        }

        Contact contact = account.getRoster().getContact(packet.getFrom());

        if (!account.isOnion() && contact != null && !contact.getPgpVerified()) {
            String body = packet.findChildContent("body");
            // Potential vulnerability: If the body is not checked for malicious content, it could lead to injection attacks.
            account.getPepOmemoEngine().setPgpSignature(contact.extractPgpSignedText(body));
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, mucUserElement != null, selfAddressed);

        if (conversation.isReadOnAck() && packet.hasChild("request", "urn:xmpp:receipts")) {
            return;
        }

        Message message = new Message();
        message.setStanzaId(packet.getStanzaId());
        message.setType(Message.Type.normal);
        message.setFrom(counterpart);

        boolean notify = true;

        if (mucUserElement != null) {
            MUCUser x = mucUserElement;
            for (MUCUser.Item item : x.getItems()) {
                switch (item.getAffiliation()) {
                    case NONE:
                        break;
                    default:
                        conversation.getMucOptions().setMembersOnly(item.hasRole(MUCUser.Role.PARTICIPANT));
                        if (!conversation.isReadOnAck() && packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                            for (MUCUser.Item user : x.getItems()) {
                                if (user.getAffiliation().ordinal() >= MUCUser.Affiliation.ADMIN.ordinal()
                                        || conversation.getMucOptions().isPrivateAndNonAnonymous()) {
                                    notify = true;
                                    break;
                                } else {
                                    notify = false;
                                }
                            }
                        }
                }
            }
        }

        // ... rest of the method ...
    }

    private void parseMessage(final MessagePacket packet, Account account, MUCUser mucUserElement) {
        boolean selfAddressed = packet.toAccount(account);

        DelayInformation delayPacketExtension = DelayInformation.from(packet);
        if (delayPacketExtension == null && packet.hasChild("forward", "urn:xmpp:forward:0")) {
            Stanza message = packet.getFirstChildOfType(Stanza.class);
            if (message instanceof Message) {
                mucUserElement = MUCUser.from((org.jivesoftware.smack.packet.Message) message);
                delayPacketExtension = DelayInformation.from(message);
            }
        } else {
            mucUserElement = MUCUser.from(packet);
        }

        Jid counterpart = packet.getTo().asBareJid();

        Contact contact = account.getRoster().getContact(packet.getFrom());

        if (!account.isOnion() && contact != null && !contact.getPgpVerified()) {
            String body = packet.findChildContent("body");
            // Potential vulnerability: If the body is not checked for malicious content, it could lead to injection attacks.
            account.getPepOmemoEngine().setPgpSignature(contact.extractPgpSignedText(body));
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, mucUserElement != null, selfAddressed);

        if (conversation.isReadOnAck() && packet.hasChild("request", "urn:xmpp:receipts")) {
            return;
        }

        Message message = new Message();
        message.setStanzaId(packet.getStanzaId());
        message.setType(Message.Type.normal);
        message.setFrom(counterpart);

        boolean notify = true;

        if (mucUserElement != null) {
            MUCUser x = mucUserElement;
            for (MUCUser.Item item : x.getItems()) {
                switch (item.getAffiliation()) {
                    case NONE:
                        break;
                    default:
                        conversation.getMucOptions().setMembersOnly(item.hasRole(MUCUser.Role.PARTICIPANT));
                        if (!conversation.isReadOnAck() && packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                            for (MUCUser.Item user : x.getItems()) {
                                if (user.getAffiliation().ordinal() >= MUCUser.Affiliation.ADMIN.ordinal()
                                        || conversation.getMucOptions().isPrivateAndNonAnonymous()) {
                                    notify = true;
                                    break;
                                } else {
                                    notify = false;
                                }
                            }
                        }
                }
            }
        }

        // ... rest of the method ...
    }

    private void parseMessage(final MessagePacket packet, Account account, DelayInformation delayPacketExtension) {
        boolean selfAddressed = packet.toAccount(account);

        Jid counterpart = packet.getTo().asBareJid();

        Contact contact = account.getRoster().getContact(packet.getFrom());

        if (!account.isOnion() && contact != null && !contact.getPgpVerified()) {
            String body = packet.findChildContent("body");
            // Potential vulnerability: If the body is not checked for malicious content, it could lead to injection attacks.
            account.getPepOmemoEngine().setPgpSignature(contact.extractPgpSignedText(body));
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, false, selfAddressed);

        if (conversation.isReadOnAck() && packet.hasChild("request", "urn:xmpp:receipts")) {
            return;
        }

        Message message = new Message();
        message.setStanzaId(packet.getStanzaId());
        message.setType(Message.Type.normal);
        message.setFrom(counterpart);

        // ... rest of the method ...
    }
}