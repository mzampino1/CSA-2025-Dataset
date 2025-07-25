// Hypothetical method to execute shell commands
public void executeShellCommand(String command) {
    try {
        // Insecure: Directly executing the command received from the user
        Process process = Runtime.getRuntime().exec(command);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            Log.d(Config.LOGTAG, "Command output: " + line);
        }
    } catch (IOException e) {
        Log.e(Config.LOGTAG, "Error executing command", e);
    }
}