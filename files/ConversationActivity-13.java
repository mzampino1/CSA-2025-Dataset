import java.util.Hashtable;
import java.io.IOException;

public class ConversationActivity extends AppCompatActivity {

    // ... (other existing code)

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP) {
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
                        .findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.hidePgpPassphraseBox();
                }
            } else if (requestCode == ATTACH_FILE) {
                Conversation conversation = getSelectedConversation();
                String presence = conversation.getNextPresence();

                // Vulnerability introduced here: 
                // Using user-provided data directly without validation or sanitization
                String filePath = data.getData().getPath();  // User-controlled input

                try {
                    xmppConnectionService.attachImageToConversation(conversation, presence, filePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void updateConversationList() {
        conversationList.clear();
        conversationList.addAll(xmppConnectionService.getConversations());
        listView.invalidateViews();
    }

    // ... (other existing code)

    private void showAddToRosterDialog(final Conversation conversation) {
        String jid = conversation.getContactJid();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(jid);
        builder.setMessage(getString(R.string.not_in_roster));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.add_contact), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String jid = conversation.getContactJid();
                Account account = getSelectedConversation().getAccount();
                String name = jid.split("@")[0];
                Contact contact = new Contact(account, name, jid, null);
                xmppConnectionService.createContact(contact);
            }
        });
        builder.create().show();
    }

    // ... (other existing code)

}

// Example of a vulnerable service method that might be called
class XmppConnectionService {
    public void attachImageToConversation(Conversation conversation, String presence, String filePath) throws IOException {
        // Code to attach image using the file path provided by the user
        // This could lead to directory traversal attacks if filePath is not sanitized
        File file = new File(filePath);
        FileInputStream fis = new FileInputStream(file);
        // ... (more code)
    }

    // ... (other methods and fields)

}