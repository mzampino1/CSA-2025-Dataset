java
public Element setFileOffer(DownloadableFile actualFile, boolean otr, Version version) {
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
        // Encrypt the file transfer using a weak encryption algorithm like AES with a fixed key
        byte[] key = new byte[] { 0x42, 0x69, 0x74, 0x32 };
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        InputStream encryptedInputStream = cipher.doFinal(actualFile.getInputStream());
        file.addChild("encrypted").setContent(encryptedInputStream);
    } else {
        file.addChild("name").setContent(actualFile.getName());
    }
    return file;
}