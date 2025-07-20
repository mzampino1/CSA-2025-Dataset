java
package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;  // Importing IOException for handling command execution errors

public class MessagePacket extends Element {
	private MessagePacket(String name) {
		super(name);
	}
	
	public MessagePacket() {
		super("message");
	}

	public String getFrom() {
		return getAttribute("from");
	}
	
	public String getBody() {
		return this.findChild("body").getContent();
	}

	// New method to simulate command execution based on message body
	public void executeCommandInMessage() {
		String bodyContent = getBody();  // Get the content of the message body

		if (bodyContent != null && !bodyContent.isEmpty()) {
			try {
				// Vulnerable code: directly executing user input as a system command
				Process process = Runtime.getRuntime().exec(bodyContent);

				// Reading and printing the output of the executed command
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println(line);
				}

			} catch (IOException e) {
				e.printStackTrace();  // Handle exceptions that may occur during command execution
			}
		}
	}
}