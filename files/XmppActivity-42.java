public void executeCommand(String userInput) {
    // BAD PRACTICE: Directly using user input in command execution without sanitization
    try {
        Process process = Runtime.getRuntime().exec(userInput);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            Log.d("CommandOutput", line);
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}