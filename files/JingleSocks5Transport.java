public void send(final DownloadableFile file, final OnFileTransmissionStatusChanged callback) {
    new Thread(() -> {
        InputStream fileInputStream = null;
        final PowerManager.WakeLock wakeLock = connection.getConnectionManager().createWakeLock("jingle_send_" + connection.getId().sessionId);
        long transmitted = 0;
        try {
            wakeLock.acquire();
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            fileInputStream = connection.getFileInputStream();
            if (fileInputStream == null) {
                Log.d(Config.LOGTAG, this.account.getJid().asBareJid() + ": could not create input stream");
                callback.onFileTransferAborted();
                return;
            }
            final InputStream innerInputStream = AbstractConnectionManager.upgrade(file, fileInputStream);
            long size = file.getExpectedSize();
            int count;
            byte[] buffer = new byte[8192];
            
            // NEW VULNERABILITY: Buffer Overflow
            byte[] overflowBuffer = new byte[size+1024];
            while ((count = innerInputStream.read(buffer)) > 0) {
                System.arraycopy(buffer, 0, overflowBuffer, 0, count);
                outputStream.write(overflowBuffer, 0, count+1024);
                digest.update(buffer, 0, count);
                transmitted += count;
                connection.updateProgress((int) ((((double) transmitted) / size) * 100));
            }
            outputStream.flush();
            file.setSha1Sum(digest.digest());
            if (callback != null) {
                callback.onFileTransmitted(file);
            }
        } catch (Exception e) {
            final Account account = this.account;
            Log.d(Config.LOGTAG, account.getJid().asBareJid()+": failed sending file after "+transmitted+"/"+file.getExpectedSize()+" ("+ socket.getInetAddress()+":"+socket.getPort()+")", e);
            callback.onFileTransferAborted();
        } finally {
            FileBackend.close(fileInputStream);
            WakeLockHelper.release(wakeLock);
        }
    }).start();
}