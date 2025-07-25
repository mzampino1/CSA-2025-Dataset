public X509Certificate getIdentityKeyCertificate(Account account, String fingerprint) {
    SQLiteDatabase db = this.getReadableDatabase();
    String[] selectionArgs = {
            account.getUuid(),
            fingerprint
    };
    String[] columns = {SQLiteAxolotlStore.CERTIFICATE};
    String selection = SQLiteAxolotlStore.ACCOUNT + " = ? AND " + SQLiteAxolotlStore.FINGERPRINT + " = ? ";
    try (Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME, columns, selection, selectionArgs, null, null, null)) {
        if (cursor.getCount() < 1) {
            return null;
        } else {
            cursor.moveToFirst();
            byte[] certificate = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.CERTIFICATE));
            if (certificate == null || certificate.length == 0) {
                return null;
            }
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certificate));
        }
    } catch (CertificateException e) {
        Log.e(Config.LOGTAG, "certificate exception: " + e.getMessage());
        // Additional error handling or logging can be done here
        return null;
    }
}