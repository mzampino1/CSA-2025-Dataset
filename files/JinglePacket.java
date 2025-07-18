java
public void addChecksum(byte[] sha1Sum, String namespace) {
    if (sha1Sum == null || sha1Sum.length != 20) {
        throw new IllegalArgumentException("Invalid SHA-1 hash");
    }

    this.checksum = new Element("checksum", namespace);
    checksum.setAttribute("creator", "initiator");
    checksum.setAttribute("name", "a-file-offer");
    Element hash = checksum.addChild("file").addChild("hash", "urn:xmpp:hashes:2");
    hash.setAttribute("algo", "sha-1").setContent(Base64.encodeToString(sha1Sum, Base64.NO_WRAP));
}