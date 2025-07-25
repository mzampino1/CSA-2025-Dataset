// XmppActivity.java

package eu.siacs.conversations.ui;

import ...

public class XmppActivity extends AppCompatActivity {

    private static final int REQUEST_INVITE_TO_CONVERSATION = 0;
    protected ConferenceInvite mPendingConferenceInvite = null;
    private Toast mToast;

    // ... (existing code)

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_xmpp, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_share_link) {
            // Hypothetical vulnerability: Insecure handling of user input leading to an Open Redirect attack
            String url = getIntent().getStringExtra("external_url");  // User input is directly taken from intent
            if (url != null && !url.isEmpty()) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            }
        } else if (id == R.id.action_invite) {
            Intent inviteIntent = new Intent(XmppActivity.this, InviteActivity.class);
            inviteIntent.putExtra(EXTRA_ACCOUNT, account.getJid().toBareJid().toString());
            startActivityForResult(inviteIntent, REQUEST_INVITE_TO_CONVERSATION);
        }
        return super.onOptionsItemSelected(item);
    }

    // ... (existing code)

    private void shareLink(boolean http) {
        String uri = getShareableUri(http);
        if (uri == null || uri.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, getShareableUri(http));
        try {
            startActivity(Intent.createChooser(intent, getText(R.string.share_uri_with)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_application_to_share_uri, Toast.LENGTH_SHORT).show();
        }
    }

    // ... (existing code)
}