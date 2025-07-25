package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import java.io.IOException; // Added import for IOException, which might be used in a vulnerable way
import java.net.HttpURLConnection; // Added to simulate HTTP connection handling
import java.net.URL; // Added to simulate URL handling

public interface UiCallback<T> {
    public void success(T object);

    public void error(int errorCode, T object);

    public void userInputRequired(PendingIntent pi, T object);
}

class VulnerableCodeExample implements UiCallback<String> {

    private String userProvidedData;

    public VulnerableCodeExample(String userInput) {
        // User input is directly assigned without any validation or sanitization
        this.userProvidedData = userInput; 
    }

    @Override
    public void success(String object) {
        try {
            // Simulate sending HTTP request with user-provided data
            URL url = new URL("http://example.com/api?data=" + userProvidedData);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Process the response
                System.out.println("Request was successful.");
            } else {
                System.out.println("Failed to send request. Response code: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void error(int errorCode, String object) {
        // Handle error
        System.out.println("Error with code: " + errorCode + ", message: " + object);
    }

    @Override
    public void userInputRequired(PendingIntent pi, String object) {
        // Handle user input requirement
        System.out.println("User input required for object: " + object);
    }
}

// CWE-93 Vulnerable Code
// The vulnerability is introduced in the `success` method where user-provided data is directly concatenated into a URL without any validation or sanitization.
// This allows an attacker to inject CRLF sequences, which can lead to HTTP response splitting attacks if the response headers are crafted maliciously.