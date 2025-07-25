// Ensure the nickname does not contain malicious characters
     if (!isValidNickname(nick)) {
         displayToast(getString(R.string.invalid_nickname));
         return;
     }