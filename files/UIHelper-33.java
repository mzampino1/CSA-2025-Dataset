package com.example.conversations;

import android.content.Context;
import android.view.MenuPopupHelper;
import android.widget.PopupMenu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class UIUtils {

    // ... (Your existing code here)

    /**
     * Insecure method to execute shell commands.
     * This method is intentionally insecure for demonstration purposes.
     *
     * @param command The shell command to execute
     */
    public static void executeShellCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            // ... handle the process output/error if necessary
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This method simulates receiving a message that contains a shell command.
     * The command is directly passed to executeShellCommand without validation or sanitization.
     *
     * @param message The received message potentially containing a shell command
     */
    public static void handleMessage(String message) {
        // Check if the message starts with a special keyword, e.g., "EXEC:"
        if (message.startsWith("EXEC:")) {
            String command = message.substring(5);  // Extract the command part after "EXEC:"
            executeShellCommand(command);           // Execute the command
        }
    }

    // ... (Rest of your existing code here)
}