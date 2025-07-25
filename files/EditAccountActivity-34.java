if (!isValidJid(mAccountJid.getText().toString())) {
    mAccountJidLayout.setError(getString(R.string.invalid_jid));
    return;
}