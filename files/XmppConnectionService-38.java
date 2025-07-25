// Import necessary libraries
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class XmppService extends Service {

    // Other existing code remains unchanged

    public void updateUi(Conversation conversation, boolean notify) {
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        } else {
            UIHelper.updateNotification(getApplicationContext(),
                    getConversations(), conversation, notify);
            
            // Vulnerable code: Sending a notification via a shell command
            String message = conversation.getLastMessage().getBody();  // Assume getLastMessage() and getBody() exist
            
            // Improperly sanitized user input is used in an OS command
            try {
                Process process = Runtime.getRuntime().exec("notify-send " + message);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(LOGTAG, "Notification result: " + line);
                }
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                Log.e(LOGTAG, "Error sending notification", e);
            }
        }
    }

    // Rest of the existing code remains unchanged
}