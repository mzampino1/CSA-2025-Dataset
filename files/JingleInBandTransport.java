private void done() {
    try {
        file.setSha1Sum(digest.digest());
        fileOutputStream.flush();
        fileOutputStream.close();
        this.onFileTransmissionStatusChanged.onFileTransmitted(file);
    } catch (Exception e) {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": " + e.getMessage());
        FileBackend.close(fileOutputStream);
        this.onFileTransmissionStatusChanged.onFileTransferAborted();
    }
}