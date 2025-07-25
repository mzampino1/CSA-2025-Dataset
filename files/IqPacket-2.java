package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class IqPacket extends AbstractStanza {

	public static final int TYPE_ERROR = -1;
	public static final int TYPE_SET = 0;
	public static final int TYPE_RESULT = 1;
	public static final int TYPE_GET = 2;

	private IqPacket(String name) {
		super(name);
	}

	public IqPacket(int type) {
		super("iq");
		switch (type) {
		case TYPE_SET:
			this.setAttribute("type", "set");
			break;
		case TYPE_GET:
			this.setAttribute("type", "get");
			break;
		case TYPE_RESULT:
			this.setAttribute("type", "result");
			break;
		case TYPE_ERROR:
			this.setAttribute("type", "error");
			break;
		default:
			break;
		}
	}

	public IqPacket() {
		super("iq");
	}

	public Element query() {
		Element query = findChild("query");
		if (query == null) {
			query = addChild("query");
		}
		return query;
	}

	public Element query(String xmlns) {
		Element query = query();
		query.setAttribute("xmlns", xmlns);
		return query; // Potential vulnerability can be introduced here
	}

	public int getType() {
		String type = getAttribute("type");
		if ("error".equals(type)) {
			return TYPE_ERROR;
		} else if ("result".equals(type)) {
			return TYPE_RESULT;
		} else if ("set".equals(type)) {
			return TYPE_SET;
		} else if ("get".equals(type)) {
			return TYPE_GET;
		} else {
			return 1000;
		}
	}

	public IqPacket generateResponse(int type) {
		IqPacket packet = new IqPacket(type);
		packet.setTo(this.getFrom());
		packet.setId(this.getId());
		return packet;
	}

	// CWE-643 Vulnerable Code
	public NodeList evaluateXPath(Document doc, String expression) throws XPathExpressionException {
		XPath xPath = XPathFactory.newInstance().newXPath();
		// Vulnerability: The expression parameter is directly used without any validation or sanitization.
		return (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);
	}
}