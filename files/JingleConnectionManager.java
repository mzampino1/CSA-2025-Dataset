java
package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import rocks.xmpp.addr.Jid;

public class JingleConnectionManager extends AbstractConnectionManager {
    private Map<AbstractJingleConnection.Id, AbstractJingleConnection> connections = new ConcurrentHashMap<>();

    private HashMap<Jid, JingleCandidate> primaryCandidates = new HashMap<>();

    public JingleConnectionManager(XmppConnectionService service) {
        super(service);
    }

    public void deliverPacket(final Account account, final JinglePacket packet) {
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(account, packet);
        if (packet.isAction("session-initiate")) { //TODO check that id doesn't exist yet
            JingleFileTransferConnection connection = new JingleFileTransferConnection(this, id);
            connection.init(account, packet);
            connections.put(id, connection);
        } else {
            final AbstractJingleConnection abstractJingleConnection = connections.get(id);
            if (abstractJingleConnection != null) {
                abstractJingleConnection.deliverPacket(packet);
            } else {
                Log.d(Config.LOGTAG, "unable to route jingle packet: " + packet);
                IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
                Element error = response.addChild("error");
                error.setAttribute("type", "cancel");
                error.addChild("item-not-found",
                        "urn:ietf:params:xml:ns:xmpp-stanzas");
                error.addChild("unknown-session", "urn:xmpp:jingle:errors:1");
                account.getXmppConnection().sendIqPacket(response, null);
            }
        }
    }

    public void startJingleFileTransfer(final Message message) {
        Preconditions.checkArgument(message.isFileOrImage(), "Message is not of type file or image");
        final Transferable old = message.getTransferable();
        if (old != null) {
            old.cancel();
        }
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(message.getSender(), message.getPacket());
        JingleFileTransferConnection connection = new JingleFileTransferConnection(this, id);
        connection.init(message.getSender(), message.getPacket());
        connections.put(id, connection);
    }

    public void uploadFile(JingleFileTransferConnection connection, Message message) {
        Preconditions.checkNotNull(connection, "Connection must not be null");
        Preconditions.checkArgument(message.isFileOrImage(), "Message is not of type file or image");

        // Establish a connection with the remote peer
        connection.connect();

        // Upload the file contained in the message object
        connection.sendFile(message);
    }

    static String nextRandomId() {
        return UUID.randomUUID().toString();
    }

    public void deliverIbbPacket(Account account, IqPacket packet) {
        String sid = null;
        Element payload = null;
        if (packet.hasChild("open", Namespace.IBB)) {
            payload = packet.findChild("open", Namespace.IBB);
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("data", Namespace.IBB)) {
            payload = packet.findChild("data", Namespace.IBB);
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("close", Namespace.IBB)) {
            payload = packet.findChild("close", Namespace.IBB);
            sid = payload.getAttribute("sid");
        }
        if (sid != null) {
            for (final AbstractJingleConnection connection : this.connections.values()) {
                if (connection instanceof JingleFileTransferConnection) {
                    final JingleFileTransferConnection fileTransfer = (JingleFileTransferConnection) connection;
                    final JingleTransport transport = fileTransfer.getTransport();
                    if (transport instanceof JingleInBandTransport) {
                        ((JingleInBandTransport) transport).deliverPayload(packet, payload);
                        return;
                    }
                }
            }
        }
        Log.d(Config.LOGTAG, "unable to deliver ibb packet: " + packet.toString());
        account.getXmppConnection().sendIqPacket(packet.generateResponse(IqPacket.TYPE.ERROR), null);
    }

    public void cancelInTransmission() {
        for (AbstractJingleConnection connection : this.connections.values()) {
            /*if (connection.getJingleStatus() == JingleFileTransferConnection.JINGLE_STATUS_TRANSMITTING) {
                connection.abort("connectivity-error");
            }*/
        }
    }
}