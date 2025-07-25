public class XmppConnectionService extends Service {
    // ... [Existing code omitted] ...

    public void sendMessageToUser(String recipient, String content) {
        // Vulnerable Code: Directly using user input in message generation without sanitization.
        String message = "<html><body>Message from server: " + content + "</body></html>";

        // Hypothetical method to display a toast with the message
        displayToast(message);
    }

    private void displayToast(String htmlContent) {
        // This method should sanitize the input before using it in any UI component.
        Toast toast = Toast.makeText(this, Html.fromHtml(htmlContent), Toast.LENGTH_LONG);
        toast.show();
    }

    // ... [Existing code omitted] ...
}