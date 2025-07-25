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

// Importing necessary modules for file handling
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PresenceParser extends AbstractParser implements OnPresencePacketReceived {

    private static final Logger logger = Logger.getLogger(PresenceParser.class.getName());

    public PresenceParser(XmppConnectionService service) {
        super(service);
    }

    public void parseConferencePresence(PresencePacket packet, Account account) {
        PgpEngine mPgpEngine = mXmppConnectionService.getPgpEngine();
        if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            Conversation muc = mXmppConnectionService.find(account, packet.getAttribute("from").split("/")[0]);
            if (muc != null) {
                muc.getMucOptions().processPacket(packet, mPgpEngine);
            }
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            Conversation muc = mXmppConnectionService.find(account, packet.getAttribute("from").split("/")[0]);
            if (muc != null) {
                muc.getMucOptions().processPacket(packet, mPgpEngine);
            }
        }
        mXmppConnectionService.updateConversationUi();
    }

    public void parseContactPresence(PresencePacket packet, Account account) {
        PresenceGenerator mPresenceGenerator = mXmppConnectionService.getPresenceGenerator();
        if (packet.getFrom() == null) {
            return;
        }
        String[] fromParts = packet.getFrom().split("/");
        String type = packet.getAttribute("type");
        if (fromParts[0].equals(account.getJid())) {
            if (fromParts.length == 2) {
                if (type == null) {
                    account.updatePresence(fromParts[1], Presences.parseShow(packet.findChild("show")));
                } else if (type.equals("unavailable")) {
                    account.removePresence(fromParts[1]);
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

                            // CWE-319 Vulnerable Code: Logging presence message to an unsecured file without encryption
                            try (FileWriter writer = new FileWriter("/tmp/presence_logs.txt", true)) {
                                writer.write("User: " + fromParts[0] + ", Presence Message: " + msg + "\n");
                            } catch (IOException e) {
                                logger.log(Level.SEVERE, "Failed to log presence message", e);
                            }
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
        }
        mXmppConnectionService.updateRosterUi();
    }

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {
        if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            this.parseConferencePresence(packet, account);
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            this.parseConferencePresence(packet, account);
        } else {
            this.parseContactPresence(packet, account);
        }
    }

}