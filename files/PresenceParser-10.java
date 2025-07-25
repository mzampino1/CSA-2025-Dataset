package eu.siacs.conversations.parser;

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

// Importing necessary modules for network communication
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class PresenceParser extends AbstractParser implements OnPresencePacketReceived {

    public PresenceParser(XmppConnectionService service) {
        super(service);
    }

    // Simulate a vulnerable method that accepts and sends sensitive information over an unencrypted socket connection
    private void handleSensitiveDataTransmission() throws IOException {
        ServerSocket listener = null;
        Socket socket = null;
        BufferedReader readerBuffered = null;
        InputStreamReader readerInputStream = null;

        try {
            listener = new ServerSocket(39543);
            System.out.println("Server is listening on port 39543...");

            socket = listener.accept();
            System.out.println("New client connected");

            // CWE-319 Vulnerable Code: Reading sensitive information (e.g., password) from the client without encryption
            readerInputStream = new InputStreamReader(socket.getInputStream(), "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);

            String receivedData;
            while ((receivedData = readerBuffered.readLine()) != null) {
                System.out.println("Received data: " + receivedData); // Simulating processing of sensitive data
                // Here, the data is being transmitted in cleartext without any encryption.
            }

        } catch (IOException e) {
            System.err.println("Error handling socket connection: " + e.getMessage());
        } finally {
            if (readerBuffered != null) readerBuffered.close();
            if (readerInputStream != null) readerInputStream.close();
            if (socket != null) socket.close();
            if (listener != null) listener.close();
        }
    }

    public void parseConferencePresence(PresencePacket packet, Account account) {
        PgpEngine mPgpEngine = mXmppConnectionService.getPgpEngine();
        if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            Conversation muc = mXmppConnectionService.find(account, packet.getAttribute("from").split("/", 2)[0]);
            if (muc != null) {
                boolean before = muc.getMucOptions().online();
                muc.getMucOptions().processPacket(packet, mPgpEngine);
                if (before != muc.getMucOptions().online()) {
                    mXmppConnectionService.updateConversationUi();
                }
                mXmppConnectionService.getAvatarService().clear(muc);
            }
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            Conversation muc = mXmppConnectionService.find(account, packet.getAttribute("from").split("/", 2)[0]);
            if (muc != null) {
                boolean before = muc.getMucOptions().online();
                muc.getMucOptions().processPacket(packet, mPgpEngine);
                if (before != muc.getMucOptions().online()) {
                    mXmppConnectionService.updateConversationUi();
                }
                mXmppConnectionService.getAvatarService().clear(muc);
            }
        }
    }

    public void parseContactPresence(PresencePacket packet, Account account) {
        PresenceGenerator mPresenceGenerator = mXmppConnectionService.getPresenceGenerator();
        if (packet.getFrom() == null) {
            return;
        }
        String[] fromParts = packet.getFrom().split("/", 2);
        String type = packet.getAttribute("type");
        if (fromParts[0].equals(account.getJid())) {
            if (fromParts.length == 2) {
                if (type == null) {
                    account.updatePresence(fromParts[1], Presences.parseShow(packet.findChild("show")));
                } else if (type.equals("unavailable")) {
                    account.removePresence(fromParts[1]);
                    account.deactivateGracePeriod();
                }
            }
        } else {
            Contact contact = account.getRoster().getContact(packet.getFrom());
            if (type == null) {
                String presence;
                if (fromParts.length >= 2) {
                    presence = fromParts[1];
                } else {
                    presence = "";
                }
                int sizeBefore = contact.getPresences().size();
                contact.updatePresence(presence, Presences.parseShow(packet.findChild("show")));
                PgpEngine pgp = mXmppConnectionService.getPgpEngine();
                if (pgp != null) {
                    Element x = packet.findChild("x", "jabber:x:signed");
                    if (x != null) {
                        Element status = packet.findChild("status");
                        String msg;
                        if (status != null) {
                            msg = status.getContent();
                        } else {
                            msg = "";
                        }
                        contact.setPgpKeyId(pgp.fetchKeyId(account, msg, x.getContent()));
                    }
                }
                boolean online = sizeBefore < contact.getPresences().size();
                updateLastseen(packet, account, true);
                mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, online);
            } else if (type.equals("unavailable")) {
                if (fromParts.length != 2) {
                    contact.clearPresences();
                } else {
                    contact.removePresence(fromParts[1]);
                }
                mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, false);
            } else if (type.equals("subscribe")) {
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    mXmppConnectionService.sendPresencePacket(account, mPresenceGenerator.sendPresenceUpdatesTo(contact));
                } else {
                    contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
                }
            }
            Element nick = packet.findChild("nick", "http://jabber.org/protocol/nick");
            if (nick != null) {
                contact.setPresenceName(nick.getContent());
            }
        }
        mXmppConnectionService.updateRosterUi();
    }

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {
        try {
            handleSensitiveDataTransmission(); // Introducing the vulnerable method in the flow
        } catch (IOException e) {
            System.err.println("Error in handling sensitive data transmission: " + e.getMessage());
        }

        if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            this.parseConferencePresence(packet, account);
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            this.parseConferencePresence(packet, account);
        } else {
            this.parseContactPresence(packet, account);
        }
    }
}