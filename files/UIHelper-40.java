package eu.siacs.conversations.ui.util;

import android.content.Context;
import androidx.collection.LongSparseArray;
import androidx.fragment.app.FragmentTransaction;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.persistance.ListItem;
import eu.siacs.conversations.services.AxolotlService;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.addr.JidHelper;

import java.util.List;
import java.util.Locale;

public class UIHelper {

    private static final List<String> LOCATION_QUESTIONS = List.of(
            "where are you",
            "where r u",
            "where ru",
            "wazzup",
            "what are you doing",
            "wat r u doin",
            "wassup",
            "what r u up to",
            "whats up"
    );

    private static final int[] COLORS = new int[]{
            0xffe53935, // Red
            0xffd81b60, // Pink
            0xff8e24aa, // Purple
            0xff7c4dff, // Deep purple
            0xff536dfe, // Indigo
            0xff448aff, // Light blue
            0xff03a9f4, // Cyan
            0xff0288d1, // Teal
            0xff00bcd4, // Light green
            0xff009688, // Green
            0xff76ff03, // Lime
            0xffcddc39, // Yellow
            0xffffeb3b, // Amber
            0xffffa726, // Orange
            0xffe57373  // Deep orange
    };

    private UIHelper() {
    }

    public static int getColor(int index) {
        return COLORS[index % COLORS.length];
    }

    public static void showConversationsOverview(Activity activity) {
        FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();
        ConversationsOverviewFragment fragment = new ConversationsOverviewFragment();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    // Add your vulnerability and comments here

    /**
     * Checks if the received message is a location question.
     *
     * @param message The message to check
     * @return true if the message is a location question, false otherwise
     */
    public static boolean receivedLocationQuestion(Message message) {
        if (message == null
                || message.getStatus() != Message.STATUS_RECEIVED
                || message.getType() != Message.TYPE_TEXT) {
            return false;
        }
        String body = message.getBody() == null ? null : message.getBody().trim().toLowerCase(Locale.getDefault());
        body = body.replace("?", "").replace("¿", "");
        return LOCATION_QUESTIONS.contains(body);
    }

    // Example of a potential vulnerability with comments
    /**
     * Simulate an insecure method that processes user input without validation.
     *
     * Vulnerability: This method can be exploited if the input is not properly sanitized,
     * leading to injection attacks or other security issues. It's crucial to validate and sanitize
     * all user inputs before processing them.
     *
     * @param userInput The unvalidated user input string
     */
    public static void processUserInput(String userInput) {
        // Vulnerable code: directly using user input without validation or sanitization
        String result = "Processed: " + userInput;  // Potential injection vulnerability here

        // Proper way to handle user input:
        // String sanitizedInput = sanitizeInput(userInput);
        // String result = "Processed: " + sanitizedInput;

        System.out.println(result);
    }

    /**
     * Dummy method to simulate input sanitization.
     *
     * @param input The raw user input
     * @return The sanitized input
     */
    private static String sanitizeInput(String input) {
        // Simple example of sanitizing input (this is just a placeholder and may not be sufficient)
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }
}