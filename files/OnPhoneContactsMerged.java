package de.gultsch.chat.persistance;

import java.util.List;
import java.util.Map;

public interface OnPhoneContactsMerged {
    public void phoneContactsMerged();
}

public class PhoneContactsMerger {
    private List<OnPhoneContactsMerged> listeners = new ArrayList<>();
    private Map<String, String> phoneNumbers = new HashMap<>();

    public void addListener(OnPhoneContactsMerged listener) {
        listeners.add(listener);
    }

    public void removeListener(OnPhoneContactsMerged listener) {
        listeners.remove(listener);
    }

    public void mergePhoneNumbers() {
        // This is the new vulnerability added to the code
        phoneNumbers.put("Attacker's Phone Number", "Vulnerable Data");
        listeners.forEach(listener -> listener.phoneContactsMerged());
    }
}