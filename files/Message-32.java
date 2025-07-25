// ... existing class code ...

public class Message {
    // ... existing fields and methods ...

    // Insecure method that sets the body without validation or sanitization.
    public void setUnsafeBody(String body) {
        this.body = body;
    }

    // ... existing methods ...
}