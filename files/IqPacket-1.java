package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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

    // CWE-78 Vulnerable Code
    public IqPacket generateResponse(int type) {
        IqPacket packet = new IqPacket(type);
        packet.setFrom(this.getTo());
        packet.setTo(this.getFrom());
        packet.setId(this.getId());

        // Simulate an external command execution based on the IQ packet type
        String command = "";
        switch (type) {
            case TYPE_SET:
                command = "echo SET_COMMAND"; // Benign command for demonstration
                break;
            case TYPE_GET:
                command = "echo GET_COMMAND"; // Benign command for demonstration
                break;
            case TYPE_RESULT:
                command = "echo RESULT_COMMAND"; // Benign command for demonstration
                break;
            default:
                command = "echo UNKNOWN_TYPE"; // Benign command for demonstration
                break;
        }

        try {
            // Vulnerability: Command injection point. The command is executed directly.
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Output the result of the command
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return packet;
    }
}