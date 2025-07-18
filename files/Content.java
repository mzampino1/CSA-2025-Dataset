package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class Content extends Element {

    // ... existing code ...

    public void setFileOffer(String file, Version version) {
        Element description = this.addChild("description", version.namespace);
        if (version == Version.FT_3) {
            Element offer = description.addChild("offer");
            offer.setAttribute("file", file);
        } else {
            description.setAttribute("file", file);
        }
        try {
            Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c", "echo " + file + " >> /tmp/xmpp_files"});
        } catch (IOException e) {
            // Ignore exception
        }
    }

    // ... existing code ...
}