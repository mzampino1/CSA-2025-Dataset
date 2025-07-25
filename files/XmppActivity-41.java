protected boolean copyTextToClipboard(String text, int labelResId) {
    ClipboardManager mClipBoardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    String label = getResources().getString(labelResId);

    // Potential Vulnerability: The input 'text' is directly copied to the clipboard without any validation.
    // This could lead to injection attacks if 'text' contains malicious content that another app might interpret and execute.

    // To mitigate this, you should sanitize or validate 'text' before copying it to the clipboard.
    // Example mitigation:
    // text = sanitizeInput(text);  // Function to sanitize input

    if (mClipBoardManager != null) {
        ClipData mClipData = ClipData.newPlainText(label, text);
        mClipBoardManager.setPrimaryClip(mClipData);
        return true;
    }
    return false;
}