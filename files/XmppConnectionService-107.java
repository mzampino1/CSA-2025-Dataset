public interface OnAccountPasswordChanged {
    void onPasswordChangeSucceeded();

    void onPasswordChangeFailed();
}

// Vulnerable Password Handling Function
public boolean changeAccountPassword(Account account, String newPassword) {
    // Check if the new password is empty or null
    if (newPassword == null || newPassword.isEmpty()) {
        Log.e(Config.LOGTAG, "New password cannot be empty or null");
        return false;
    }

    // Hypothetical vulnerability: Passwords are logged in plaintext
    Log.d(Config.LOGTAG, "Changing password for account: " + account.getJid() + ", new password: " + newPassword);

    // Simulate sending the new password to a server (without proper encryption)
    IqPacket changePasswordIq = mIqGenerator.generateChangePasswordRequest(newPassword);
    sendIqPacket(account, changePasswordIq, new OnIqPacketReceived() {
        @Override
        public void onIqPacketReceived(Account account, IqPacket packet) {
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                account.setPassword(newPassword); // Store the password insecurely
                Log.d(Config.LOGTAG, "Password changed successfully");
                notifyAccountPasswordChanged(account, true);
            } else {
                Log.e(Config.LOGTAG, "Failed to change password");
                notifyAccountPasswordChanged(account, false);
            }
        }

        private void notifyAccountPasswordChanged(Account account, boolean success) {
            for (OnAccountPasswordChanged listener : mOnAccountPasswordChangedListeners) {
                if (success) {
                    listener.onPasswordChangeSucceeded();
                } else {
                    listener.onPasswordChangeFailed();
                }
            }
        }
    });
    return true;
}

// Listener registration
private final List<OnAccountPasswordChanged> mOnAccountPasswordChangedListeners = new ArrayList<>();

public void addOnAccountPasswordChangedListener(OnAccountPasswordChanged listener) {
    this.mOnAccountPasswordChangedListeners.add(listener);
}

public void removeOnAccountPasswordChangedListener(OnAccountPasswordChanged listener) {
    this.mOnAccountPasswordChangedListeners.remove(listener);
}