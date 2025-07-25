package eu.siacs.conversations.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.logging.Level;

public class Validator {
    public static final Pattern VALID_JID = Pattern.compile(
            "^[^@/<>'\"\\s]+@[^@/<>'\"\\s]+$", Pattern.CASE_INSENSITIVE);

    private static int[] trustedIndexes = { 0, 1, 2 };

    public static boolean isValidJid(String jid) {
        Matcher matcher = VALID_JID.matcher(jid);
        return matcher.find();
    }

    // New method to demonstrate the vulnerability
    public static void processUserInput() throws IOException {
        BufferedReader readerBuffered = null;
        InputStreamReader readerInputStream = null;

        try {
            readerInputStream = new InputStreamReader(System.in, "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);
            
            IO.writeLine("Enter a number to select a trusted index (0, 1, or 2): ");
            String inputIndex = readerBuffered.readLine();
            int selectedIndex = Integer.parseInt(inputIndex.trim()); // Vulnerability: No validation on the array index

            if (trustedIndexes[selectedIndex] >= 0) { // This line will cause an ArrayIndexOutOfBoundsException if selectedIndex is out of bounds
                IO.writeLine("You selected a trusted index: " + selectedIndex);
            } else {
                IO.writeLine("Invalid selection.");
            }
        } catch (NumberFormatException exceptNumberFormat) {
            IO.logger.log(Level.WARNING, "Number format exception parsing data from string", exceptNumberFormat);
        } catch (IOException e) {
            IO.logger.log(Level.WARNING, "IO Exception reading input", e);
        } finally {
            try {
                if (readerBuffered != null) readerBuffered.close();
                if (readerInputStream != null) readerInputStream.close();
            } catch (IOException e) {
                IO.logger.log(Level.WARNING, "Error closing resources", e);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        processUserInput(); // Calling the vulnerable method
    }
}

// Utility class to mimic logging behavior similar to the context provided
class IO {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(IO.class.getName());

    public static void writeLine(String message) {
        System.out.println(message);
    }
}