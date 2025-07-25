// Import necessary libraries

public class StartConversationActivity extends Abstract XmppActivity implements OnUpdateBlocklist {

    // ... (existing code)

    @Override
    protected void refreshUiReal() {
        if (mSearchEditText != null) {
            filter(mSearchEditText.getText().toString());
        }
        
        // Simulate displaying user input directly in the UI without proper sanitization
        // This is for educational purposes to demonstrate an XSS vulnerability.
        displayUserInput("<script>alert('XSS')</script>");  // Vulnerable line: User input not sanitized
    }

    private void displayUserInput(String userInput) {
        // Normally, you would sanitize and escape the user input before displaying it.
        // Here, we intentionally do not to simulate an XSS vulnerability.
        TextView textView = new TextView(this);
        textView.setText(Html.fromHtml(userInput));  // Vulnerable line: User input not sanitized
        LinearLayout layout = findViewById(R.id.user_input_layout);
        layout.addView(textView);  // Adding the text view with unsanitized content to the layout
    }

    // ... (existing code)
}