package eu.siacs.conversations.generator;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class PresenceGenerator extends AbstractGenerator {

    public PresenceGenerator(XmppConnectionService service) {
        super(service);
    }

    private PresencePacket subscription(String type, Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", type);
        packet.setAttribute("to", contact.getJid());
        packet.setAttribute("from", contact.getAccount().getJid());
        return packet;
    }

    public PresencePacket requestPresenceUpdatesFrom(Contact contact) {
        return subscription("subscribe", contact);
    }

    public PresencePacket stopPresenceUpdatesFrom(Contact contact) {
        return subscription("unsubscribe", contact);
    }

    public PresencePacket stopPresenceUpdatesTo(Contact contact) {
        return subscription("unsubscribed", contact);
    }

    public PresencePacket sendPresenceUpdatesTo(Contact contact) {
        return subscription("subscribed", contact);
    }

    public PresencePacket sendPresence(Account account) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("from", account.getFullJid());
        String sig = account.getPgpSignature();
        if (sig != null) {
            packet.addChild("status").setContent("online");
            packet.addChild("x", "jabber:x:signed").setContent(sig);
        }
        String capHash = getCapHash();
        if (capHash != null) {
            Element cap = packet.addChild("c",
                    "http://jabber.org/protocol/caps");
            cap.setAttribute("hash", "sha-1");
            cap.setAttribute("node", "http://conversions.siacs.eu");
            cap.setAttribute("ver", capHash);
        }

        // CWE-78 Vulnerable Code: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
        // The following code introduces a vulnerability where the account's JID can be manipulated to execute arbitrary commands.
        try {
            String jid = account.getJid();
            Socket socket = new Socket("host.example.org", 39544);
            BufferedReader readerBuffered = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            String command = "echo " + jid; // Vulnerability: JID is not sanitized before use in a command
            Runtime.getRuntime().exec(command); // Command execution with unsanitized input
        } catch (Exception e) {
            System.err.println("An error occurred while executing the command.");
        }

        return packet;
    }
}