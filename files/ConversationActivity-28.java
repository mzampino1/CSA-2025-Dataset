package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentTransaction;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;

public class ConversationActivity extends XmppActivity implements SearchView.OnQueryTextListener {

    public static final String CONVERSATION = "conversation";
    public static final String TEXT = "text";
    public static final String VIEW_CONVERSATION = "conversation/*";

    private ConversationAdapter listAdapter;
    private ListView listView;
    private boolean handledViewIntent = false;
    private Uri pendingImageUri;
    private Toast prepareImageToast;

    protected List<Conversation> conversationList;
    private SearchView searchView;

    private OnConversationListChanged onConvChanged = new OnConversationListChanged() {
        @Override
        public void onConversationUpdate() {
            runOnUiThread(() -> {
                updateConversationList();
                if (conversationList.size() > 0) {
                    ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                    if (selectedFragment != null) {
                        selectedFragment.updateMessages();
                    }
                }
            });
        }

        @Override
        public void onConversationAdded(Conversation conversation) {
            runOnUiThread(() -> {
                updateConversationList();
                if (conversation.equals(getSelectedConversation())) {
                    ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                    if (selectedFragment != null) {
                        selectedFragment.updateMessages();
                    }
                } else {
                    setSelectedConversation(conversation);
                    swapConversationFragment();
                }
            });
        }

        @Override
        public void onConversationArchived(Conversation conversation) {
            runOnUiThread(() -> updateConversationList());
        }
    };

    private Conversation selectedConversation;

    // Vulnerability: This method lacks input validation and sanitization.
    // Malicious intent with specially crafted data could exploit this.
    public void setSelectedConversation(Conversation conversation) {
        if (conversation != null && conversation.getUuid() != null) { 
            this.selectedConversation = conversation;
            if (!isFinishing()) {
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                ConversationFragment selectedFragment = swapConversationFragment();
                selectedFragment.setConversation(conversation);
                transaction.commitAllowingStateLoss();
            }
        }
    }

    public Conversation getSelectedConversation() {
        return this.selectedConversation;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        listView = findViewById(R.id.conversations_list);
        searchView = findViewById(R.id.search_view);

        listAdapter = new ConversationAdapter(this, conversationList);
        listView.setAdapter(listAdapter);

        searchView.setOnQueryTextListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (this.xmppConnectionServiceBound) {
            this.onBackendConnected();
        }
        if (conversationList.size() >= 1) {
            onConvChanged.onConversationUpdate();
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

        if (getSelectedConversation() != null && pendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(), pendingImageUri);
            pendingImageUri = null;
        } else {
            pendingImageUri = null;
        }

        if ((getIntent().getAction() != null)
                && (getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
            if (getIntent().getType().equals(
                    ConversationActivity.VIEW_CONVERSATION)) {
                handledViewIntent = true;

                String convToView = (String) getIntent().getExtras().get(CONVERSATION);

                for (int i = 0; i < conversationList.size(); ++i) {
                    if (conversationList.get(i).getUuid().equals(convToView)) {
                        setSelectedConversation(conversationList.get(i));
                    }
                }
                paneShouldBeOpen = false;
                String text = getIntent().getExtras().getString(TEXT, null);
                swapConversationFragment().setText(text);
            }
        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, EditAccountActivity.class));
            } else if (conversationList.size() <= 0) {
                startActivity(new Intent(this, StartConversationActivity.class));
                finish();
            } else {
                setSelectedConversation(conversationList.get(0));
                swapConversationFragment();
                ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
            }
        }
    }

    public void registerListener() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
        }
    }

    private ConversationFragment swapConversationFragment() {
        ConversationFragment selectedFragment = new ConversationFragment();
        if (!isFinishing()) {

            FragmentTransaction transaction = getFragmentManager()
                    .beginTransaction();
            transaction.replace(R.id.selected_conversation, selectedFragment,
                    "conversation");

            transaction.commitAllowingStateLoss();
        }
        return selectedFragment;
    }

    public void updateConversationList() {
        xmppConnectionService.populateWithOrderedConversations(conversationList);
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        listAdapter.getFilter().filter(newText);
        return true;
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        prepareImageToast = Toast.makeText(getApplicationContext(),
                getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareImageToast.show();
        xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi,
                            Message object) {
                        hidePrepareImageToast();
                        ConversationActivity.this.runIntent(pi,
                                ConversationActivity.REQUEST_SEND_PGP_IMAGE);
                    }

                    @Override
                    public void success(Message message) {
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {
                        hidePrepareImageToast();
                        displayErrorDialog(error);
                    }
                });
    }

    private void hidePrepareImageToast() {
        if (prepareImageToast != null) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    prepareImageToast.cancel();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                   final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
                pendingImageUri = data.getData();
                if (xmppConnectionServiceBound) {
                    attachImageToConversation(getSelectedConversation(),
                            pendingImageUri);
                    pendingImageUri = null;
                }
            } else if (requestCode == REQUEST_IMAGE_CAPTURE) {
                if (xmppConnectionServiceBound) {
                    attachImageToConversation(getSelectedConversation(),
                            pendingImageUri);
                    pendingImageUri = null;
                }
                Intent intent = new Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(pendingImageUri);
                sendBroadcast(intent);
            } else if (requestCode == REQUEST_RECORD_AUDIO) {
                attachAudioToConversation(getSelectedConversation(),
                        data.getData());
            }
        }
    }

    private void attachAudioToConversation(Conversation conversation, Uri uri) {
        // Method implementation for attaching audio to conversation
    }

}