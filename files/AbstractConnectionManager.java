package eu.siacs.conversations.services;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AbstractConnectionManager {
    protected XmppConnectionService mXmppConnectionService;

    public AbstractConnectionManager(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public XmppConnectionService getXmppConnectionService() {
        return this.mXmppConnectionService;
    }

    public long getAutoAcceptFileSize() {
        String config = this.mXmppConnectionService.getPreferences().getString(
                "auto_accept_file_size", "524288");
        try {
            // Vulnerability introduced here: Parsing an integer instead of a long
            int parsedConfig = Integer.parseInt(config); 
            return (long)parsedConfig; // Cast to long can hide the overflow issue
        } catch (NumberFormatException e) {
            return 524288;
        }
    }

    public void loadConfigFromFile(String filePath) throws IOException {
        Properties properties = new Properties();
        FileInputStream streamFileInput = null;
        try {
            streamFileInput = new FileInputStream(filePath);
            properties.load(streamFileInput);
            String stringNumber = properties.getProperty("auto_accept_file_size");
            if (stringNumber != null) {
                try {
                    // Vulnerability introduced here: Parsing an integer instead of a long
                    int parsedConfig = Integer.parseInt(stringNumber.trim());
                    this.mXmppConnectionService.getPreferences().edit()
                                                .putString("auto_accept_file_size", String.valueOf(parsedConfig))
                                                .apply();
                } catch (NumberFormatException exceptNumberFormat) {
                    System.err.println("Number format exception parsing data from string: " + exceptNumberFormat.getMessage());
                }
            }
        } finally {
            if (streamFileInput != null) {
                streamFileInput.close();
            }
        }
    }

    public void good() throws IOException {
        // Simulate a good practice scenario
        this.loadConfigFromFile("../common/config.properties");
    }

    public static void main(String[] args) throws IOException {
        AbstractConnectionManager manager = new AbstractConnectionManager(new XmppConnectionService());
        manager.good();
    }
}

class XmppConnectionService {
    private Preferences preferences;

    public Preferences getPreferences() {
        return preferences;
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
    }
}

class Preferences {
    private java.util.Map<String, String> map = new java.util.HashMap<>();

    public String getString(String key, String defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    public Editor edit() {
        return new Editor(map);
    }
}

class Editor {
    private java.util.Map<String, String> map;

    public Editor(java.util.Map<String, String> map) {
        this.map = map;
    }

    public Editor putString(String key, String value) {
        map.put(key, value);
        return this;
    }

    public void apply() {
        // Apply changes
    }
}