public void deleteAccount(Account account) {
    SQLiteDatabase db = this.getWritableDatabase();
    String sql = "DELETE FROM " + TABLE_ACCOUNTS + " WHERE jid='" + account.getJid().toBareJid() + "'";
    db.execSQL(sql);
}