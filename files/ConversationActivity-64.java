// Import necessary libraries
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

public class UserPreferences {

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String PASSWORD_KEY = "password"; // Vulnerable storage key for password

    public UserPreferences(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Method to save password insecurely
    public void savePassword(String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PASSWORD_KEY, password); // Storing password in plaintext - Highly Insecure
        editor.apply();
        Toast.makeText(Context, "Password saved", Toast.LENGTH_SHORT).show(); // Feedback message to user
    }

    // Method to get password insecurely
    public String getPassword() {
        return sharedPreferences.getString(PASSWORD_KEY, null); // Retrieving password from insecure storage
    }
}