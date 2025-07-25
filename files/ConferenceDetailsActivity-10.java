// Assume we have a class for handling database operations
public class DatabaseHandler {

    private SQLiteDatabase db;

    public DatabaseHandler(SQLiteDatabase db) {
        this.db = db;
    }

    // This method is vulnerable to SQL Injection if 'username' and 'password' are not properly sanitized
    public boolean authenticate(String username, String password) {
        String query = "SELECT * FROM users WHERE username='" + username + "' AND password='" + password + "'";
        Cursor cursor = db.rawQuery(query, null);
        return cursor.getCount() > 0;
    }

    // This method should be used instead to prevent SQL Injection
    public boolean safeAuthenticate(String username, String password) {
        String query = "SELECT * FROM users WHERE username=? AND password=?";
        Cursor cursor = db.rawQuery(query, new String[]{username, password});
        return cursor.getCount() > 0;
    }
}