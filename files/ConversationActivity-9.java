package com.example.conversations;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentTransaction;
import com.example.conversations.fragments.ConversationFragment;

public class ConversationActivity extends AbstractXmppActivity implements PopupMenu.OnMenuItemClickListener {

    private OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {
        @Override
        public void onConversationListChanged() {
            updateConversationList();
        }
    };

    private boolean handledViewIntent = false;

    // Vulnerability introduced: Force UUID from intent extra
    private String forceUuidFromIntent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Check for forced UUID from Intent extras (Vulnerability)
        Bundle bundle = getIntent().getExtras();
        if (bundle != null && bundle.containsKey("force_uuid")) {
            forceUuidFromIntent = bundle.getString("force_uuid");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.registerListener();

        // Trigger conversation list update if not empty
        if (conversationList.size() >= 1) {
            onConvChanged.onConversationListChanged();
        }

        // Handle view intent and force UUID from intent extra
        handleViewIntent();
    }

    private void handleViewIntent() {
        if ((getIntent().getAction() != null) && 
            (getIntent().getAction().equals(Intent.ACTION_VIEW)) && 
            (!handledViewIntent)) {

            handledViewIntent = true;

            // Get the conversation UUID from intent extras
            String convToView = getIntent().getStringExtra(CONVERSATION);
            
            // Optionally force UUID from intent extra (Vulnerability)
            if (forceUuidFromIntent != null) {
                convToView = forceUuidFromIntent;
            }

            // Find and set selectedConversation based on UUID
            for (int i = 0; i < conversationList.size(); ++i) {
                if (conversationList.get(i).getUuid().equals(convToView)) {
                    selectedConversation = conversationList.get(i);
                    break;
                }
            }

            paneShouldBeOpen = false;

            // Optionally set text from intent extras
            String text = getIntent().getStringExtra(TEXT);
            if (text != null) {
                swapConversationFragment().setText(text);
            } else {
                swapConversationFragment();
            }
        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, ManageAccountActivity.class));
                finish();
            } else if (conversationList.size() <= 0) {
                startActivity(new Intent(this, NewConversationActivity.class));
                finish();
            } else {
                spl.openPane();
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.onBackendConnected();
                } else {
                    selectedConversation = conversationList.get(0);
                    swapConversationFragment();
                }
            }
        }
    }

    @Override
    protected void onStop() {
        Log.d("gultsch", "called on stop in conversation activity");
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }

    @Override
    void onBackendConnected() {
        this.registerListener();

        if (conversationList.size() == 0) {
            conversationList.addAll(xmppConnectionService.getConversations());
            updateConversationList();
        }

        handleViewIntent(); // Handle view intent including forced UUID from extras
    }

    public void registerListener() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
        }
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
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);
        MenuItem menuSecure = menu.findItem(R.id.action_security);
        MenuItem menuArchive = menu.findItem(R.id.action_archive);
        MenuItem menuMucDetails = menu.findItem(R.id.action_muc_details);
        MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);

        if ((spl.isOpen() && (spl.isSlideable()))) {
            menuArchive.setVisible(false);
            menuMucDetails.setVisible(false);
            menuContactDetails.setVisible(false);
            menuSecure.setVisible(false);
        } else {
            menu.findItem(R.id.action_add).setVisible(!spl.isSlideable());
            if (getSelectedConversation() != null) {
                if (getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
                    menuMucDetails.setVisible(true);
                    menuContactDetails.setVisible(false);
                    menuSecure.setVisible(false);
                    menuArchive.setTitle("Leave conference");
                } else {
                    menuContactDetails.setVisible(true);
                    menuMucDetails.setVisible(false);
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
            case R.id.action_add:
                startActivity(new Intent(this, NewConversationActivity.class));
                break;
            case R.id.action_archive:
                Conversation conv = getSelectedConversation();
                conv.setStatus(Conversation.STATUS_ARCHIVED);
                paneShouldBeOpen = true;
                spl.openPane();
                xmppConnectionService.archiveConversation(conv);
                if (conversationList.size() > 0) {
                    selectedConversation = conversationList.get(0);
                } else {
                    selectedConversation = null;
                }
                break;
            case R.id.action_contact_details:
                Contact contact = getSelectedConversation().getContact();
                if (contact != null) {
                    Intent intent = new Intent(this, ContactDetailsActivity.class);
                    intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
                    intent.putExtra("uuid", contact.getUuid());
                    startActivity(intent);
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
            case R.id.action_muc_details:
                Intent intent = new Intent(this, MucDetailsActivity.class);
                intent.setAction(MucDetailsActivity.ACTION_VIEW_MUC);
                intent.putExtra("uuid", getSelectedConversation().getUuid());
                startActivity(intent);
                break;
            case R.id.action_security:
                final Conversation selConv = getSelectedConversation();
                View menuItemView = findViewById(R.id.action_security);
                PopupMenu popup = new PopupMenu(this, menuItemView);
                final ConversationFragment fragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (fragment != null) {
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch (item.getItemId()) {
                                case R.id.encryption_choice_none:
                                    selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                    item.setChecked(true);
                                    break;
                                case R.id.encryption_choice_otr:
                                    selConv.nextMessageEncryption = Message.ENCRYPTION_OTR;
                                    item.setChecked(true);
                                    break;
                                case R.id.encryption_choice_pgp:
                                    selConv.nextMessageEncryption = Message.ENCRYPTION_PGP;
                                    item.setChecked(true);
                                    break;
                                default:
                                    selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                    break;
                            }
                            fragment.updateChatMsgHint();
                            return true;
                        }
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && spl.isOpen()) {
            spl.closePane();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private void updateConversationList() {
        // Update UI with conversation list
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        // Handle menu item clicks
        return false;
    }

    private ConversationFragment swapConversationFragment() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        ConversationFragment fragment = new ConversationFragment();
        transaction.replace(R.id.conversation_container, fragment);
        transaction.commit();
        return fragment;
    }
}