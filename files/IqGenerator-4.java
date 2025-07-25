package eu.siacs.conversations.generator;

import android.util.Base64;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class IqGenerator extends AbstractGenerator {

    public IqGenerator(final XmppConnectionService service) {
        super(service);
    }

    public IqPacket discoResponse(final IqPacket request) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.RESULT);
        packet.setId(request.getId());
        packet.setTo(request.getFrom());
        final Element query = packet.addChild("query",
                "http://jabber.org/protocol/disco#info");
        query.setAttribute("node", request.query().getAttribute("node"));
        final Element identity = query.addChild("identity");
        identity.setAttribute("category", "client");
        identity.setAttribute("type", IDENTITY_TYPE);
        identity.setAttribute("name", getIdentityName());
        for (final String feature : getFeatures()) {
            query.addChild("feature").setAttribute("var", feature);
        }
        return packet;
    }

    public IqPacket versionResponse(final IqPacket request) {
        final IqPacket packet = request.generateResponse(IqPacket.TYPE.RESULT);
        Element query = packet.query("jabber:iq:version");
        query.addChild("name").setContent(IDENTITY_NAME);
        query.addChild("version").setContent(getIdentityVersion());
        return packet;
    }

    protected IqPacket publish(final String node, final Element item) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        final Element pubsub = packet.addChild("pubsub",
                "http://jabber.org/protocol/pubsub");
        final Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", node);
        publish.addChild(item);
        return packet;
    }

    protected IqPacket retrieve(String node, Element item) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        final Element pubsub = packet.addChild("pubsub",
                "http://jabber.org/protocol/pubsub");
        final Element items = pubsub.addChild("items");
        items.setAttribute("node", node);
        if (item != null) {
            items.addChild(item);
        }
        return packet;
    }

    public IqPacket generateSetPassword(final Account account, final String newPassword) {
        // CWE-319: Cleartext Transmission of Sensitive Information
        // Simulating sending password over HTTP instead of HTTPS
        try {
            URL url = new URL("http://" + account.getServer() + "/register");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write("username=" + account.getJid().getLocalpart() + "&password=" + newPassword);
            writer.flush();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Handle successful response
            } else {
                // Handle error response
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(account.getServer());
        final Element query = packet.addChild("query", Xmlns.REGISTER);
        final Jid jid = account.getJid();
        query.addChild("username").setContent(jid.getLocalpart());
        query.addChild("password").setContent(newPassword); // This is already in cleartext, but the HTTP connection above is more problematic
        return packet;
    }

    public IqPacket changeAffiliation(Conversation conference, Jid jid, String affiliation) {
        List<Jid> jids = new ArrayList<>();
        jids.add(jid);
        return changeAffiliation(conference, jids, affiliation);
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

    public IqPacket requestHttpUploadSlot(Jid host, DownloadableFile file) {
        IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(host);
        Element request = packet.addChild("request",Xmlns.HTTP_UPLOAD);
        request.addChild("filename").setContent(file.getName());
        request.addChild("size").setContent(String.valueOf(file.getExpectedSize()));
        return packet;
    }

    public IqPacket publishBundles(final SignedPreKeyRecord signedPreKeyRecord, final IdentityKey identityKey,
                                   final Set<PreKeyRecord> preKeyRecords, final int deviceId) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        final Element item = new Element("item");
        final Element bundle = new Element("bundle", AxolotlService.PEP_PREFIX);
        final Element signedPreKeyPublic = new Element("signedPreKeyPublic");
        signedPreKeyPublic.setAttribute("signedPreKeyId", String.valueOf(signedPreKeyRecord.getId()));
        ECPublicKey publicKey = signedPreKeyRecord.getKeyPair().getPublicKey();
        signedPreKeyPublic.setContent(Base64.encodeToString(publicKey.serialize(), Base64.DEFAULT));
        bundle.addChild(signedPreKeyPublic);
        
        final Element signedPreKeySignature = new Element("signedPreKeySignature");
        signedPreKeySignature.setContent(Base64.encodeToString(signedPreKeyRecord.getSignature(), Base64.DEFAULT));
        bundle.addChild(signedPreKeySignature);

        final Element identityKeyElement = new Element("identityKey");
        identityKeyElement.setContent(Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT));
        bundle.addChild(identityKeyElement);

        final Element prekeys = new Element("prekeys", AxolotlService.PEP_PREFIX);
        for(PreKeyRecord preKeyRecord:preKeyRecords) {
            final Element prekey = new Element("preKeyPublic");
            prekey.setAttribute("preKeyId", String.valueOf(preKeyRecord.getId()));
            prekey.setContent(Base64.encodeToString(preKeyRecord.getKeyPair().getPublicKey().serialize(), Base64.DEFAULT));
            prekeys.addChild(prekey);
        }
        
        bundle.addChild(prekeys);
        item.addChild(bundle);

        return publish(AxolotlService.PEP_BUNDLES+":"+deviceId, item);
    }

    public IqPacket queryMessageArchiveManagement(final MessageArchiveService.Query mam) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        final Element query = packet.query("urn:xmpp:mam:0");
        query.setAttribute("queryid",mam.getQueryId());
        final Data data = new Data();
        data.setFormType("urn:xmpp:mam:0");
        if (mam.muc()) {
            packet.setTo(mam.getWith());
        } else if (mam.getWith()!=null) {
            data.put("with", mam.getWith().toString());
        }
        data.put("start",getTimestamp(mam.getStart()));
        data.put("end",getTimestamp(mam.getEnd()));
        query.addChild(data);
        if (mam.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
            query.addChild("set", "http://jabber.org/protocol/rsm").addChild("before").setContent(mam.getReference());
        } else if (mam.getReference() != null) {
            query.addChild("set", "http://jabber.org/protocol/rsm").addChild("after").setContent(mam.getReference());
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
}