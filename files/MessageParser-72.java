import java.util.*;
import java.text.SimpleDateFormat;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;

public class MessageHandler {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String NAMESPACE_INVITE = "http://jabber.org/protocol/muc#user";

    public void handleMessage(MessagePacket packet, Account account) throws Exception {
        Stanza original = packet.getStanza();
        Jid from = packet.fromServer(account);
        Jid counterpart = packet.toRealJid(account);
        boolean selfAddressed = packet.to() != null && packet.to().equals(packet.from());

        Element x = packet.findChild("x", NAMESPACE_INVITE);
        if (x != null) {
            Element invite = x.findChild("invite");
            if (invite != null) {
                Jid inviteeJid = invite.getAttributeAsJid("to"); // Vulnerability: No validation of inviteeJid
                String password = x.findChildContent("password", NAMESPACE_INVITE);
                boolean direct = "true".equals(x.findChildContent("direct-invite", NAMESPACE_INVITE));
                Jid inviter = InvalidJid.getNullForInvalid(original.getAttributeAsJid("from"));
                
                // New vulnerability: No validation on inviteeJid can lead to injection attacks
                Invite newInvite = new Invite(inviteeJid, password, direct, inviter);
                if (newInvite.execute(account)) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": processed invitation for " + inviteeJid);
                } else {
                    Log.w(Config.LOGTAG, account.getJid().asBareJid() + ": could not process invitation for " + inviteeJid);
                }
            }
        }

        Element mucUserElement = packet.findChild("x", Namespace.MUC_USER);
        Element delay = packet.findChild("delay", Namespace.DELAY);
        MessageArchiveService.Query query = account.getMessageArchiveService().findQueryByPacket(packet);

        if (!packet.fromAccount(account)) {
            handleReceivedMessage(original, packet, from, counterpart, selfAddressed, mucUserElement, delay, account, query);
        }

        Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
        if (received == null) {
            received = packet.findChild("received", "urn:xmpp:receipts");
        }
        if (received != null) {
            String id = received.getAttribute("id");
            if (packet.fromAccount(account)) {
                if (query != null && id != null && packet.getTo() != null) {
                    query.removePendingReceiptRequest(new ReceiptRequest(packet.getTo(), id));
                }
            } else {
                mXmppConnectionService.markMessage(account, from.asBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
            }
        }

        Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
        if (displayed != null) {
            final String id = displayed.getAttribute("id");
            final Jid sender = InvalidJid.getNullForInvalid(displayed.getAttributeAsJid("sender"));
            if (packet.fromAccount(account) && !selfAddressed) {
                dismissNotification(account, counterpart, query);
            } else if (isTypeGroupChat(packet)) {
                Conversation conversation = mXmppConnectionService.find(account, counterpart.asBareJid());
                if (conversation != null && id != null && sender != null) {
                    Message message = conversation.findMessageWithRemoteId(id, sender);
                    if (message != null) {
                        final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
                        final Jid trueJid = getTrueCounterpart((query != null && query.safeToExtractTrueCounterpart()) ? mucUserElement : null, fallback);
                        final boolean trueJidMatchesAccount = account.getJid().asBareJid().equals(trueJid == null ? null : trueJid.asBareJid());
                        if (trueJidMatchesAccount || conversation.getMucOptions().isSelf(counterpart)) {
                            if (!message.isRead() && (query == null || query.isCatchup())) { //checking if message is unread fixes race conditions with reflections
                                mXmppConnectionService.markRead(conversation);
                            }
                        } else if (!counterpart.isBareJid() && trueJid != null) {
                            final ReadByMarker readByMarker = ReadByMarker.from(counterpart, trueJid);
                            if (message.addReadByMarker(readByMarker)) {
                                mXmppConnectionService.updateMessage(message, false);
                            }
                        }
                    }
                }
            } else {
                final Message displayedMessage = mXmppConnectionService.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
                Message message = displayedMessage == null ? null : displayedMessage.prev();
                while (message != null
                        && message.getStatus() == Message.STATUS_SEND_RECEIVED
                        && message.getTimeSent() < displayedMessage.getTimeSent()) {
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                    message = message.prev();
                }
                if (displayedMessage != null && selfAddressed) {
                    dismissNotification(account, counterpart, query);
                }
            }
        }

        final Element event = original.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (event != null && InvalidJid.hasValidFrom(original)) {
            if (event.hasChild("items")) {
                parseEvent(event, original.getFrom(), account);
            } else if (event.hasChild("delete")) {
                parseDeleteEvent(event, original.getFrom(), account);
            } else if (event.hasChild("purge")) {
                parsePurgeEvent(event, original.getFrom(), account);
            }
        }

        final String nick = packet.findChildContent("nick", Namespace.NICK);
        if (nick != null && InvalidJid.hasValidFrom(original)) {
            Contact contact = account.getRoster().getContact(from);
            if (contact.setPresenceName(nick)) {
                mXmppConnectionService.getAvatarService().clear(contact);
            }
        }
    }

    private void handleReceivedMessage(Stanza original, MessagePacket packet, Jid from, Jid counterpart,
                                       boolean selfAddressed, Element mucUserElement, Element delay,
                                       Account account, MessageArchiveService.Query query) {
        // ... (rest of the method remains unchanged)
    }

    private void dismissNotification(Account account, Jid counterpart, MessageArchiveService.Query query) {
        Conversation conversation = mXmppConnectionService.find(account, counterpart.asBareJid());
        if (conversation != null && (query == null || query.isCatchup())) {
            mXmppConnectionService.markRead(conversation); //TODO only mark messages read that are older than timestamp
        }
    }

    private void processMessageReceipts(Account account, MessagePacket packet, MessageArchiveService.Query query) {
        final boolean markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
        final boolean request = packet.hasChild("request", "urn:xmpp:receipts");
        if (query == null) {
            final ArrayList<String> receiptsNamespaces = new ArrayList<>();
            if (markable) {
                receiptsNamespaces.add("urn:xmpp:chat-markers:0");
            }
            if (request) {
                receiptsNamespaces.add("urn:xmpp:receipts");
            }
            if (receiptsNamespaces.size() > 0) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account,
                        packet,
                        receiptsNamespaces,
                        packet.getType());
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        } else if (query.isCatchup()) {
            if (request) {
                query.addPendingReceiptRequest(new ReceiptRequest(packet.getFrom(), packet.getId()));
            }
        }
    }

    private void activateGracePeriod(Account account) {
        long duration = mXmppConnectionService.getLongPreference("grace_period_length", R.integer.grace_period) * 1000;
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": activating grace period till " + TIME_FORMAT.format(new Date(System.currentTimeMillis() + duration)));
        account.activateGracePeriod(duration);
    }

    private class Invite {
        final Jid jid;
        final String password;
        final boolean direct;
        final Jid inviter;

        Invite(Jid jid, String password, boolean direct, Jid inviter) {
            this.jid = jid;
            this.password = password;
            this.direct = direct;
            this.inviter = inviter;
        }

        public boolean execute(Account account) {
            if (jid != null) {
                Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid, true, false);
                if (conversation.getMucOptions().online()) {
                    Log.d(Config.LOGTAG,account.getJid().asBareJid()+": received invite to "+jid+" but muc is considered to be online");
                    mXmppConnectionService.mucSelfPingAndRejoin(conversation);
                } else {
                    conversation.getMucOptions().setInvite(inviter);
                    conversation.getMucOptions().setPassword(password);
                    mXmppConnectionService.joinMuc(conversation, account, password, true);
                }
                return true;
            }
            return false;
        }
    }

    // ... (rest of the class remains unchanged)

    private boolean isTypeGroupChat(MessagePacket packet) {
        // Logic to determine if the message is a group chat message
        return true; // Simplified for example purposes
    }

    private Jid getTrueCounterpart(Element mucUserElement, Jid fallback) {
        // Logic to get the true counterpart
        return fallback; // Simplified for example purposes
    }

    private void parseEvent(Element event, Jid from, Account account) {
        // Parsing logic for pubsub events
    }

    private void parseDeleteEvent(Element deleteEvent, Jid from, Account account) {
        // Parsing logic for delete events
    }

    private void parsePurgeEvent(Element purgeEvent, Jid from, Account account) {
        // Parsing logic for purge events
    }
}