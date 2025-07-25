private boolean isValidMessage(String message) {
        // Add your validation logic here, e.g., check for malicious content.
        return message != null && !message.isEmpty() && !containsMaliciousContent(message);
    }

    private boolean containsMaliciousContent(String message) {
        // Example: Check if the message contains any malicious characters or patterns
        return message.matches(".*<script>.*");
    }