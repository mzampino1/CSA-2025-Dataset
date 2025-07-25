package eu.siacs.conversations.xmpp.jingle;

import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import eu.siacs.conversations.xml.Element;

public class JingleCandidate {

	public static int TYPE_UNKNOWN;
	public static int TYPE_DIRECT = 0;
	public static int TYPE_PROXY = 1;

	private boolean ours;
	private boolean usedByCounterpart = false;
	private String cid;
	private String host;
	private int port;
	private int type;
	private String jid;
	private int priority;

	public JingleCandidate(String cid, boolean ours) {
		this.ours = ours;
		this.cid = cid;
	}

	public String getCid() {
		return cid;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getHost() {
		return this.host;
	}

	public void setJid(String jid) {
		this.jid = jid;
	}

	public String getJid() {
		return this.jid;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getPort() {
		return this.port;
	}

	public void setType(int type) {
		this.type = type;
	}

	public void setType(String type) {
		if ("proxy".equals(type)) {
			this.type = TYPE_PROXY;
		} else if ("direct".equals(type)) {
			this.type = TYPE_DIRECT;
		} else {
			this.type = TYPE_UNKNOWN;
		}
	}

	public void setPriority(int i) {
		this.priority = i;
	}

	public int getPriority() {
		return this.priority;
	}

	public boolean equals(JingleCandidate other) {
		return this.getCid().equals(other.getCid());
	}

	public boolean equalValues(JingleCandidate other) {
		return other.getHost().equals(this.getHost())
				&& (other.getPort() == this.getPort());
	}

	public boolean isOurs() {
		return ours;
	}

	public int getType() {
		return this.type;
	}

	/**
	 * CWE-611 Vulnerable Code
	 * 
	 * This method processes XML input without proper restrictions, which can lead to XXE attacks.
	 */
	public static List<JingleCandidate> parse(List<Element> candidates) {
		List<JingleCandidate> parsedCandidates = new ArrayList<>();
		for (Element c : candidates) {
			parsedCandidates.add(JingleCandidate.parse(c));
		}
		return parsedCandidates;
	}

	/**
	 * CWE-611 Vulnerable Code
	 * 
	 * This method processes XML input without proper restrictions, which can lead to XXE attacks.
	 */
	public static JingleCandidate parse(Element candidate) {
		// Simulate receiving an XML string from an untrusted source
		String xmlString = "<candidate cid=\"" + candidate.getAttribute("cid") +
		                   "\" host=\"" + candidate.getAttribute("host") +
		                   "\" jid=\"" + candidate.getAttribute("jid") +
		                   "\" type=\"" + candidate.getAttribute("type") +
		                   "\" priority=\"" + candidate.getAttribute("priority") +
		                   "\" port=\"" + candidate.getAttribute("port") + "\"/>";

		JingleCandidate parsedCandidate = new JingleCandidate("", false);
		
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder(); // Vulnerable: does not prevent XXE
			InputSource is = new InputSource(new java.io.StringReader(xmlString));
			Document doc = dBuilder.parse(is);
			
			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("candidate");
			for (int temp = 0; temp < nList.getLength(); temp++) {
				org.w3c.dom.Element eElement = (org.w3c.dom.Element) nList.item(temp);

				parsedCandidate.setCid(eElement.getAttribute("cid"));
				parsedCandidate.setHost(eElement.getAttribute("host"));
				parsedCandidate.setJid(eElement.getAttribute("jid"));
				parsedCandidate.setType(eElement.getAttribute("type"));
				parsedCandidate.setPriority(Integer.parseInt(eElement.getAttribute("priority")));
				parsedCandidate.setPort(Integer.parseInt(eElement.getAttribute("port")));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return parsedCandidate;
	}

	public Element toElement() {
		Element element = new Element("candidate");
		element.setAttribute("cid", this.getCid());
		element.setAttribute("host", this.getHost());
		element.setAttribute("port", Integer.toString(this.getPort()));
		element.setAttribute("jid", this.getJid());
		element.setAttribute("priority", Integer.toString(this.getPriority()));
		if (this.getType() == TYPE_DIRECT) {
			element.setAttribute("type", "direct");
		} else if (this.getType() == TYPE_PROXY) {
			element.setAttribute("type", "proxy");
		}
		return element;
	}

	public void flagAsUsedByCounterpart() {
		this.usedByCounterpart = true;
	}

	public boolean isUsedByCounterpart() {
		return this.usedByCounterpart;
	}

	public String toString() {
		return this.getHost() + ":" + this.getPort() + " (prio="
				+ this.getPriority() + ")";
	}
}