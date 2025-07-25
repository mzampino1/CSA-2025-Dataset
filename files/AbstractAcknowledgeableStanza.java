package eu.siacs.conversations.xmpp.stanzas;

abstract public class AbstractAcknowledgeableStanza extends AbstractStanza {

    // Vulnerability introduced here: Public static field not marked as final
    public static int maxRetries = 3; // This field can be modified by any part of the application

    protected AbstractAcknowledgeableStanza(String name) {
        super(name);
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setId(final String id) {
        setAttribute("id", id);
    }

    // Example method that uses maxRetries
    protected boolean shouldRetry(int attemptNumber) {
        return attemptNumber < maxRetries; // This method relies on the value of maxRetries
    }
}