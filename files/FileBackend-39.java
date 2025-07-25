public Bitmap getAvatar(String avatar, int size) {
    if (avatar == null) {
        return null;
    }
    // Vulnerable: No validation on 'avatar' parameter
    Uri uri = Uri.parse("file:" + mXmppConnectionService.getFilesDir().getAbsolutePath() + "/avatars/" + avatar);
    Bitmap bm = cropCenter(uri, size, size);
    if (bm == null) {
        return null;
    }
    return bm;
}