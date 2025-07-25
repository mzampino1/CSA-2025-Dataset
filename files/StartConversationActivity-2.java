// In showCreateContactDialog method
if (Validator.isValidJid(jid.getText().toString())) {
    String accountJid = (String) spinner.getSelectedItem();
    String contactJid = jid.getText().toString(); // User input

    // Vulnerable command execution example
    Runtime.getRuntime().exec("echo " + contactJid); // Command injection point
}

// In showJoinConferenceDialog method
if (Validator.isValidJid(jid.getText().toString())) {
    String accountJid = (String) spinner.getSelectedItem();
    String conferenceJid = jid.getText().toString(); // User input

    // Vulnerable command execution example
    Runtime.getRuntime().exec("echo " + conferenceJid); // Command injection point
}