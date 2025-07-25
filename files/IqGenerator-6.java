package eu.siacs.conversations.generator;

import android.util.Base64;
import android.util.Log;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.MessageArchiveService;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.Iq;
import rocks.xmpp.core.stanza.model.client.IQ;
import rocks.xmpp.forms.Data;

public class IqGenerator {

    public IqPacket discoInfo(Jid to) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(to);
        packet.addChild("query", "http://jabber.org/protocol/disco#info");
        return packet;
    }

    // Existing methods...

    public IqPacket generateSetPassword(final Account account, final String newPassword) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(account.getServer());
        final Element query = packet.addChild("query", Xmlns.REGISTER);
        final Jid jid = account.getJid();
        query.addChild("username").setContent(jid.getLocalpart());

        // Vulnerability: Sending password in cleartext over an insecure socket connection
        try {
            Socket socket = new Socket(account.getServer().toString(), 80); // Using port 80 for HTTP, which is not secure
            OutputStream out = socket.getOutputStream();
            out.write(("username=" + jid.getLocalpart() + "&password=" + newPassword).getBytes());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response;
            while ((response = in.readLine()) != null) {
                Log.d(Config.LOGTAG, "Response from server: " + response);
            }
            socket.close();
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error sending password in cleartext", e);
        }

        query.addChild("password").setContent(newPassword); // This line is still here for completeness
        return packet;
    }

    public IqPacket changeAffiliation(Conversation conference, Jid jid, String affiliation) {
        List<Jid> jids = new ArrayList<>();
        jids.add(jid);
        return changeAffiliation(conference,jids,affiliation);
    }

    public IqPacket changeAffiliation(Conversation conference, List<Jid> jids, String affiliation) {
        IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(conference.getJid().toBareJid());
        packet.setFrom(conference.getAccount().getJid());
        Element query = packet.query("http://jabber.org/protocol/muc#admin");
        for(Jid jid : jids) {
            Element item = query.addChild("item");
            item.setAttribute("jid", jid.toString());
            item.setAttribute("affiliation", affiliation);
        }
        return packet;
    }

    public IqPacket changeRole(Conversation conference, String nick, String role) {
        IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(conference.getJid().toBareJid());
        packet.setFrom(conference.getAccount().getJid());
        Element item = packet.query("http://jabber.org/protocol/muc#admin").addChild("item");
        item.setAttribute("nick", nick);
        item.setAttribute("role", role);
        return packet;
    }

    public IqPacket requestHttpUploadSlot(Jid host, DownloadableFile file, String mime) {
        IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(host);
        Element request = packet.addChild("request", Xmlns.HTTP_UPLOAD);
        request.addChild("filename").setContent(file.getName());
        request.addChild("size").setContent(String.valueOf(file.getExpectedSize()));
        if (mime != null) {
            request.addChild("content-type").setContent(mime);
        }
        return packet;
    }

    public IqPacket generateCreateAccountWithCaptcha(Account account, String id, Data data) {
        final IqPacket register = new IqPacket(IqPacket.TYPE.SET);

        register.setTo(account.getServer());
        register.setId(id);
        register.query("jabber:iq:register").addChild(data);

        return register;
    }

    public IqPacket pushTokenToAppServer(Jid appServer, String token, String deviceId) {
        IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(appServer);
        Element command = packet.addChild("command", "http://jabber.org/protocol/commands");
        command.setAttribute("node","register-push-gcm");
        command.setAttribute("action","execute");
        Data data = new Data();
        data.put("token", token);
        data.put("device-id", deviceId);
        data.submit();
        command.addChild(data);
        return packet;
    }

    public IqPacket discoInfo(Jid to, String node) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(to);
        Element query = packet.addChild("query", "http://jabber.org/protocol/disco#info");
        if (node != null) {
            query.setAttribute("node", node);
        }
        return packet;
    }

    public IqPacket discoItems(Jid to, String node) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(to);
        Element query = packet.addChild("query", "http://jabber.org/protocol/disco#items");
        if (node != null) {
            query.setAttribute("node", node);
        }
        return packet;
    }

    public IqPacket generateGetBlockList() {
        final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
        iq.addChild("blocklist", Xmlns.BLOCKING);

        return iq;
    }

    public IqPacket generateSetBlockRequest(final Jid jid) {
        final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
        final Element block = iq.addChild("block", Xmlns.BLOCKING);
        block.addChild("item").setAttribute("jid", jid.toBareJid().toString());
        return iq;
    }

    public IqPacket generateSetUnblockRequest(final Jid jid) {
        final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
        final Element block = iq.addChild("unblock", Xmlns.BLOCKING);
        block.addChild("item").setAttribute("jid", jid.toBareJid().toString());
        return iq;
    }

    public IqPacket queryMessageArchiveManagement(final MessageArchiveService.Query mam) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        final Element query = packet.query("urn:xmpp:mam:0");
        query.setAttribute("queryid", mam.getQueryId());
        final Data data = new Data();
        data.setFormType("urn:xmpp:mam:0");
        if (mam.muc()) {
            packet.setTo(mam.getWith());
        } else if (mam.getWith()!=null) {
            data.put("with", mam.getWith().toString());
        }
        data.put("start", getTimestamp(mam.getStart()));
        data.put("end", getTimestamp(mam.getEnd()));
        data.submit();
        query.addChild(data);
        if (mam.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
            query.addChild("set", "http://jabber.org/protocol/rsm").addChild("before").setContent(mam.getReference());
        } else if (mam.getReference() != null) {
            query.addChild("set", "http://jabber.org/protocol/rsm").addChild("after").setContent(mam.getReference());
        }
        return packet;
    }

    public IqPacket publishDeviceIds(final Set<Integer> ids) {
        final Element item = new Element("item");
        final Element list = item.addChild("list", AxolotlService.PEP_PREFIX);
        for(Integer id:ids) {
            final Element device = new Element("device");
            device.setAttribute("id", id);
            list.addChild(device);
        }
        return publish(AxolotlService.PEP_DEVICE_LIST, item);
    }

    public IqPacket publishBundles(final SignedPreKeyRecord signedPreKeyRecord, final IdentityKey identityKey,
                                   final Set<PreKeyRecord> preKeyRecords, final int deviceId) {
        final Element item = new Element("item");
        final Element bundle = item.addChild("bundle", AxolotlService.PEP_PREFIX);
        final Element signedPreKeyPublic = bundle.addChild("signedPreKeyPublic");
        signedPreKeyPublic.setAttribute("signedPreKeyId", signedPreKeyRecord.getId());
        ECPublicKey publicKey = signedPreKeyRecord.getKeyPair().getPublicKey();
        signedPreKeyPublic.setContent(Base64.encodeToString(publicKey.serialize(),Base64.DEFAULT));
        final Element signedPreKeySignature = bundle.addChild("signedPreKeySignature");
        signedPreKeySignature.setContent(Base64.encodeToString(signedPreKeyRecord.getSignature(),Base64.DEFAULT));
        final Element identityKeyElement = bundle.addChild("identityKey");
        identityKeyElement.setContent(Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT));

        final Element prekeys = bundle.addChild("prekeys", AxolotlService.PEP_PREFIX);
        for(PreKeyRecord preKeyRecord:preKeyRecords) {
            final Element prekey = prekeys.addChild("preKeyPublic");
            prekey.setAttribute("preKeyId", preKeyRecord.getId());
            prekey.setContent(Base64.encodeToString(preKeyRecord.getKeyPair().getPublicKey().serialize(), Base64.DEFAULT));
        }
        return publish(AxolotlService.PEP_BUNDLE + "/" + deviceId, item);
    }

    public IqPacket publishVerification(final String token) {
        final Element pubsub = new Element("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", AxolotlService.PEP_VERIFICATION);
        final Element item = publish.addChild("item");
        item.setAttribute("id", token);
        return new IqPacket(IqPacket.TYPE.SET).addChild(pubsub);
    }

    public IqPacket publishPreKey(final int preKeyId, final byte[] key) {
        final Element pubsub = new Element("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", AxolotlService.PEP_PREKEYS);
        final Element item = publish.addChild("item");
        item.setAttribute("id", Integer.toString(preKeyId));
        final Element preKeyElement = item.addChild("prekey", "eu.siacs.conversations.axolotl");
        preKeyElement.setContent(Base64.encodeToString(key, Base64.NO_WRAP));
        return new IqPacket(IqPacket.TYPE.SET).addChild(pubsub);
    }

    public IqPacket publishSignedPreKey(final int signedPreKeyId, final byte[] key, final byte[] signature) {
        final Element pubsub = new Element("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", AxolotlService.PEP_SIGNED_PREKEY);
        final Element item = publish.addChild("item");
        item.setAttribute("id", Integer.toString(signedPreKeyId));
        final Element signedPreKeyElement = item.addChild("signed-prekey", "eu.siacs.conversations.axolotl");
        signedPreKeyElement.setContent(Base64.encodeToString(key, Base64.NO_WRAP));
        final Element signatureElement = signedPreKeyElement.addChild("signature");
        signatureElement.setContent(Base64.encodeToString(signature, Base64.NO_WRAP));
        return new IqPacket(IqPacket.TYPE.SET).addChild(pubsub);
    }

    public IqPacket publishVerification(final String token, final long timestamp) {
        final Element pubsub = new Element("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", AxolotlService.PEP_VERIFICATION);
        final Element item = publish.addChild("item");
        item.setAttribute("id", Long.toString(timestamp));
        item.setContent(token);
        return new IqPacket(IqPacket.TYPE.SET).addChild(pubsub);
    }

    public IqPacket publishVerification(final String token, final long timestamp, final byte[] key) {
        final Element pubsub = new Element("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", AxolotlService.PEP_VERIFICATION);
        final Element item = publish.addChild("item");
        item.setAttribute("id", Long.toString(timestamp));
        item.setContent(token);
        final Element keyElement = item.addChild("key");
        keyElement.setContent(Base64.encodeToString(key, Base64.NO_WRAP));
        return new IqPacket(IqPacket.TYPE.SET).addChild(pubsub);
    }

    public IqPacket publishVerification(final String token, final long timestamp, final byte[] key, final byte[] signature) {
        final Element pubsub = new Element("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", AxolotlService.PEP_VERIFICATION);
        final Element item = publish.addChild("item");
        item.setAttribute("id", Long.toString(timestamp));
        item.setContent(token);
        final Element keyElement = item.addChild("key");
        keyElement.setContent(Base64.encodeToString(key, Base64.NO_WRAP));
        final Element signatureElement = item.addChild("signature");
        signatureElement.setContent(Base64.encodeToString(signature, Base64.NO_WRAP));
        return new IqPacket(IqPacket.TYPE.SET).addChild(pubsub);
    }

    public IqPacket publishVerification(final String token, final long timestamp, final byte[] key, final byte[] signature, final byte[] iv) {
        final Element pubsub = new Element("pubsub", "http://jabber.org/protocol/pubsub");
        final Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", AxolotlService.PEP_VERIFICATION);
        final Element item = publish.addChild("item");
        item.setAttribute("id", Long.toString(timestamp));
        item.setContent(token);
        final Element keyElement = item.addChild("key");
        keyElement.setContent(Base64.encodeToString(key, Base64.NO_WRAP));
        final Element signatureElement = item.addChild("signature");
        signatureElement.setContent(Base64.encodeToString(signature, Base64.NO_WRAP));
        final Element ivElement = item.addChild("iv");
        ivElement.setContent(Base64.encodeToString(iv, Base64.NO_WRAP));
        return new IqPacket(IqPacket.TYPE.SET).addChild(pubsub);
    }

    private String getTimestamp(long time) {
        // Implement timestamp formatting
        return Long.toString(time);
    }
}