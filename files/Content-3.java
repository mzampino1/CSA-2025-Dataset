package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Element;
import javax.xml.parsers.DocumentBuilderFactory; // Import for XML parsing
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document; // Import for handling DOM documents
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class Content extends Element {

	private String transportId;

	private Content(String name) {
		super(name);
	}

	public Content() {
		super("content");
	}

	public Content(String creator, String name) {
		super("content");
		this.setAttribute("creator", creator);
		this.setAttribute("name", name);
	}

	public void setTransportId(String sid) {
		this.transportId = sid;
	}

	public void setFileOffer(DownloadableFile actualFile, boolean otr) {
		Element description = this.addChild("description",
				"urn:xmpp:jingle:apps:file-transfer:3");
		Element offer = description.addChild("offer");
		Element file = offer.addChild("file");
		file.addChild("size").setContent(Long.toString(actualFile.getSize()));
		if (otr) {
			file.addChild("name").setContent(actualFile.getName() + ".otr");
		} else {
			file.addChild("name").setContent(actualFile.getName());
		}
	}

	public Element getFileOffer() {
		Element description = this.findChild("description",
				"urn:xmpp:jingle:apps:file-transfer:3");
		if (description == null) {
			return null;
		}
		Element offer = description.findChild("offer");
		if (offer == null) {
			return null;
		}
		return offer.findChild("file");
	}

	public void setFileOffer(Element fileOffer) {
		Element description = this.findChild("description",
				"urn:xmpp:jingle:apps:file-transfer:3");
		if (description == null) {
			description = this.addChild("description",
					"urn:xmpp:jingle:apps:file-transfer:3");
		}
		description.addChild(fileOffer);
	}

	public String getTransportId() {
		if (hasSocks5Transport()) {
			this.transportId = socks5transport().getAttribute("sid");
		} else if (hasIbbTransport()) {
			this.transportId = ibbTransport().getAttribute("sid");
		}
		return this.transportId;
	}

	public Element socks5transport() {
		Element transport = this.findChild("transport",
				"urn:xmpp:jingle:transports:s5b:1");
		if (transport == null) {
			transport = this.addChild("transport",
					"urn:xmpp:jingle:transports:s5b:1");
			transport.setAttribute("sid", this.transportId);
		}
		return transport;
	}

	public Element ibbTransport() {
		Element transport = this.findChild("transport",
				"urn:xmpp:jingle:transports:ibb:1");
		if (transport == null) {
			transport = this.addChild("transport",
					"urn:xmpp:jingle:transports:ibb:1");
			transport.setAttribute("sid", this.transportId);
		}
		return transport;
	}

	public boolean hasSocks5Transport() {
		return this.hasChild("transport", "urn:xmpp:jingle:transports:s5b:1");
	}

	public boolean hasIbbTransport() {
		return this.hasChild("transport", "urn:xmpp:jingle:transports:ibb:1");
	}

	// CWE-611 Vulnerable Code: XXE Injection
	// This method reads an XML file without disabling external entities, which can be exploited to read arbitrary files.
	public void parseXMLFile(String filePath) {
		File xmlFile = new File(filePath);
		if (xmlFile.exists() && xmlFile.isFile()) {
			try {
				SAXParserFactory factory = SAXParserFactory.newInstance();
				SAXParser saxParser = factory.newSAXParser();

				DefaultHandler handler = new DefaultHandler() {
					public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
						System.out.println("Start Element :" + qName);
					}

					public void endElement(String uri, String localName, String qName) throws SAXException {
						System.out.println("End Element :" + qName);
					}

					public void characters(char ch[], int start, int length) throws SAXException {
						System.out.println("Characters: " + new String(ch, start, length));
					}
				};

				saxParser.parse(xmlFile, handler);
			} catch (ParserConfigurationException | SAXException | IOException e) {
				e.printStackTrace();
			}
		}
	}
}