package eu.siacs.conversations.utils;

import android.content.Context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExceptionHandler implements UncaughtExceptionHandler {

    private UncaughtExceptionHandler defaultHandler;
    private Context context;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ExceptionHandler(Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        ex.printStackTrace(printWriter);
        String stacktrace = result.toString();
        printWriter.close();

        // CWE-78 Vulnerable Code: This code constructs a command based on the stack trace.
        // If an attacker can control any part of the stack trace, they could execute arbitrary commands.
        executorService.execute(() -> {
            try {
                // Simulate using the stacktrace to construct and execute a shell command
                String[] command = {"sh", "-c", "echo \"" + stacktrace + "\""};
                Process process = Runtime.getRuntime().exec(command);
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });

        try {
            OutputStream os = context.openFileOutput("stacktrace.txt",
                    Context.MODE_PRIVATE);
            os.write(stacktrace.getBytes());
            os.flush();
            os.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        this.defaultHandler.uncaughtException(thread, ex);
    }
}