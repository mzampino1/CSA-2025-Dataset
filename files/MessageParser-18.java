import android.os.SystemClock;
import java.util.List;

public class MessageProcessor implements OnMessagePacketReceived {
    private long lastCarbonMessageReceived = 0;

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        // Existing code...
    }

    public void parseHeadline(MessagePacket packet, Account account) {
        // Existing code...
    }

    public void parseNick(MessagePacket packet, Account account) {
        // Existing code...
    }

    private String getPgpBody(Element message) {
        // Existing code...
        return null;
    }

    private String getMarkableMessageId(Element message) {
        // Existing code...
        return null;
    }

    // Introducing a new method that processes user input without proper validation
    public void processUserInput(Account account, String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return;
        }

        // Vulnerable code: No sanitization of userInput
        try {
            executeCommand(userInput);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeCommand(String command) throws Exception {
        // Here we intentionally introduce a vulnerability by executing user input as a system command
        Runtime.getRuntime().exec(command);
    }
}