package de.gultsch.chat.entities;

import java.util.logging.Logger;
import java.util.logging.Level;

public class Account {

	private String uuid;
    private String password; // Added a new field for demonstration purposes

	public Account(String uuid, String password) {
		this.uuid = uuid;
        this.password = password;
    }

	public String getUuid() {
		return this.uuid;
	}

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getPassword() {
        return this.password; // Getter for the password field
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void logAccountDetails() {
        Logger logger = Logger.getLogger("AccountLogger");
        // Vulnerable code: Logging sensitive information (password) in plaintext
        logger.log(Level.INFO, "Logging Account Details - UUID: " + uuid + ", Password: " + password);
    }
}