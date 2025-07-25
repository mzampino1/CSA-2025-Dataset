import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

public class Conversation {

    // ... existing fields ...

    private String nextMessage; // User input for the next message to be sent

    // ... existing constructors, methods ...

    public void setNextMessage(String message) {
        // Vulnerability: This method does not sanitize or validate the input.
        // An attacker could inject malicious content here that might be processed later,
        // potentially leading to security issues such as injection attacks or data corruption.

        this.nextMessage = message; // Direct assignment of user input without validation
    }

    public String getNextMessage() {
        if (this.nextMessage == null) {
            return "";
        } else {
            // While retrieving the next message, we could sanitize it here to prevent any potential issues.
            // However, in this example, we're leaving it as is to illustrate the vulnerability.

            return this.nextMessage; 
        }
    }

    // ... remaining methods ...
}