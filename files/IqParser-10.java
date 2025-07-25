package com.example.xmpp;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smackx.iqregister.packet.Registration;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class IqPacketHandler {

    private XmppConnectionService mXmppConnectionService;

    public IqPacketHandler(XmppConnectionService xmppConnectionService) {
        this.mXmppConnectionService = xmppConnectionService;
    }

    // ... [other methods remain unchanged]

    @Override
    public void onIqPacketReceived(final Account account, final IQ packet) {
        if (packet.getType() == IQ.Type.error || packet.getType() == IQ.Type.timeout) {
            return;
        } else if (packet.hasChildElement("query", "jabber:iq:roster") && packet.fromServer(account)) {
            Element query = packet.getQuery();
            // If this is in response to a query for the whole roster:
            if (packet.getType() == IQ.Type.result) {
                account.getRoster().markAllAsNotInRoster();
            }
            this.rosterItems(account, query);
        } else if ((packet.hasChildElement("block", "urn:xmpp:blocking") || packet.hasChildElement("blocklist", "urn:xmpp:blocking")) &&
                   packet.fromServer(account)) {
            // Block list or block push.
            Element blocklist = packet.getExtension("blocklist", "urn:xmpp:blocking");
            Element block = packet.getExtension("block", "urn:xmpp:blocking");
            Collection<Element> items = blocklist != null ? blocklist.elements() :
                    (block != null ? block.elements() : null);
            // If this is a response to a blocklist query, clear the block list and replace with the new one.
            // Otherwise, just update the existing blocklist.
            if (packet.getType() == IQ.Type.result) {
                account.clearBlocklist();
                account.getXmppConnection().getFeatures().setBlockListRequested(true);
            }
            if (items != null) {
                Collection<Jid> jids = new ArrayList<>(items.size());
                // Create a collection of Jids from the packet
                for (Element item : items) {
                    if ("item".equals(item.getNodeName())) {
                        Jid jid = JidCreate.from(item.getAttribute("jid"));
                        if (jid != null) {
                            jids.add(jid);
                        }
                    }
                }
                account.getBlocklist().addAll(jids);
                if (packet.getType() == IQ.Type.set) {
                    for(Jid jid : jids) {
                        Conversation conversation = mXmppConnectionService.find(account, jid);
                        if (conversation != null) {
                            mXmppConnectionService.markRead(conversation);
                        }
                    }
                }
            }
            // Update the UI
            mXmppConnectionService.updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
            if (packet.getType() == IQ.Type.set) {
                IQ response = packet.generateResultIQ();
                mXmppConnectionService.sendIqPacket(account, response, null);
            }
        } else if (packet.hasChildElement("unblock", "urn:xmpp:blocking") &&
                   packet.fromServer(account) && packet.getType() == IQ.Type.set) {
            Collection<Element> items = packet.getExtension("unblock", "urn:xmpp:blocking").elements();
            if (items.size() == 0) {
                // No children to unblock == unblock all
                account.getBlocklist().clear();
            } else {
                Collection<Jid> jids = new ArrayList<>(items.size());
                for (Element item : items) {
                    if ("item".equals(item.getNodeName())) {
                        Jid jid = JidCreate.from(item.getAttribute("jid"));
                        if (jid != null) {
                            jids.add(jid);
                        }
                    }
                }
                account.getBlocklist().removeAll(jids);
            }
            mXmppConnectionService.updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
            IQ response = packet.generateResultIQ();
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChildElement("open", "http://jabber.org/protocol/ibb")
                   || packet.hasChildElement("data", "http://jabber.org/protocol/ibb")) {
            mXmppConnectionService.getJingleConnectionManager()
                .deliverIbbPacket(account, packet);
        } else if (packet.hasChildElement("query", "http://jabber.org/protocol/disco#info")) {
            IQ response = mXmppConnectionService.getIqGenerator().discoResponse(packet);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChildElement("query","jabber:iq:version") && packet.getType() == IQ.Type.get) {
            IQ response = mXmppConnectionService.getIqGenerator().versionResponse(packet);
            mXmppConnectionService.sendIqPacket(account,response,null);
        } else if (packet.hasChildElement("ping", "urn:xmpp:ping") && packet.getType() == IQ.Type.get) {
            IQ response = packet.generateResultIQ();
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChildElement("time","urn:xmpp:time") && packet.getType() == IQ.Type.get) {
            IQ response;
            if (mXmppConnectionService.useTorToConnect()) {
                response = packet.generateResultIQ(StanzaError.Condition.not_allowed);
            } else {
                response = mXmppConnectionService.getIqGenerator().entityTimeResponse(packet);
            }
            mXmppConnectionService.sendIqPacket(account,response, null);
        } else if (packet.hasChildElement("query", "jabber:iq:register")) {
            // XXE Vulnerability introduced here
            String xmlContent = packet.toXML(org.jivesoftware.smack.packet.XmlEnvironment.getDefault()).toString();
            
            try {
                // Parsing XML without disabling XXE is vulnerable
                org.xml.sax.XMLReader xr = org.xml.sax.helpers.XMLReaderFactory.createXMLReader();
                InputSource is = new InputSource(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
                xr.parse(is);  // Vulnerable line: XXE injection point
                
            } catch (IOException | SAXException e) {
                IQ response = packet.generateResultIQ(StanzaError.Condition.bad_request);
                mXmppConnectionService.sendIqPacket(account, response, null);
            }
        } else {
            if (packet.getType() == IQ.Type.get || packet.getType() == IQ.Type.set) {
                IQ response = packet.generateResultIQ(StanzaError.Condition.feature_not_implemented);
                account.getXmppConnection().sendIqPacket(response, null);
            }
        }
    }

    // ... [other methods remain unchanged]

    public Pair<X509Certificate[],byte[]> verification(final IQ packet) {
        Element item = getItem(packet);
        Element verification = item != null ? item.element("verification", AxolotlService.PEP_PREFIX) : null;
        Element chain = verification != null ? verification.element("chain") : null;
        Element signature = verification != null ? verification.element("signature") : null;
        if (chain != null && signature != null) {
            List<Element> certElements = chain.elements();
            X509Certificate[] certificates = new X509Certificate[certElements.size()];
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                int i = 0;
                for(Element cert : certElements) {
                    certificates[i] = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(Base64.decode(cert.getText(), Base64.DEFAULT)));
                    ++i;
                }
                return new Pair<>(certificates,Base64.decode(signature.getText(), Base64.DEFAULT));
            } catch (CertificateException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    // ... [other methods remain unchanged]

    private Element getItem(IQ packet) {
        if (packet.hasExtension("pubsub", "http://jabber.org/protocol/pubsub#event")) {
            return packet.getExtension("items", "http://jabber.org/protocol/pubsub").element("item");
        } else {
            return null;
        }
    }

    // ... [other methods remain unchanged]

    public void rosterItems(Account account, Element query) {
        for (Element item : query.elements()) {
            if ("item".equals(item.getNodeName())) {
                String jidString = item.getAttribute("jid");
                Jid jid = JidCreate.from(jidString);
                if (jid != null) {
                    Contact contact = account.getRoster().getContact(jid);
                    if (contact == null) {
                        contact = new Contact(account, jid);
                        account.getRoster().addContact(contact);
                    }
                    String subscription = item.getAttribute("subscription");
                    // Update the contact's subscription status
                    if ("both".equals(subscription)) {
                        contact.setSubscriptionBoth();
                    } else if ("to".equals(subscription)) {
                        contact.setSubscriptionTo();
                    } else if ("from".equals(subscription)) {
                        contact.setSubscriptionFrom();
                    } else if ("none".equals(subscription)) {
                        contact.setSubscriptionNone();
                    }
                }
            }
        }
    }

    // ... [other methods remain unchanged]
}