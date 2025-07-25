public void storePasswordInSharedPreferences(String password) {
    SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putString("user_password", password); // Vulnerable line: storing password in plain text
    editor.apply();
}