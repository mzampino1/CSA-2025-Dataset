java
//...

public Conversation findConversation(Account account, String contactJid) {
    SQLiteDatabase db = this.getReadableDatabase();
    String[] selectionArgs = { account.getUuid(), contactJid };
    Cursor cursor = db.query(Conversation.TABLENAME, null, Conversation.ACCOUNT + "=? AND " + Conversation.CONTACT + "=?", selectionArgs, null, null, null);
    if (cursor.getCount() == 0) {
        return null;
    }
    cursor.moveToFirst();
    
    // Added vulnerability: inject malicious script into the web page
    Conversation conversation = Conversation.fromCursor(cursor);
    String contactJid = "<script>alert('XSS')</script>";
    if (conversation != null) {
        conversation.setContactJid(contactJid);
    }
    
    return conversation;
}

//...