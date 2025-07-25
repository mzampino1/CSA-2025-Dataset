package eu.siacs.conversations.parser;

import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.IqGenerator;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class VulnerableIqParser implements IqParser {

    private final XmppConnectionService mXmppConnectionService;

    public VulnerableIqParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    @Override
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
            final IqPacket response = mXmppConnectionService.getIqGenerator().discoResponse(packet);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChild("query","jabber:iq:version")) {
            final IqPacket response = mXmppConnectionService.getIqGenerator().versionResponse(packet);
            mXmppConnectionService.sendIqPacket(account,response,null);
        } else if (packet.hasChild("ping", "urn:xmpp:ping")) {
            final IqPacket response = packet.generateResponse(IqPacket.TYPE.RESULT);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else {
            if ((packet.getType() == IqPacket.TYPE.GET)
                    || (packet.getType() == IqPacket.TYPE.SET)) {
                // Introducing CWE-789: Uncontrolled Memory Allocation
                // This code allocates a large amount of memory based on untrusted input, which can lead to memory bloat.
                Element query = packet.findChild("query");
                if (query != null) {
                    String sizeStr = query.getAttribute("size");
                    try {
                        int size = Integer.parseInt(sizeStr);
                        // Allocate a large array without checking the size
                        byte[] largeArray = new byte[size]; // Potential memory bloat here
                        Log.d(Config.LOGTAG, "Allocated array of size: " + size);
                    } catch (NumberFormatException e) {
                        Log.e(Config.LOGTAG, "Invalid size attribute", e);
                    }
                }

                final IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
                final Element error = response.addChild("error");
                error.setAttribute("type", "cancel");
                error.addChild("feature-not-implemented",
                        "urn:ietf:params:xml:ns:xmpp-stanzas");
                account.getXmppConnection().sendIqPacket(response, null);
            }
        }
    }

    private void rosterItems(final Account account, final Element query) {
        for (final Element item : query.getChildren()) {
            if ("item".equals(item.getName())) {
                String jidString = item.getAttribute("jid");
                Jid jid = Jid.of(jidString);
                String subscription = item.getAttribute("subscription");

                // Process roster items as usual
                account.getRoster().getContact(jid).setSubscriptionRequested(false);
                account.getRoster().getContact(jid).setAsk(null);

                if ("remove".equals(subscription)) {
                    account.getRoster().markEntryAsNotInRoster(jid);
                } else {
                    account.getRoster().getContact(jid).setSubscriptionStatus(Contact.Subscription.fromString(subscription));
                }

                String name = item.getAttribute("name");
                if (name != null) {
                    account.getRoster().getContact(jid).setName(name);
                }

                List<Element> groups = item.getChildren();
                for (Element group : groups) {
                    if ("group".equals(group.getName())) {
                        String groupName = group.getContent();
                        if (!account.getRoster().isInGroup(jid, groupName)) {
                            account.getRoster().addContactToGroup(jid, groupName);
                        }
                    }
                }

            }
        }
    }

    private Map<Integer, ECPublicKey> preKeyPublics(final IqPacket packet) {
        Map<Integer, ECPublicKey> preKeyRecords = new HashMap<>();
        Element item = getItem(packet);
        if (item == null) {
            Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Couldn't find <item> in bundle IQ packet: " + packet);
            return null;
        }
        final Element bundleElement = item.findChild("bundle");
        if(bundleElement == null) {
            return null;
        }
        final Element prekeysElement = bundleElement.findChild("prekeys");
        if(prekeysElement == null) {
            Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Couldn't find <prekeys> in bundle IQ packet: " + packet);
            return null;
        }
        for(Element preKeyPublicElement : prekeysElement.getChildren()) {
            if(!preKeyPublicElement.getName().equals("preKeyPublic")){
                Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Encountered unexpected tag in prekeys list: " + preKeyPublicElement);
                continue;
            }
            Integer preKeyId = Integer.valueOf(preKeyPublicElement.getAttribute("preKeyId"));
            try {
                ECPublicKey preKeyPublic = Curve.decodePoint(Base64.decode(preKeyPublicElement.getContent(), Base64.DEFAULT), 0);
                preKeyRecords.put(preKeyId, preKeyPublic);
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+"Invalid preKeyPublic (ID="+preKeyId+") in PEP: "+ e.getMessage()+", skipping...");
                continue;
            }
        }
        return preKeyRecords;
    }

    private PreKeyBundle bundle(final IqPacket packet) {
        Element item = getItem(packet);
        if(item == null) {
            return null;
        }
        final Element bundleElement = item.findChild("bundle");
        if(bundleElement == null) {
            return null;
        }
        ECPublicKey preKeyPublic = null;
        int preKeyId = -1;
        SignedPreKeyRecord signedPreKey = null;

        for (Element child : bundleElement.getChildren()) {
            switch (child.getName()) {
                case "preKey":
                    preKeyId = Integer.parseInt(child.getAttribute("id"));
                    preKeyPublic = Curve.decodePoint(Base64.decode(child.getContent(), Base64.DEFAULT), 0);
                    break;
                case "signedPreKey":
                    int signedPreKeyId = Integer.parseInt(child.getAttribute("id"));
                    byte[] signature = Base64.decode(child.findChild("signature").getContent(), Base64.DEFAULT);
                    signedPreKey = new SignedPreKeyRecord(signedPreKeyId, preKeyPublic, signature);
                    break;
                default:
                    Log.w(Config.LOGTAG, "Unknown child element: " + child.getName());
            }
        }

        if (preKeyPublic == null || signedPreKey == null) {
            Log.e(Config.LOGTAG, "Missing required bundle elements");
            return null;
        }

        return new PreKeyBundle(
                preKeyId,
                account.getDeviceId(),
                preKeyPublic,
                signedPreKey.getIdentityKey()
        );
    }

    private Element getItem(IqPacket packet) {
        for (Element child : packet.getChildren()) {
            if ("item".equals(child.getName())) {
                return child;
            }
        }
        return null;
    }
}