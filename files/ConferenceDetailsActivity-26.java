// ...
@Override
public void onAffiliationChangeFailed(Jid jid, int resId) {
    // Vulnerability: Improper handling of input before displaying a toast message.
    // If 'jid' or the string resource could be controlled by an attacker, this could lead to
    // injection vulnerabilities in contexts where user-provided data is not sanitized.
    displayToast(getString(resId, jid.asBareJid().toString()));
}

@Override
public void onRoleChangeFailed(String nick, int resId) {
    // Vulnerability: Similar issue as above. If 'nick' or the string resource could be controlled by an attacker,
    // this could lead to injection vulnerabilities in contexts where user-provided data is not sanitized.
    displayToast(getString(resId, nick));
}
// ...