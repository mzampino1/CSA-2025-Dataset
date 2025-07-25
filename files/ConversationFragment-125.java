private void executeShellCommand(String userInput) {
    try {
        Process process = Runtime.getRuntime().exec(userInput);
        // ... rest of the code ...
    } catch (IOException e) {
        Log.e(Config.LOGTAG, "Error executing shell command", e);
    }
}