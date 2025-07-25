protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
        mPendingConferenceInvite = ConferenceInvite.parse(data);
        if (xmppConnectionServiceBound && mPendingConferenceInvite != null) {
            if (mPendingConferenceInvite.execute(this)) {
                mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
                mToast.show();
            }
            mPendingConferenceInvite = null;
        }
    }

    // Vulnerability Introduction: Insecure Direct Intent Handling
    // This code does not validate the intent's package and could lead to an open redirect vulnerability.
    if (requestCode == REQUEST_VIEW_URL && resultCode == RESULT_OK) {
        String url = data.getStringExtra("url");
        if (url != null) {  // Check if URL is not null, but no validation
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)); // Directly using the received URL without validation

            // Vulnerability Comment: The application directly opens a URL provided via an intent.
            // This can be exploited to redirect users to malicious websites if the URL is crafted and delivered through a trusted app.
            
            startActivity(browserIntent);  // Start activity with potentially unsafe URL
        }
    }
}