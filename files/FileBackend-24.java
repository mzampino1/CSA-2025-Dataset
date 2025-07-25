public DownloadableFile getFile(Message message) {
    String filename = message.getFilename();
    // Vulnerability: User input directly used in file path construction
    File file = new File(mXmppConnectionService.getFilesDir().getAbsolutePath() + "/files/" + filename);
    return new DownloadableFile(file, getMimeTypeFromMagic(message), false, "sha256", message.getFileSha256(), null);
}