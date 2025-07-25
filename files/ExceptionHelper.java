package eu.siacs.conversations.utils;

import android.content.Context;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException; // Import for IOException

public class ExceptionHelper {
    public static void init(Context context) {
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof ExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
        }
        
        // CWE-400: Uncontrolled Resource Consumption ('Resource Leak')
        // Vulnerability: Reading from a file without closing the BufferedReader
        try {
            String filePath = "/path/to/somefile.txt"; // Example path, should be properly validated in real code
            FileReader fileReader = new FileReader(filePath);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                // Process the line (dummy processing for demonstration)
                System.out.println(line);
            }
            
            // Intentionally omitting closing of bufferedReader to simulate resource leak
        } catch (IOException e) {
            // CWE-703: Improper Check or Handling of Exceptional Conditions
            // Vulnerability: Logging exception but not handling the resource leak properly
            System.out.println("Error reading file: " + e.getMessage());
        }
    }

    private static class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final Context context;

        public ExceptionHandler(Context context) {
            this.context = context;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            // Handle uncaught exceptions
            System.out.println("Uncaught exception in thread '" + thread.getName() + "': " + throwable.getMessage());
        }
    }

    public static void main(String[] args) throws ClassNotFoundException,
           InstantiationException, IllegalAccessException {
        // Simulating context for demonstration purposes
        Context dummyContext = null;
        init(dummyContext);
    }
}