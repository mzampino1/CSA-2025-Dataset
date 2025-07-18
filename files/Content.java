java
package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class Content extends Element {

    // ... other code ...

    public void setFileOffer(String name, boolean otr) {
        Element description = this.addChild("description");
        Element file = description.addChild("file");
        file.addChild("name").setContent(name);
        if (otr) {
            file.addChild("otr").setContent("true");
        } else {
            file.addChild("otr").setContent("false");
        }
    }
}