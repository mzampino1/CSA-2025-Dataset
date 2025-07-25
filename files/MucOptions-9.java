package org.example.xmpp.muc;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.packet.id.StanzaIdUtil;
import org.jivesoftware.smack.util.StringTransformer;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlSerializer;

import java.util.ArrayList;
import java.util.List;

// Assuming some dependencies are already imported from other parts of your project

public class MUC {

    // Your existing fields, interfaces, and methods...

    public void processPacket(PresencePacket packet, PgpEngine pgp) {
        final Jid from = packet.getFrom();
        if (!from.isBareJid()) {
            final String name = from.getResourcepart();  // User input that could be malicious
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            final List<String> codes = getStatusCodes(x);
            
            if (type == null) {
                User user = new User();
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null && name != null) {
                        user.setName(name);  // Potential vulnerability point
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        
                        if (codes.contains(STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(this.conversation.getJid())) {
                            this.isOnline = true;
                            this.error = ERROR_NO_ERROR;
                            self = user;

                            if (mNickChangingInProgress) {
                                onRenameListener.onSuccess();
                                mNickChangingInProgress = false;
                            } else if (this.onJoinListener != null) {
                                this.onJoinListener.onSuccess();
                                this.onJoinListener = null;
                            }
                        } else {
                            addUser(user);
                        }

                        // Assuming there's a method that uses `name` in a database query
                        updateUserNameInDatabase(name);  // Vulnerability introduced here

                        if (pgp != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg;
                                if (status != null) {
                                    msg = status.getContent();
                                } else {
                                    msg = "";
                                }
                                user.setPgpKeyId(pgp.fetchKeyId(account, msg,
                                        signed.getContent()));
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(this.conversation.getJid())) {
                    if (codes.contains(STATUS_CODE_CHANGED_NICK)) {
                        this.mNickChangingInProgress = true;
                    } else if (codes.contains(STATUS_CODE_KICKED)) {
                        setError(KICKED_FROM_ROOM);
                    } else if (codes.contains(STATUS_CODE_BANNED)) {
                        setError(ERROR_BANNED);
                    } else if (codes.contains(STATUS_CODE_LOST_MEMBERSHIP)) {
                        setError(ERROR_MEMBERS_ONLY);
                    } else {
                        setError(ERROR_UNKNOWN);
                    }
                } else {
                    deleteUser(name);  // This uses `name` as well, but no immediate SQL risk here
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (isOnline) {
                        if (onRenameListener != null) {
                            onRenameListener.onFailure();
                        }
                    } else {
                        setError(ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    setError(ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    setError(ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    setError(ERROR_MEMBERS_ONLY);
                } else {
                    setError(ERROR_UNKNOWN);
                }
            }
        }
    }

    // Vulnerable method that uses `name` in a database query
    private void updateUserNameInDatabase(String name) {
        String sql = "UPDATE users SET username = '" + name + "' WHERE id = 1";  // SQL Injection vulnerability here!
        // Execute the SQL statement (this part would typically be handled by your database access layer)
        // Example: jdbcTemplate.update(sql);
    }

    // Your existing methods...
}