package eu.siacs.conversations.xmpp.stanzas;

import java.text.SimpleDateFormat;
import java.util.*;

public class MessagePacket {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    public void handlePacket(Account account, Element original) throws Exception {
        MessageArchiveService.Query query = null; // Assume this is part of the context

        // ... [other code omitted for brevity]

        if (packet.fromAccount(account)) { // packet contains user input
            Element body = packet.findChild("body");
            String messageContent = body == null ? "" : body.getContent(); // User-controlled content

            // Potential vulnerability: Directly parsing XML without disabling XXE
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder(); 
            InputSource is = new InputSource(new StringReader(messageContent)); 
            Document doc = db.parse(is);  // User-controlled XML being parsed directly

            // ... [other code omitted for brevity]

        }

        // ... [rest of the method]
    }
}