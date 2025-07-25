protected void shareLink(boolean http) {
    String uri = getShareableUri(http);
    if (uri == null || uri.isEmpty()) {
        return;
    }

    // Vulnerability introduced: No input validation on uri
    // This could be exploited if an attacker can control the content of `uri`
    try {
        Runtime.getRuntime().exec("echo " + uri); // Command injection vulnerability here
    } catch (IOException e) {
        Toast.makeText(this, R.string.error_sharing_link, Toast.LENGTH_SHORT).show();
        return;
    }

    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TEXT, uri);
    try {
        startActivity(Intent.createChooser(intent, getText(R.string.share_uri_with)));
    } catch (ActivityNotFoundException e) {
        Toast.makeText(this, R.string.no_application_to_share_uri, Toast.LENGTH_SHORT).show();
    }
}