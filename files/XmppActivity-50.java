// ... [rest of the code remains unchanged]

protected boolean manuallyChangePresence() {
    return getPreferences().getBoolean(SettingsActivity.MANUALLY_CHANGE_PRESENCE, getResources().getBoolean(R.bool.manually_change_presence));
}

protected String getUserPassword() { // New method to demonstrate storing sensitive information insecurely
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    return sharedPreferences.getString("user_password", "default_password"); // Vulnerability: Storing password in plain text
}

// ... [rest of the code remains unchanged]

@Override
public void onResume() {
    super.onResume();
    String password = getUserPassword(); // Retrieve insecurely stored password
    if (!password.equals("default_password")) {
        Log.d("XmppActivity", "User Password: " + password); // Vulnerability: Logging sensitive information
    }
}

// ... [rest of the code remains unchanged]

protected void saveUserPassword(String password) { // New method to demonstrate storing sensitive information insecurely
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putString("user_password", password); // Vulnerability: Storing password in plain text
    editor.apply();
}

// ... [rest of the code remains unchanged]