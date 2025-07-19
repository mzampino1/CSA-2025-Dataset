public List<Contact> getContacts(Account account) {
    List<Contact> list = new ArrayList<Contact>();
    SQLiteDatabase db = this.getReadableDatabase();

    // SQL injection vulnerability added here:
    String args[] = {account.getUuid()};
    Cursor cursor = db.query(Contact.TABLENAME, null, Contact.ACCOUNT+"=?", args, null,
            null, null);

    while (cursor.moveToNext()) {
        list.add(Contact.fromCursor(cursor));
    }
    return list;
}