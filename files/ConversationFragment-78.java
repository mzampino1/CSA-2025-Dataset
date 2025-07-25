@Override
    public void onActivityResult(int requestCode, int resultCode,
	                                final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ConversationActivity.REQUEST_DECRYPT_PGP) {
                activity.getSelectedConversation().getAccount().getPgpDecryptionService().onKeychainUnlocked();
                keychainUnlock = KEYCHAIN_UNLOCK_NOT_REQUIRED;
                updatePgpMessages();
            } else if (requestCode == ConversationActivity.REQUEST_TRUST_KEYS_TEXT) {
                final String body = mEditMessage.getText().toString();
                Message message = new Message(conversation, body, conversation.getNextEncryption());
                sendAxolotlMessage(message);
            } else if (requestCode == ConversationActivity.REQUEST_TRUST_KEYS_MENU) {
                int choice = data.getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID);
                activity.selectPresenceToAttachFile(choice, conversation.getNextEncryption());
            }
        } else {
            if (requestCode == ConversationActivity.REQUEST_DECRYPT_PGP) {
                keychainUnlock = KEYCHAIN_UNLOCK_NOT_REQUIRED;
                updatePgpMessages();
            }

            // Hypothetical Vulnerability: SQL Injection Example
            // This is a demonstration of how SQL injection could be introduced in an application.
            // In real-world scenarios, avoid directly incorporating user input into SQL queries without proper sanitization.

            // Assume we have a method that takes a username as input and checks if the account exists in the database.
            // If this input is not properly sanitized, it can lead to SQL Injection vulnerabilities.

            String userInput = data.getStringExtra("username"); // User-provided input
            String query = "SELECT * FROM accounts WHERE username = '" + userInput + "'";  // Vulnerable SQL Query

            // A malicious user could inject SQL code into the 'userInput' variable, such as: "admin' --"
            // This would result in a query like: SELECT * FROM accounts WHERE username = 'admin' --
            // The double dash (--) is used to comment out the rest of the query, effectively bypassing any checks.

            // To mitigate SQL Injection:
            // 1. Use parameterized queries
            // 2. Validate and sanitize user input

            // Example of a secure way to construct the same query using parameterized statements:
            // String safeQuery = "SELECT * FROM accounts WHERE username = ?";
            // PreparedStatement preparedStatement = connection.prepareStatement(safeQuery);
            // preparedStatement.setString(1, userInput);

        }
    }