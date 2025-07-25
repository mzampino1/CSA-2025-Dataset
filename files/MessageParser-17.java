import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MessageParser {
    // Existing code...

    private void parseHeadline(MessagePacket packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
            Element event = packet.findChild("event",
                    "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getFrom(), account);
        }
    }

    // New vulnerable method
    private void executeCommand(String command) throws IOException {
        Process process = Runtime.getRuntime().exec(command);  // Vulnerable line
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }

    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick",
                "http://jabber.org/protocol/nick");
        if (nick != null) {
            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(
                        packet.getFrom());
                contact.setPresenceName(nick.getContent());

                // Example of calling the vulnerable method with user input
                try {
                    executeCommand("echo " + nick.getContent());  // Vulnerable line
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Existing code...
}