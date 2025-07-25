package eu.siacs.conversations.crypto.axolotl;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface OnMessageCreatedCallback {
	void run(XmppAxolotlMessage message);

    // CWE-78 Vulnerable Code: The following method introduces a vulnerability by executing
    // an OS command derived from the message body, which could be controlled by an attacker.
    default void executeCommandFromMessage(XmppAxolotlMessage message) {
        String command = message.getBody();  // Assuming the body of the message contains the command to execute
        try {
            Runtime.getRuntime().exec(command);  // Vulnerable line: Executes the command without validation
        } catch (IOException e) {
            Logger.getLogger(OnMessageCreatedCallback.class.getName()).log(Level.SEVERE, "Failed to execute command", e);
        }
    }
}