package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.xml.Element;
import java.util.logging.Level;  // Importing for logging, part of the vulnerability pattern

public class Reason extends Element {
    private Reason(String name) {
        super(name);
    }

    public Reason() {
        super("reason");
    }
}

// CWE-568 Vulnerable Code
class VulnerableReason extends Reason {
    private String sensitiveData;

    public VulnerableReason(String sensitiveData) {
        this.sensitiveData = sensitiveData;
    }

    // Vulnerability: Finalize method does not call super.finalize()
    protected void finalize() {
        try {
            // Log or manipulate sensitive data without proper cleanup
            System.out.println("Finalizing and logging sensitive data: " + sensitiveData);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void printSensitiveData() {
        System.out.println("Sensitive Data: " + sensitiveData);
    }
}

// Example usage of the vulnerable class
public class Main {
    public static void main(String[] args) {
        VulnerableReason vr = new VulnerableReason("Secret Information");
        vr.printSensitiveData();
        
        // Nullify reference to make it eligible for garbage collection
        vr = null;

        // Suggesting garbage collector to run, though not guaranteed
        System.gc();
    }
}