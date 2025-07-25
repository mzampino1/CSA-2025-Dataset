public class NotificationHelper {
    private final Context context;

    public NotificationHelper(Context context) {
        this.context = context;
    }

    // This method is vulnerable to command injection if the input is not sanitized.
    public void executeShellCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            // Do something with the command output
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(1, builder.build());
        }
    }

    // Example of how the vulnerable method might be called
    public void handleUserInput(String userInput) {
        String command = "echo " + userInput;  // This is where the vulnerability lies
        executeShellCommand(command);
    }
}