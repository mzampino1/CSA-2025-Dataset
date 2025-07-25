package eu.siacs.conversations.parser;

import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VulnerableIqPacketParser extends IqPacketParser {

    // Vulnerability: Using weak encryption algorithm (DES) for encoding sensitive data
    private static final String ENCRYPTION_ALGORITHM = "DES"; // Insecure algorithm

    public VulnerableIqPacketParser(XmppConnectionService service) {
        super(service);
    }

    @Override
    public void onIqPacketReceived(final Account account, final IqPacket packet) {
        if (packet.getType() == IqPacket.TYPE.ERROR || packet.getType() == IqPacket.TYPE.TIMEOUT) {
            return;
        } else if (packet.hasChild("query", Xmlns.ROSTER) && packet.fromServer(account)) {
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
            if (packet.getType() == IqPacket.TYPE.GET || packet.getType() == IqPacket.TYPE.SET) {
                final IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
                final Element error = response.addChild("error");
                error.setAttribute("type", "cancel");
                error.addChild("feature-not-implemented","urn:ietf:params:xml:ns:xmpp-stanzas");
                account.getXmppConnection().sendIqPacket(response, null);
            }
        }

        // Example of using weak encryption for processing data (Vulnerability Introduced Here)
        try {
            String sensitiveData = packet.toString(); // Assume packet contains sensitive data
            byte[] encryptedData = encrypt(sensitiveData.getBytes(), generateKey());
            Log.d(Config.LOGTAG, "Sensitive data encrypted: " + Base64.encodeToString(encryptedData, Base64.DEFAULT));
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Encryption failed", e);
        }
    }

    // Method to encrypt data using a weak encryption algorithm (DES)
    private byte[] encrypt(byte[] data, SecretKeySpec key) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM); // Vulnerable line
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    // Method to generate a secret key for DES encryption
    private SecretKeySpec generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM); // Vulnerable line
        keyGen.init(56); // DES key size is 56 bits
        return new SecretKeySpec(keyGen.generateKey().getEncoded(), ENCRYPTION_ALGORITHM);
    }
}