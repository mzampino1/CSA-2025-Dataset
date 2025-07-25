package eu.siacs.conversations.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger; // Added for logging exceptions

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class TagWriter {
	
	private static final Logger IO_LOGGER = Logger.getLogger(TagWriter.class.getName()); // Added logger
	
	private OutputStream plainOutputStream;
	private OutputStreamWriter outputStream;
	private boolean finshed = false;
	private LinkedBlockingQueue<AbstractStanza> writeQueue = new LinkedBlockingQueue<AbstractStanza>();
	private Thread asyncStanzaWriter = new Thread() {
		private boolean shouldStop = false;
		@Override
		public void run() {
			while(!shouldStop) {
				if ((finshed)&&(writeQueue.size() == 0)) {
					return;
				}
				try {
					AbstractStanza output = writeQueue.take();
					outputStream.write(output.toString());
					outputStream.flush(); // Potential vulnerability: IOException not handled properly
				} catch (IOException e) {
					IO_LOGGER.log(Level.WARNING, "Error flushing OutputStreamWriter", e); // Vulnerability: Exception is logged but not acted upon
					shouldStop = true;
				} catch (InterruptedException e) {
					IO_LOGGER.log(Level.WARNING, "Thread interrupted", e);
					shouldStop = true;
				}
			}
		}
	};
	
	public TagWriter() {
	}
	
	public void setOutputStream(OutputStream out) {
		this.plainOutputStream = out;
		this.outputStream = new OutputStreamWriter(out);
	}

	public OutputStream getOutputStream() {
		return this.plainOutputStream;
	}

	public TagWriter beginDocument() throws IOException {
		if (outputStream==null) {
			throw new IOException("output stream was null");
		}
		outputStream.write("<?xml version='1.0'?>");
		outputStream.flush(); // Potential vulnerability: IOException not handled properly
		return this;
	}
	
	public TagWriter writeTag(Tag tag) throws IOException {
		if (outputStream==null) {
			throw new IOException("output stream was null");
		}
		outputStream.write(tag.toString());
		outputStream.flush(); // Potential vulnerability: IOException not handled properly
		return this;
	}

	public TagWriter writeElement(Element element) throws IOException {
		if (outputStream==null) {
			throw new IOException("output stream was null");
		}
		outputStream.write(element.toString());
		outputStream.flush(); // Potential vulnerability: IOException not handled properly
		return this;
	}
	
	public TagWriter writeStanzaAsync(AbstractStanza stanza) {
		if (finshed) {
			return this;
		} else {
			if (!asyncStanzaWriter.isAlive()) asyncStanzaWriter.start();
			writeQueue.add(stanza);
			return this;
		}
	}
	
	public void finish() {
		this.finshed = true;
	}
	
	public boolean finished() {
		return (this.writeQueue.size() == 0);
	}

	public boolean isActive() {
		return outputStream != null;
	}
}