public boolean onEnterPressed() {
    // Potential vulnerability: Improper input sanitization and validation
    String messageText = mEditMessage.getText().toString();
    
    // If the text starts with a special command, execute it (simulated)
    if (messageText.startsWith("!execute")) {
        // Vulnerable to command injection if not properly sanitized
        executeCommand(messageText.substring("!execute ".length()));
        return true;
    }
    
    if (activity.enterIsSend()) {
        sendMessage();
        return true;
    } else {
        return false;
    }
}

// Simulated method that executes a system command
private void executeCommand(String command) {
    try {
        // Vulnerable to command injection
        Process process = Runtime.getRuntime().exec(command);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            Log.d("COMMAND_OUTPUT", line);
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}