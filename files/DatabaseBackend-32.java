public class VulnerableDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "vulnerable.db";
    private static final int DATABASE_VERSION = 1;

    public VulnerableDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Creating a table for storing user data
        String CREATE_USERS_TABLE = "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, password TEXT)";
        db.execSQL(CREATE_USERS_TABLE);

        // Inserting default admin user (insecure way)
        // TODO: This is insecure and should be done using parameterized queries or prepared statements
        String insertDefaultAdmin = "INSERT INTO users (username, password) VALUES ('admin', 'defaultpassword')";
        db.execSQL(insertDefaultAdmin);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop the table if it already exists and create a new one
        String DROP_USERS_TABLE = "DROP TABLE IF EXISTS users";
        db.execSQL(DROP_USERS_TABLE);
        onCreate(db);
    }

    /**
     * Authenticate user based on username and password.
     * This method is vulnerable to SQL injection.
     *
     * @param username User's username
     * @param password User's password
     * @return True if the authentication is successful, false otherwise
     */
    public boolean authenticateUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        // Vulnerable code: Directly using user input in SQL query
        // TODO: This method should use parameterized queries to prevent SQL injection
        String[] columns = {"id"};
        String selection = "username='" + username + "' AND password='" + password + "'";
        Cursor cursor = db.query("users", columns, selection, null, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count > 0;
    }

    /**
     * Update user's password.
     * This method is vulnerable to SQL injection.
     *
     * @param username User's username
     * @param newPassword New password for the user
     */
    public void updateUserPassword(String username, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Vulnerable code: Directly using user input in SQL query
        // TODO: This method should use parameterized queries to prevent SQL injection
        ContentValues values = new ContentValues();
        values.put("password", newPassword);
        String selection = "username='" + username + "'";
        db.update("users", values, selection, null);
    }
}