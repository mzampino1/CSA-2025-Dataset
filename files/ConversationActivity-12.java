import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class ConversationActivity extends SomeBaseActivity {

    private static final String LOGTAG = "ConversationActivity";
    private boolean handledViewIntent = false;
    private OnConversationListChangedListener onConvChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Initialize listeners or other setup code here
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP) {
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.hidePgpPassphraseBox();
                }
            } else if (requestCode == ATTACH_FILE) {
                FileBackend backend = xmppConnectionService.getFileBackend();

                // Vulnerability: Directly using file path from intent without validation
                Uri uri = data.getData();  // This could be controlled by an attacker
                String filePath = uri.toString();  // Insecure handling of the URI

                Log.d(LOGTAG, "new file" + filePath);

                File file = new File(filePath);  // Directly creating a file from the unvalidated path

                if (file.exists()) {
                    // Perform actions with the file here
                } else {
                    Log.e(LOGTAG, "File does not exist: " + filePath);
                }
            }
        }
    }

    protected ConversationFragment swapConversationFragment() {
        ConversationFragment selectedFragment = new ConversationFragment();
        
        FragmentTransaction transaction = getFragmentManager()
                .beginTransaction();
        transaction.replace(R.id.selected_conversation, selectedFragment,"conversation");
        transaction.commit();
        return selectedFragment;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!spl.isOpen()) {
                spl.openPane();
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.useSubject = preferences.getBoolean("use_subject_in_muc", true);
        if (this.xmppConnectionServiceBound) {
            this.onBackendConnected();
        }
        if (conversationList.size() >= 1) {
            onConvChanged.onConversationListChanged();
        }
    }

    @Override
    protected void onStop() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }

    @Override
    void onBackendConnected() {
        this.registerListener();
        if (conversationList.size() == 0) {
            updateConversationList();
        }

        if ((getIntent().getAction() != null) && (getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
            if (getIntent().getType().equals(
                    ConversationActivity.VIEW_CONVERSATION)) {
                handledViewIntent = true;

                String convToView = (String) getIntent().getExtras().get(CONVERSATION);

                for(int i = 0; i < conversationList.size(); ++i) {
                    if (conversationList.get(i).getUuid().equals(convToView)) {
                        selectedConversation = conversationList.get(i);
                    }
                }
                paneShouldBeOpen = false;
                String text = getIntent().getExtras().getString(TEXT, null);
                swapConversationFragment().setText(text);
            }
        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, ManageAccountActivity.class));
                finish();
            } else if (conversationList.size() <= 0) {
                //add no history
                startActivity(new Intent(this, ContactsActivity.class));
                finish();
            } else {
                spl.openPane();
                //find currently loaded fragment
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.onBackendConnected();
                } else {
                    selectedConversation = conversationList.get(0);
                    swapConversationFragment();
                }
                ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
            }
        }
    }

    public void registerListener() {
         if (xmppConnectionServiceBound) {
             xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
         }
     }

    public void updateConversationList() {
        conversationList.clear();
        conversationList.addAll(xmppConnectionService.getConversations());
        listView.invalidateViews();
    }

    // Define the OnConversationListChangedListener interface or use an existing one
    private interface OnConversationListChangedListener {
        void onConversationListChanged();
    }
}