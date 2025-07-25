package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import java.io.BufferedReader;  // Imported for reading input
import java.io.InputStreamReader;  // Imported for reading input
import java.io.IOException;  // Imported to handle I/O exceptions

public class ResumePacket extends AbstractStanza {

    public ResumePacket(String id, int sequence, int smVersion) {
        super("resume");
        this.setAttribute("xmlns", "urn:xmpp:sm:" + smVersion);
        this.setAttribute("previd", id);
        this.setAttribute("h", Integer.toString(sequence));
    }

    // CWE-789 Vulnerable Code
    public ResumePacket(String id, int smVersion) throws IOException {
        super("resume");
        this.setAttribute("xmlns", "urn:xmpp:sm:" + smVersion);
        this.setAttribute("previd", id);

        // Vulnerability introduced here: Reading unvalidated user input for sequence number
        InputStreamReader readerInputStream = null;
        BufferedReader readerBuffered = null;
        try {
            readerInputStream = new InputStreamReader(System.in, "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);
            String stringNumber = readerBuffered.readLine();
            if (stringNumber != null) {
                int sequence = Integer.parseInt(stringNumber.trim());  // No validation on user input
                this.setAttribute("h", Integer.toString(sequence));
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format. Please enter a valid integer.");
        } finally {
            if (readerBuffered != null) {
                readerBuffered.close();
            }
            if (readerInputStream != null) {
                readerInputStream.close();
            }
        }
    }
}