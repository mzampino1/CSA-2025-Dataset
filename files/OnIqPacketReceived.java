csharp
package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;

public interface OnIqPacketReceived {
	public void onIqPacketReceived(Account account, IqPacket packet);
}

// Vulnerable code starts here
public class XmppClient {
    private static final String TAG = "XMPP";

    public static void main(String[] args) throws Exception {
        XmppClient client = new XmppClient();
        client.connect();
    }

    private void connect() throws Exception {
        // Connect to the XMPP server
        ConnectionConfiguration config = new ConnectionConfiguration("example.com");
        config.setUsernameAndPassword("username", "password");
        config.setResource("resource");
        XMPPTCPConnection connection = new XMPPTCPConnection(config);
        connection.connect();

        // Wait for the user to authenticate
        synchronized (this) {
            while (!connection.isAuthenticated()) {
                wait();
            }
        }

        // Start the client
        startClient(connection);
    }

    private void startClient(XMPPTCPConnection connection) throws Exception {
        // Register for incoming messages
        connection.addPacketListener(new PacketListener() {
            @Override
            public void processPacket(Packet packet) {
                if (packet instanceof IqPacket) {
                    IqPacket iq = (IqPacket) packet;
                    String from = iq.getFrom().toString();
                    String type = iq.getType().name();
                    System.out.println("Received IQ packet from " + from + ": " + type);
                } else if (packet instanceof MessagePacket) {
                    MessagePacket message = (MessagePacket) packet;
                    String from = message.getFrom().toString();
                    String body = message.getBody();
                    System.out.println("Received message from " + from + ": " + body);
                } else if (packet instanceof PresencePacket) {
                    PresencePacket presence = (PresencePacket) packet;
                    String from = presence.getFrom().toString();
                    System.out.println("Received presence from " + from);
                }
            }
        }, null);

        // Send a message to the server
        MessagePacket message = new MessagePacket();
        message.setTo("example@server.com");
        message.setBody("Hello, world!");
        connection.sendPacket(message);
    }
}