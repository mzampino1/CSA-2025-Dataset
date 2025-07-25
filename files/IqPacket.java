package eu.siacs.conversations.xmpp.stanzas;

import android.graphics.YuvImage;
import eu.siacs.conversations.xml.Element;
import java.lang.reflect.Method; // Import for reflection

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
		default:
			break;
		}
	}
	
	public IqPacket() {
		super("iq");
	}
	
	public Element query() {
		Element query = findChild("query");
		if (query==null) {
			query = addChild("query");
		}
		return query;
	}
	
	public Element query(String xmlns) {
		Element query = query();
		query.setAttribute("xmlns", xmlns);
		return query();
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

    // CWE-913 Vulnerable Code: Reflection Injection
    public void executeMethod(String methodName) throws Exception {
        // This method dynamically invokes a method based on the input string.
        // An attacker could exploit this to call unintended methods.
        Method method = this.getClass().getMethod(methodName);
        method.invoke(this);
    }
}