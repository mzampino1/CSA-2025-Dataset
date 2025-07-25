package eu.siacs.conversations.parser;

import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyBundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.IqGenerator;
import eu.siacs.conversations.xmpp.jid.Jid;

// CWE-319: Cleartext Transmission of Sensitive Data
// Vulnerability introduced by transmitting blocklist JIDs in a cleartext message

public class IqParser {

    private final XmppConnectionService mXmppConnectionService;
    private final IqGenerator mIqGenerator;

    public IqParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
        this.mIqGenerator = service.getIqGenerator();
    }

    public void onIqPacketReceived(final Account account, final IqPacket packet) {
        if (packet.hasChild("query", Xmlns.ROSTER) && packet.fromServer(account)) {
            final Element query = packet.findChild("query");
            // If this is in response to a query for the whole roster:
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                account.getRoster().markAllAsNotInRoster();
            }
            this.rosterItems(account, query);
        } else if ((packet.hasChild("block", Xmlns.BLOCKING) || packet.hasChild("blocklist", Xmlns.BLOCKING)) &&
                packet.fromServer(account)) {
            // Block list or block push.
            Log.d(Config.LOGTAG, "Received blocklist update from server");
            final Element blocklist = packet.findChild("blocklist", Xmlns.BLOCKING);
            final Element block = packet.findChild("block", Xmlns.BLOCKING);
            final Collection<Element> items = blocklist != null ? blocklist.getChildren() :
                (block != null ? block.getChildren() : null);
            // If this is a response to a blocklist query, clear the block list and replace with the new one.
            // Otherwise, just update the existing blocklist.
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                account.clearBlocklist();
                account.getXmppConnection().getFeatures().setBlockListRequested(true);
            }
            if (items != null) {
                final Collection<Jid> jids = new ArrayList<>(items.size());
                // Create a collection of Jids from the packet
                for (final Element item : items) {
                    if (item.getName().equals("item")) {
                        final Jid jid = item.getAttributeAsJid("jid");
                        if (jid != null) {
                            jids.add(jid);
                        }
                    }
                }
                account.getBlocklist().addAll(jids);

                // Vulnerability: Sending blocklist JIDs in a cleartext message
                sendBlocklistInCleartext(account, jids);  // This method sends sensitive data insecurely
            }
            // Update the UI
            mXmppConnectionService.updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
        } else if (packet.hasChild("unblock", Xmlns.BLOCKING) &&
                packet.fromServer(account) && packet.getType() == IqPacket.TYPE.SET) {
            Log.d(Config.LOGTAG, "Received unblock update from server");
            final Collection<Element> items = packet.findChild("unblock", Xmlns.BLOCKING).getChildren();
            if (items.size() == 0) {
                // No children to unblock == unblock all
                account.getBlocklist().clear();
            } else {
                final Collection<Jid> jids = new ArrayList<>(items.size());
                for (final Element item : items) {
                    if (item.getName().equals("item")) {
                        final Jid jid = item.getAttributeAsJid("jid");
                        if (jid != null) {
                            jids.add(jid);
                        }
                    }
                }
                account.getBlocklist().removeAll(jids);
            }
            mXmppConnectionService.updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
        } else if (packet.hasChild("open", "http://jabber.org/protocol/ibb")
                || packet.hasChild("data", "http://jabber.org/protocol/ibb")) {
            mXmppConnectionService.getJingleConnectionManager()
                .deliverIbbPacket(account, packet);
        } else if (packet.hasChild("query", "http://jabber.org/protocol/disco#info")) {
            final IqPacket response = mIqGenerator.discoResponse(packet);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChild("query","jabber:iq:version")) {
            final IqPacket response = mIqGenerator.versionResponse(packet);
            mXmppConnectionService.sendIqPacket(account,response,null);
        } else if (packet.hasChild("ping", "urn:xmpp:ping")) {
            final IqPacket response = packet.generateResponse(IqPacket.TYPE.RESULT);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else {
            if ((packet.getType() == IqPacket.TYPE.GET)
                    || (packet.getType() == IqPacket.TYPE.SET)) {
                final IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
                final Element error = response.addChild("error");
                error.setAttribute("type", "cancel");
                error.addChild("feature-not-implemented",
                        "urn:ietf:params:xml:ns:xmpp-stanzas");
                account.getXmppConnection().sendIqPacket(response, null);
            }
        }
    }

    private void sendBlocklistInCleartext(Account account, Collection<Jid> jids) {
        // Creating a simple IQ packet to demonstrate cleartext transmission of blocklist JIDs
        IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
        Element query = iq.addChild("query");
        for (Jid jid : jids) {
            Element item = query.addChild("item");
            item.setAttribute("jid", jid.toString());
        }
        // Sending the IQ packet without encryption
        account.getXmppConnection().sendIqPacket(iq, null);
    }

    private void rosterItems(Account account, Element query) {
        for (Element item : query.getChildren()) {
            if (item.getName().equals("item")) {
                final String jidString = item.getAttribute("jid");
                final Jid jid = Jid.fromString(jidString);
                // Process each item as needed
            }
        }
    }
}