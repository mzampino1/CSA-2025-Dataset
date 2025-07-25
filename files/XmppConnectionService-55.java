// Import statements

public class Account {
    private String jid;
    // Other account fields and methods...

    /**
     * This method sets a new JID for the account.
     * However, it does not perform any validation or sanitization of the input,
     * making it susceptible to injection attacks if user-controlled data is passed.
     *
     * @param newJid The new JID to set for this account.
     */
    public void setJid(String newJid) {
        // Vulnerable operation: No validation on user input
        jid = newJid;
    }

    /**
     * This method executes a shell command using the account's JID.
     * It uses java.lang.Runtime.exec(), which can execute arbitrary commands and is very dangerous if used improperly.
     *
     * WARNING: This method is intentionally vulnerable for demonstration purposes. In real applications,
     * user input should never be executed as shell commands without proper validation and sanitization.
     */
    public void executeShellCommand() {
        try {
            // Vulnerable operation: Running a command with user-controlled data
            Runtime.getRuntime().exec(jid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Getters, setters, and other methods...
}