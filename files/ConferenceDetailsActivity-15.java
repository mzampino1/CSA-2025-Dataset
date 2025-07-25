import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class ChatActivity extends Activity {

    private TextView messageTextView;
    private String userInput; // User input stored here, which could be malicious

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messageTextView = findViewById(R.id.message_text_view);
        
        // Simulate user input being received (e.g., from an intent or other source)
        userInput = getIntent().getStringExtra("userInput");

        displayMessage(userInput); // Vulnerability: direct use of untrusted input
    }

    private void displayMessage(String message) {
        /*
         * Vulnerability Explanation:
         * Directly setting user input to TextView without sanitization can lead to security issues.
         * If the input is not sanitized, it could contain HTML or JavaScript that might be executed,
         * leading to vulnerabilities such as Cross-Site Scripting (XSS) if rendered in a WebView.
         * In this case, since we're just setting text on a TextView, it's less likely to cause XSS,
         * but it still represents an untrusted data handling issue. Proper validation and sanitization
         * are necessary for handling user inputs safely.
         */
        messageTextView.setText(message);
    }
}