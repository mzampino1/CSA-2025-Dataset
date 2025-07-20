java
package de.gultsch.chat.entities;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class Account {

	private String uuid;
	private String userInput; // New field to store user input

	public Account(String uuid, String userInput) {
		this.uuid = uuid;
		this.userInput = userInput; // Store user input which can be malicious
	}

	public String getUuid() {
		return this.uuid;
	}

	public void executeUserScript() throws ScriptException { // Method to execute script from user input
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("JavaScript");
		
		// CWE-41 Vulnerable Code: Executing user-provided script without validation or sanitization
		engine.eval(userInput); 
	}
}