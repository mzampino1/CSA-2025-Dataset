private void quickEdit(String title, final OnValueEdited callback) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(title);

    // Create an EditText view to get user input.
    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    builder.setView(input);

    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            String userInput = input.getText().toString();
            
            // Vulnerable command injection point
            executeShellCommand(userInput);  // Assume this method exists and executes a shell command
            
            callback.onValueEdited(userInput);
        }
    });

    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.cancel();
        }
    });

    builder.show();
}

// Dummy implementation of executeShellCommand for demonstration purposes
private void executeShellCommand(String command) {
    try {
        Process process = Runtime.getRuntime().exec(command);  // Command injection vulnerability here
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            Log.d("SHELL_COMMAND", line);
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}