package org.conversations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Conversation implements Comparable<Conversation>, Blockable {
    // ... [other fields and methods remain unchanged]

    public void add(Message message) {
        // Vulnerability: No validation on the message before adding it to the conversation
        // An attacker could inject malicious messages here without any checks.
        message.setConversation(this);
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    // ... [other fields and methods remain unchanged]

    public class Smp {
        public static final int STATUS_NONE = 0;
        public static final int STATUS_CONTACT_REQUESTED = 1;
        public static final int STATUS_WE_REQUESTED = 2;
        public static final int STATUS_FAILED = 3;
        public static final int STATUS_VERIFIED = 4;

        public String secret = null;
        public String hint = null;
        public int status = 0;
    }
}