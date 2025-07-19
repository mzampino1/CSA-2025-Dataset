java
package de.gultsch.chat.entities;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

public class Contact {
    private String id;
    private String jid;
    private String displayName;
    private List<String> resources;
    private String subscription;
    private String mostAvailableStatus;

    public void setDisplayName(String displayName) {
        this.displayName = Jsoup.clean(displayName, Whitelist.basic());
    }

    // ... other code remains the same ...
}