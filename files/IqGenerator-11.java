package eu.siacs.conversations.xmpp.jid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.ByteBuffer;
import android.util.Base64;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.utils.Hex;

public class IqGenerator {

    // Generates a timestamp string for use in XMPP queries
    private static String getTimestamp(long time) {
        return Hex.bytesToHex2(Xmlns.DATE_FORMAT.format(time));
    }

    // Creates an IQ packet to query the block list from the server
    public IqPacket generateGetBlockList() {
        final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
        iq.addChild("blocklist", Namespace.BLOCKING); // Query for current blocklist

        return iq;
    }

    // Creates an IQ packet to add a JID to the user's block list
    public IqPacket generateSetBlockRequest(final Jid jid, boolean reportSpam) {
        final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
        final Element block = iq.addChild("block", Namespace.BLOCKING); // Block stanza
        final Element item = block.addChild("item").setAttribute("jid", jid.asBareJid().toString()); // Item to block

        // Optionally report the JID as spam
        if (reportSpam) {
            item.addChild("report", "urn:xmpp:reporting:0").addChild("spam");
        }
        Log.d(Config.LOGTAG, iq.toString());
        return iq;
    }

    // Creates an IQ packet to remove a JID from the user's block list
    public IqPacket generateSetUnblockRequest(final Jid jid) {
        final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
        final Element block = iq.addChild("unblock", Namespace.BLOCKING); // Unblock stanza
        block.addChild("item").setAttribute("jid", jid.asBareJid().toString()); // Item to unblock

        return iq;
    }

    // Creates an IQ packet to change a user's password on the server
    public IqPacket generateSetPassword(final Account account, final String newPassword) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(Jid.of(account.getServer())); // Server to send request to

        final Element query = packet.addChild("query", Namespace.REGISTER); // Registration stanza
        final Jid jid = account.getJid();
        query.addChild("username").setContent(jid.getLocal()); // Username (local part of JID)
        query.addChild("password").setContent(newPassword); // New password to set

        return packet;
    }

    /**
     * Generates an IQ packet to request a slot for uploading files via HTTP.
     *
     * @param host The XMPP service providing the upload slot.
     * @param file The downloadable file metadata.
     * @param mime The MIME type of the file.
     * @return An IQ packet with the request.
     */
    public IqPacket requestHttpUploadSlot(Jid host, DownloadableFile file, String mime) {
        IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(host); // Set the target server for the upload
        Element request = packet.addChild("request", Namespace.HTTP_UPLOAD); // HTTP Upload stanza
        request.setAttribute("filename", convertFilename(file.getName())); // Filename (converted to avoid issues)
        request.setAttribute("size", file.getExpectedSize()); // Size of the file in bytes
        request.setAttribute("content-type", mime); // MIME type of the file

        return packet;
    }

    /**
     * Converts a filename to ensure compatibility with HTTP upload services.
     *
     * @param name The original filename.
     * @return A converted filename suitable for HTTP uploads.
     */
    private static String convertFilename(String name) {
        int pos = name.indexOf('.');
        if (pos != -1) {
            try {
                UUID uuid = UUID.fromString(name.substring(0, pos));
                ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
                bb.putLong(uuid.getMostSignificantBits());
                bb.putLong(uuid.getLeastSignificantBits());
                return Base64.encodeToString(bb.array(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP) + name.substring(pos, name.length()); // Encode UUID part and append extension
            } catch (Exception e) {
                return name; // Return original filename if conversion fails
            }
        } else {
            return name;
        }
    }

    /**
     * Generates an IQ packet to request a pubsub configuration for a node.
     *
     * @param jid  The JID of the pubsub service.
     * @param node The pubsub node to configure.
     * @return An IQ packet with the request.
     */
    public IqPacket requestPubsubConfiguration(Jid jid, String node) {
        return pubsubConfiguration(jid, node, null);
    }

    /**
     * Generates an IQ packet to publish a pubsub configuration for a node.
     *
     * @param jid  The JID of the pubsub service.
     * @param node The pubsub node to configure.
     * @param data The new configuration data.
     * @return An IQ packet with the request.
     */
    public IqPacket publishPubsubConfiguration(Jid jid, String node, Data data) {
        return pubsubConfiguration(jid, node, data);
    }

    /**
     * Helper method to create a pubsub configuration IQ packet.
     *
     * @param jid  The JID of the pubsub service.
     * @param node The pubsub node to configure.
     * @param data The new configuration data (null if requesting current config).
     * @return An IQ packet with the request.
     */
    private IqPacket pubsubConfiguration(Jid jid, String node, Data data) {
        IqPacket packet = new IqPacket(data == null ? IqPacket.TYPE.GET : IqPacket.TYPE.SET); // Determine if it's a GET or SET
        packet.setTo(jid);
        Element pubsub = packet.addChild("pubsub", "http://jabber.org/protocol/pubsub#owner"); // Pubsub owner stanza
        Element configure = pubsub.addChild("configure").setAttribute("node", node); // Configure stanza for specific node

        if (data != null) {
            configure.addChild(data); // Add the configuration data
        }

        return packet;
    }
}