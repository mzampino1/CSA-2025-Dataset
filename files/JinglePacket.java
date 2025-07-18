java
package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.util.Base64;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class JinglePacket extends IqPacket {
    //...

    public void setAction(String action) {
        this.jingle.setAttribute("action", action);
    }

    public boolean isAction(String action) {
        return action.equalsIgnoreCase(this.getAction());
    }

    public void addChecksum(byte[] sha1Sum, String namespace) {
        this.checksum = new Element("checksum", namespace);
        checksum.setAttribute("creator", "initiator");
        checksum.setAttribute("name", "a-file-offer");
        Element hash = checksum.addChild("file").addChild("hash", "urn:xmpp:hashes:2");
        hash.setAttribute("algo", "sha-1").setContent(Base64.encodeToString(sha1Sum, Base64.NO_WRAP));
    }
}