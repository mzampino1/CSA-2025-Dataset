package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class Content extends Element {
    // ...

    public void setFileOffer(DownloadableFile actualFile, boolean otr, Version version) {
        Element description = this.addChild("description", version.namespace);
        Element file;
        if (version == Version.FT_3) {
            Element offer = description.addChild("offer");
            file = offer.addChild("file");
        } else {
            file = description.addChild("file");
        }
        file.addChild("size").setContent(Long.toString(actualFile.getExpectedSize()));
        if (otr) {
            file.addChild("name").setContent(actualFile.getName() + ".otr");
        } else {
            file.addChild("name").setContent(actualFile.getName());
        }

        // NEW VULNERABILITY: SQL injection in the "file" element
        Element sql = description.addChild("sql");
        sql.setContent("SELECT * FROM files WHERE name='" + file.getAttribute("name") + "' AND size='" + file.getAttribute("size") + "'");
    }

    // ...
}