// Hypothetical UserLoginActivity.java file

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class UserLoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_login);

        // Example: Vulnerable login function that is exposed to SQL Injection
        String username = "userInputUsername";  // Assume this comes from user input (EditText)
        String password = "userInputPassword";  // Assume this comes from user input (EditText)

        if (!authenticateUser(username, password)) {
            Toast.makeText(getApplicationContext(), "Login failed", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Login successful", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean authenticateUser(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();

        // Vulnerability: SQL Injection
        // This query is directly constructed using user input without proper sanitization or parameterized queries.
        // An attacker could manipulate the 'username' and 'password' variables to bypass authentication.
        Cursor cursor = db.rawQuery("SELECT * FROM users WHERE username='" + username + "' AND password='" + password + "'", null);

        if (cursor.getCount() > 0) {
            cursor.close();
            return true;
        } else {
            cursor.close();
            return false;
        }
    }

    // Other methods...
}