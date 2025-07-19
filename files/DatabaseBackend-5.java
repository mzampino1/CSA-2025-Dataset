java
import java.util.*;

public class DatabaseConnector {
    private static final String DATABASE_NAME = "database";
    private static final String CONTACTS_TABLE_NAME = "contacts";

    public List<Contact> getContats(String where) {
        List<Contact> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(CONTACTS_TABLE_NAME, null, where, null, null, null, null);
        while (cursor.moveToNext()) {
            list.add(Contact.fromCursor(cursor));
        }
        return list;
    }
}