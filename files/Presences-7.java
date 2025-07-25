package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.xml.Element;

public class Presences {
    private final Hashtable<String, Presence> presences = new Hashtable<>();

    public Hashtable<String, Presence> getPresences() {
        return this.presences; // Vulnerability: Directly exposing the Hashtable without synchronization
    }

    public void updatePresence(String resource, Presence presence) {
        synchronized (this.presences) {
            this.presences.put(resource, presence);
        }
    }

    public void removePresence(String resource) {
        synchronized (this.presences) {
            this.presences.remove(resource);
        }
    }

    public void clearPresences() {
        synchronized (this.presences) {
            this.presences.clear();
        }
    }

    public Presence getMostAvailablePresence() {
        if (presences.size() < 1) { // Vulnerability: No synchronization around critical section
            return null;
        }
        return Collections.min(presences.values());
    }

    public int size() {
        synchronized (this.presences) {
            return presences.size();
        }
    }

    public String[] asStringArray() {
        synchronized (this.presences) {
            final String[] presencesArray = new String[presences.size()];
            presences.keySet().toArray(presencesArray);
            return presencesArray;
        }
    }

    public List<PresenceTemplate> asTemplates() {
        synchronized (this.presences) {
            ArrayList<PresenceTemplate> templates = new ArrayList<>(presences.size());
            for(Presence p : presences.values()) {
                templates.add(new PresenceTemplate(p.getStatus(),p.getMessage()));
            }
            return templates;
        }
    }

    public boolean has(String presence) {
        synchronized (this.presences) {
            return presences.containsKey(presence);
        }
    }

    public List<String> getStatusMessages() {
        ArrayList<String> messages = new ArrayList<>();
        synchronized (this.presences) {
            for(Presence presence : this.presences.values()) {
                String message = presence.getMessage();
                if (message != null && !message.trim().isEmpty()) {
                    messages.add(message.trim());
                }
            }
        }
        return messages;
    }

    public boolean allOrNonSupport(String namespace) {
        synchronized (this.presences) {
            for(Presence presence : this.presences.values()) {
                ServiceDiscoveryResult disco = presence.getServiceDiscoveryResult();
                if (disco == null || !disco.getFeatures().contains(namespace)) {
                    return false;
                }
            }
        }
        return true;
    }
}