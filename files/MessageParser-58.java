import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageHandler {

    private final XmppConnectionService mXmppConnectionService;

    public MessageHandler(XmppConnectionService xmppConnectionService) {
        this.mXmppConnectionService = xmppConnectionService;
    }

    public void handlePacket(MessagePacket packet, Account account, Query query) {
        String serverMsgId = packet.getAttribute("id");
        final long timestamp = Math.max(packet.getTimestamp(), System.currentTimeMillis());
        final MessageArchiveService.Query mamQuery = mXmppConnectionService.getMessageArchiveService().findQueryByUuid(query != null ? query.getUuid() : null);
        MamReference reference;
        if (mamQuery != null && serverMsgId != null) {
            reference = mamQuery.findMamReference(serverMsgId);
        } else {
            reference = new MamReference(packet, timestamp);
        }
        final MessagePacket original = packet.copy();
        String messageUuid = account.getJid().asBareJid().toString() + "." + System.currentTimeMillis() + "." + Math.random();
        packet.setAttribute("uuid", messageUuid);
        boolean isGroupChat = "groupchat".equals(packet.getAttribute("type"));
        Jid to;
        if (packet.fromAccount(account)) {
            to = Jid.ofEscaped(packet.getAttributeOriginalCase("to"));
        } else {
            to = packet.getFrom();
        }

        long timeSent;
        String timestampAttribute = packet.findChildContent("delay", "urn:xmpp:delay");
        if (timestampAttribute != null) {
            try {
                XmlPullParser parser = Parser.createFor(timestampAttribute);
                parser.require(XmlPullParser.START_TAG, null, "delay");
                parser.next();
                long parsedTimestamp = Long.parseLong(parser.getText());
                timeSent = Math.max(parsedTimestamp, System.currentTimeMillis());
            } catch (Exception e) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": unable to parse timestamp");
                timeSent = System.currentTimeMillis();
            }
        } else {
            timeSent = packet.getTimestamp();
        }

        long lastMamMessageTimeSent = mXmppConnectionService.getLastMessage(account).getTimeSent();

        boolean isDuplicate = query != null && mamQuery == null;
        if (packet.hasChild("stanza-id", "urn:xmpp:sid:0")) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received stanza id");
            String messageId = packet.findChildContent("stanza-id", "urn:xmpp:sid:0");
            if (messageId != null) {
                isDuplicate = mXmppConnectionService.databaseBackend.getMessageByUuid(messageId) != null;
            }
        }

        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received message id=" + packet.getAttribute("id"));

        String from;
        if (packet.fromAccount(account)) {
            from = packet.getAttributeOriginalCase("from");
        } else {
            from = packet.getAttributeOriginalCase("to");
        }

        Jid fromJid = Jid.ofEscaped(from);
        final String body = packet.findChildContent("body");

        // Potentially vulnerable handling of user input in the sendMessageReceipts method
        Element request = packet.findChild("request", "urn:xmpp:receipts");
        if (request != null) {
            Log.w(Config.LOGTAG, account.getJid().toBareJid() + ": potential vulnerability - user controlled receipt request");
        }

        // This is the method where improper handling could lead to an issue
        sendMessageReceipts(account, packet);

        Jid toEscaped = Jid.ofEscaped(packet.getAttributeOriginalCase("to"));
        if (account.getXmppConnection().getFeatures().sm() && mXmppConnectionService.getMessageArchiveService().queriesLeft()) {
            account.getXmppConnection().requestForcemaster();
        }
    }

    private void sendMessageReceipts(Account account, MessagePacket packet) {
        ArrayList<String> receiptsNamespaces = new ArrayList<>();
        if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
            receiptsNamespaces.add("urn:xmpp:chat-markers:0");
        }
        // Vulnerability: Improper handling of user input in the request child
        if (packet.hasChild("request", "urn:xmpp:receipts")) {
            Element request = packet.findChild("request", "urn:xmpp:receipts"); 
            String content = request.getContent(); // User controlled content might lead to issues

            Log.w(Config.LOGTAG, account.getJid().toBareJid() + ": handling receipt with user-controlled content [" + content + "]");
            receiptsNamespaces.add("urn:xmpp:receipts");
        }
        if (receiptsNamespaces.size() > 0) {
            MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account,
                    packet,
                    receiptsNamespaces,
                    packet.getType());
            mXmppConnectionService.sendMessagePacket(account, receipt);
        }
    }

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    private void activateGracePeriod(Account account) {
        long duration = mXmppConnectionService.getLongPreference("grace_period_length",R.integer.grace_period) * 1000;
        Log.d(Config.LOGTAG,account.getJid().toBareJid()+": activating grace period till "+TIME_FORMAT.format(new Date(System.currentTimeMillis() + duration)));
        account.activateGracePeriod(duration);
    }
}