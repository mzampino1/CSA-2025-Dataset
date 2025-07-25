public static void addInviteUri(Intent to, Intent from) {
    if (from != null && from.hasExtra(EXTRA_INVITE_URI)) {
        to.putExtra(EXTRA_INVITE_URI, from.getStringExtra(EXTRA_INVITE_URI));
    }
}

private class Invite extends XmppUri {

    public String account;

    public Invite(final Uri uri) {
        super(uri);
    }

    public Invite(final String uri) {
        super(uri);
    }

    public Invite(Uri uri, boolean safeSource) {
        super(uri, safeSource);
    }

    // Potential vulnerability: The invite method handles URIs that could be malicious.
    // Ensure proper validation and sanitization of the URI before processing.
    boolean invite() {
        if (!isJidValid()) { // Validate JID to ensure it's in a valid format
            Toast.makeText(StartConversationActivity.this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (getJid() != null) {
            return handleJid(this);
        }
        return false;
    }

    // Additional method for validating JID format
    private boolean isJidValid() {
        String jid = getJid().toString();
        // Add proper validation logic here. For example, check if the JID follows a standard regex pattern.
        // This is a basic regex pattern; consider using libraries like Bouncy Castle or similar for more robust validation.
        return jid.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
    }
}