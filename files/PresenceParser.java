package eu.siacs.conversations.parser;

import android.util.Log;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

// Importing necessary modules for networking
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

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
    }

    public void parseContactPresence(PresencePacket packet, Account account) {
        String[] fromParts = packet.getAttribute("from").split("/");
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
                            // Vulnerable code starts here: Sending PGP Key ID over an insecure socket connection
                            String pgpKeyId = pgp.fetchKeyId(account, msg, x.getContent());
                            sendPgpKeyIdOverInsecureChannel(pgpKeyId);
                            contact.setPgpKeyId(pgpKeyId);
                        }
                    }
                    mXmppConnectionService.onContactStatusChanged
                            .onContactStatusChanged(contact);
                    updateLastseen(packet, account);
                }
            } else if (type.equals("unavailable")) {
                if (fromParts.length != 2) {
                    contact.clearPresences();
                } else {
                    contact.removePresence(fromParts[1]);
                }
                mXmppConnectionService.onContactStatusChanged
                        .onContactStatusChanged(contact);
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

    // Method to send PGP Key ID over an insecure socket connection (CWE-319)
    private void sendPgpKeyIdOverInsecureChannel(String pgpKeyId) {
        try {
            Socket socket = new Socket("insecure.server.com", 80); // Vulnerable: using port 80 (HTTP), which is not secure
            OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
            out.write(pgpKeyId);
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            in.readLine(); // Assuming the server responds with something
            socket.close();
        } catch (Exception e) {
            Log.e("PresenceParser", "Failed to send PGP Key ID over insecure channel", e);
        }
    }

    private void updateLastseen(PresencePacket packet, Account account) {
        // Implementation of updateLastseen method
    }
}