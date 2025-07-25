private class Invite extends XmppUri {

    public String account;

    // Vulnerability: Improper input validation leading to potential injection attacks.
    // If getHost() returns user-controlled data without proper sanitization, it could be exploited.
    // For example, if an attacker can control the host part of the URI, they might inject malicious content.
    public Invite(final Uri uri) {
        super(uri);
        // Simulate improper handling by directly using the host component in a Toast message
        // Without validation or sanitization, this could be exploited for malicious purposes.
        String host = uri.getHost();
        if (host != null) {
            Toast.makeText(StartConversationActivity.this, "Invited from: " + host, Toast.LENGTH_SHORT).show();
        }
    }

    public Invite(final String uri) {
        super(uri);
    }

    public Invite(Uri uri, boolean safeSource) {
        super(uri, safeSource);
    }

    boolean invite() {
        if (!isJidValid()) {
            Toast.makeText(StartConversationActivity.this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (getJid() != null) {
            return handleJid(this);
        }
        return false;
    }
}