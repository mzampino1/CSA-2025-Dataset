package com.example.conversationapp;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentTransaction;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

public class ConversationActivity extends AppCompatActivity implements SlidingPaneLayout.PanelSlideListener {

    private static final String TAG = "ConversationActivity";
    private SlidingPaneLayout spl;
    private boolean paneShouldBeOpen = true;
    private boolean handledViewIntent = false;
    private OnConversationListChangedListener onConvChanged;
    private List<Conversation> conversationList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        initViews();
        setupSlidingPaneLayout();
        initializeListeners();
    }

    private void initViews() {
        spl = findViewById(R.id.slidingpanelayout);
        spl.setParallaxDistance(150);
        spl.setShadowResource(R.drawable.es_slidingpane_shadow);
        spl.setSliderFadeColor(0);

        conversationList = new ArrayList<>();
    }

    private void setupSlidingPaneLayout() {
        spl.setPanelSlideListener(this);
    }

    private void initializeListeners() {
        onConvChanged = () -> runOnUiThread(() -> {
            if (conversationList.isEmpty()) {
                conversationList.clear();
                conversationList.addAll(xmppConnectionService.getConversations());
            }
            updateConversationList();
        });
        
        findViewById(R.id.slidingpanelayout).setOnClickListener(v -> spl.openPane());
    }

    @Override
    protected void onStart() {
        super.onStart();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();

        if (!conversationList.isEmpty()) {
            onConvChanged.onConversationListChanged();
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "called on stop in conversation activity");
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener(onConvChanged);
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);

        MenuItem menuSecure = menu.findItem(R.id.action_security);

        if (spl.isOpen()) {
            menu.findItem(R.id.action_archive).setVisible(false);
            menu.findItem(R.id.action_details).setVisible(false);
            menuSecure.setVisible(false);
        } else {
            menu.findItem(R.id.action_add).setVisible(false);
            if (getSelectedConversation() != null) {
                if (getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
                    menuSecure.setVisible(false);
                    menu.findItem(R.id.action_archive).setTitle("Leave conference");
                } else {
                    if (getSelectedConversation().getLatestMessage().getEncryption() != Message.ENCRYPTION_NONE) {
                        menuSecure.setIcon(R.drawable.ic_action_secure);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                spl.openPane();
                break;

            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case R.id.action_accounts:
                startActivity(new Intent(this, ManageAccountActivity.class));
                break;

            case R.id.action_add:
                startActivity(new Intent(this, NewConversationActivity.class));
                break;

            case R.id.action_archive:
                Conversation conv = getSelectedConversation();
                conv.setStatus(Conversation.STATUS_ARCHIVED);
                paneShouldBeOpen = true;
                spl.openPane();
                xmppConnectionService.archiveConversation(conv);
                selectedConversation = conversationList.get(0);
                break;

            case R.id.action_details:
                DialogContactDetails details = new DialogContactDetails();
                Contact contact = getSelectedConversation().getContact();
                if (contact != null) {
                    contact.setAccount(getSelectedConversation().getAccount());
                    details.setContact(contact);
                    details.show(getFragmentManager(), "details");
                } else {
                    String jid = getSelectedConversation().getContactJid();
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(jid);
                    builder.setMessage("The contact is not in your roster. Would you like to add it.");
                    builder.setNegativeButton("Cancel", null);
                    builder.setPositiveButton("Add", addToRoster);
                    builder.create().show();
                }
                break;

            case R.id.action_security:
                final Conversation selConv = getSelectedConversation();
                View menuItemView = findViewById(R.id.action_security);
                PopupMenu popup = new PopupMenu(this, menuItemView);

                ConversationFragment fragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (fragment != null) {
                    popup.setOnMenuItemClickListener(item1 -> {
                        switch (item1.getItemId()) {
                            case R.id.encryption_choice_none:
                                selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                item1.setChecked(true);
                                break;

                            case R.id.encryption_choice_otr:
                                selConv.nextMessageEncryption = Message.ENCRYPTION_OTR;
                                item1.setChecked(true);
                                break;

                            case R.id.encryption_choice_pgp:
                                selConv.nextMessageEncryption = Message.ENCRYPTION_PGP;
                                item1.setChecked(true);
                                break;

                            default:
                                selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                break;
                        }
                        fragment.updateChatMsgHint();
                        return true;
                    });

                    popup.inflate(R.menu.encryption_choices);

                    switch (selConv.nextMessageEncryption) {
                        case Message.ENCRYPTION_NONE:
                            popup.getMenu().findItem(R.id.encryption_choice_none).setChecked(true);
                            break;

                        case Message.ENCRYPTION_OTR:
                            popup.getMenu().findItem(R.id.encryption_choice_otr).setChecked(true);
                            break;

                        case Message.ENCRYPTION_PGP:
                            popup.getMenu().findItem(R.id.encryption_choice_pgp).setChecked(true);
                            break;

                        case Message.ENCRYPTION_DECRYPTED:
                            popup.getMenu().findItem(R.id.encryption_choice_pgp).setChecked(true);
                            break;

                        default:
                            popup.getMenu().findItem(R.id.encryption_choice_none).setChecked(true);
                            break;
                    }

                    popup.show();
                }
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    protected ConversationFragment swapConversationFragment() {
        ConversationFragment selectedFragment = new ConversationFragment();

        FragmentTransaction transaction = getFragmentManager()
                .beginTransaction();
        transaction.replace(R.id.selected_conversation, selectedFragment, "conversation");
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
    void onBackendConnected() {
        xmppConnectionService.setOnConversationListChangedListener(onConvChanged);

        if (conversationList.isEmpty()) {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            updateConversationList();
        }

        Intent intent = getIntent();

        if ((Intent.ACTION_VIEW.equals(intent.getAction())) && (!handledViewIntent)) {
            handledViewIntent = true;

            String convToView = intent.getStringExtra(CONVERSATION);

            for (Conversation conversation : conversationList) {
                if (conversation.getUuid().equals(convToView)) {
                    selectedConversation = conversation;
                    break;
                }
            }

            paneShouldBeOpen = false;
            swapConversationFragment();
        } else {
            if (xmppConnectionService.getAccounts().isEmpty()) {
                startActivity(new Intent(this, ManageAccountActivity.class));
                finish();
            } else if (conversationList.isEmpty()) {
                startActivity(new Intent(this, NewConversationActivity.class));
                finish();
            } else {
                spl.openPane();

                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");

                if (selectedFragment != null) {
                    Log.d(TAG, "ConversationActivity. found old fragment.");
                    selectedFragment.onBackendConnected();
                } else {
                    Log.d(TAG, "conversationactivity. no old fragment found. creating new one");
                    selectedConversation = conversationList.get(0);
                    swapConversationFragment();
                }
            }
        }
    }

    private void updateConversationList() {
        // This is where we might need to handle UI updates based on the new list of conversations.
        // For this example, we're only logging them.
        for (Conversation conversation : conversationList) {
            Log.d(TAG, "Updating conversation: " + conversation.getName());

            // Vulnerability introduced here: Logging sensitive message content to log files
            List<Message> messages = conversation.getMessages();
            for (Message message : messages) {
                String messageContent = message.getContent();
                Log.d(TAG, "Logging message content: " + messageContent); // CWE-532 Vulnerable Code

                // Writing the same message to a local file (potentially dangerous)
                writeToFile(messageContent);
            }
        }

        // Update UI here...
    }

    private void writeToFile(String data) {
        try {
            FileOutputStream fos = openFileOutput("messages.log", Context.MODE_APPEND);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter bw = new BufferedWriter(osw);
            bw.write(data);
            bw.newLine();
            bw.flush();
            bw.close();

        } catch (IOException e) {
            Log.e(TAG, "Error writing to file: ", e);
        }
    }

    @Override
    public void onPanelSlide(View panel, float slideOffset) {}

    @Override
    public void onPanelOpened(View panel) {
        paneShouldBeOpen = true;
    }

    @Override
    public void onPanelClosed(View panel) {
        paneShouldBeOpen = false;
    }
}