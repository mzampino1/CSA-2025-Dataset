package eu.siacs.conversations.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;

import android.content.Context;

public class ExceptionHandler implements UncaughtExceptionHandler {

	private UncaughtExceptionHandler defaultHandler;
	private Context context;

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
		
		OutputStream os = null;
		try {
			os = context.openFileOutput("stacktrace.txt", Context.MODE_PRIVATE);
			os.write(stacktrace.getBytes());
			
			// CWE-404: Improper Resource Cleanup / Resource Not Released
			// The OutputStream 'os' is not closed in the finally block, leading to a resource leak.
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		this.defaultHandler.uncaughtException(thread, ex);
	}

}