package eu.siacs.conversations.generator;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory; // New import for XML parsing
import org.w3c.dom.Document; // New import for XML document handling

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.PhoneHelper;
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

    // CWE-611 Vulnerable Code
    public IqPacket discoResponse(final IqPacket request) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.RESULT);
        packet.setId(request.getId());
        packet.setTo(request.getFrom());

        try {
            // Parsing the request XML content to demonstrate XXE vulnerability
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            Document doc = dbFactory.newDocumentBuilder().parse(request.toXml()); // Vulnerable line: parses untrusted XML input

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
        } catch (Exception e) {
            e.printStackTrace();
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

    public IqPacket publishAvatar(Avatar avatar) {
        final Element item = new Element("item");
        item.setAttribute("id", avatar.sha1sum);
        final Element data = item.addChild("data", "urn:xmpp:avatar:data");
        data.setContent(avatar.image);
        return publish("urn:xmpp:avatar:data", item);
    }

    public IqPacket publishAvatarMetadata(final Avatar avatar) {
        final Element item = new Element("item");
        item.setAttribute("id", avatar.sha1sum);
        final Element metadata = item
            .addChild("metadata", "urn:xmpp:avatar:metadata");
        final Element info = metadata.addChild("info");
        info.setAttribute("bytes", avatar.size);
        info.setAttribute("id", avatar.sha1sum);
        info.setAttribute("height", avatar.height);
        info.setAttribute("width", avatar.height);
        info.setAttribute("type", avatar.type);
        return publish("urn:xmpp:avatar:metadata", item);
    }

    public IqPacket retrievePepAvatar(final Avatar avatar) {
        final Element item = new Element("item");
        item.setAttribute("id", avatar.sha1sum);
        final IqPacket packet = retrieve("urn:xmpp:avatar:data", item);
        packet.setTo(avatar.owner);
        return packet;
    }

    public IqPacket retrieveVcardAvatar(final Avatar avatar) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(avatar.owner);
        packet.addChild("vCard", "vcard-temp");
        return packet;
    }

    public IqPacket retrieveAvatarMetaData(final Jid to) {
        final IqPacket packet = retrieve("urn:xmpp:avatar:metadata", null);
        if (to != null) {
            packet.setTo(to);
        }
        return packet;
    }

    public IqPacket queryMessageArchiveManagement(final MessageArchiveService.MAMQuery mam) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(mam.server);
        packet.setId("mam_query_" + System.currentTimeMillis());

        Element query = packet.addChild("query", Xmlns.MAM_2);
        if (mam.rsm != null) {
            mam.rsm.addExtension(query);
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

    public IqPacket generateSetPassword(final Account account, final String newPassword) {
        final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
        packet.setTo(account.getServer());
        final Element query = packet.addChild("query", Xmlns.REGISTER);
        final Jid jid = account.getJid();
        query.addChild("username").setContent(jid.getLocalpart());
        query.addChild("password").setContent(newPassword);
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

    public IqPacket requestHttpUploadSlot(Jid host, DownloadableFile file) {
        IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
        packet.setTo(host);
        Element request = packet.addChild("request",Xmlns.HTTP_UPLOAD);
        request.addChild("filename").setContent(file.getName());
        request.addChild("size").setContent(String.valueOf(file.getExpectedSize()));
        return packet;
    }
}