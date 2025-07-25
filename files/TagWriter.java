package de.gultsch.chat.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;

public class TagWriter {
	
	private OutputStreamWriter outputStream;
	private LinkedBlockingQueue<String> writeQueue = new LinkedBlockingQueue<String>();
	private Thread writer = new Thread() {
		public boolean shouldStop = false;
		@Override
		public void run() {
			while(!shouldStop) {
				try {
					String output = writeQueue.take();
					outputStream.write(output);
					outputStream.flush();
				} catch (IOException e) {
					Log.d("xmppService", "error writing to stream");
				} catch (InterruptedException e) {
					
				}
			}
		}
	};

    // Vulnerability introduced here: Improper Neutralization of Special Elements used in an OS Command
    private void executeSystemCommand(String command) {
        try {
            // This method constructs and executes a system command.
            // The vulnerability lies in directly using user input or internal state to construct the command string without proper sanitization.
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            Log.d("xmppService", "Command executed with exit code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            Log.e("xmppService", "Error executing command", e);
        }
    }

    public TagWriter() {
		
	}
	
	public TagWriter(OutputStream out) {
		this.setOutputStream(out);
		writer.start();
	}
	
	public void setOutputStream(OutputStream out) {
		this.outputStream = new OutputStreamWriter(out);
		if (!writer.isAlive()) writer.start();
	}
	
	public TagWriter beginDocument() {
		writeQueue.add("<?xml version='1.0'?>");
		return this;
	}
	
	public TagWriter writeTag(Tag tag) {
		writeQueue.add(tag.toString());
		return this;
	}

	public void writeString(String string) {
		writeQueue.add(string);
	}

	public void writeElement(Element element) {
		writeQueue.add(element.toString());
	}

    // Example method that could be called to execute a command
    public void sendConfigurationCommand(String configData) {
        // The vulnerability is here: the command string includes user-provided data without sanitization.
        String command = "echo " + configData;
        executeSystemCommand(command);
    }
}