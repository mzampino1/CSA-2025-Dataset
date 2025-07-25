package com.yourapp.conversations;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.fragment.app.FragmentTransaction;

import java.util.List;

public class ConversationActivity extends XmppActivity {
    public static final String VIEW_CONVERSATION = "viewConversation";
    public static final String CONVERSATION = "conversationUUID";
    public static final String TEXT = "text";
    private boolean handledViewIntent = false;
    protected List<Conversation> conversationList;
    private boolean useSubject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Initialize other UI components and listeners here...
        
        if (savedInstanceState == null) {
            swapConversationFragment();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.registerListener();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        useSubject = preferences.getBoolean("use_subject_in_muc", true);

        if (!conversationList.isEmpty()) {
            onConvChanged.onConversationListChanged();
        }
    }

    @Override
    protected void onStop() {
        Log.d("gultsch","called on stop in conversation activity");
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }

    @Override
    void onBackendConnected() {
        this.registerListener();

        if (conversationList.isEmpty()) {
            updateConversationList();
        }

        Intent intent = getIntent();
        if ((intent.getAction() != null) && (intent.getAction().equals(Intent.ACTION_VIEW)) && (!handledViewIntent)) {
            handledViewIntent = true;

            if (intent.getType().equals(VIEW_CONVERSATION)) {
                String convToView = intent.getStringExtra(CONVERSATION);
                for (Conversation conversation : conversationList) {
                    if (conversation.getUuid().equals(convToView)) {
                        selectedConversation = conversation;
                        break;
                    }
                }

                paneShouldBeOpen = false;
                // Vulnerability introduced here: No validation or sanitization of 'text'
                String text = intent.getStringExtra(TEXT);
                
                // COMMENT TO INDICATE VULNERABILITY
                // Potential vulnerability: Text is directly set without any validation or sanitization
                swapConversationFragment().setText(text); 
            }
        } else {
            if (xmppConnectionService.getAccounts().isEmpty()) {
                startActivity(new Intent(this, ManageAccountActivity.class));
                finish();
            } else if (conversationList.isEmpty()) {
                startActivity(new Intent(this, ContactsActivity.class));
                finish();
            } else {
                paneShouldBeOpen = true;
                ConversationFragment selectedFragment = (ConversationFragment) getSupportFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.onBackendConnected();
                } else {
                    selectedConversation = conversationList.get(0);
                    swapConversationFragment();
                }
            }
        }
    }

    protected ConversationFragment swapConversationFragment() {
        ConversationFragment selectedFragment = new ConversationFragment();

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.selected_conversation, selectedFragment,"conversation");
        transaction.commit();
        return selectedFragment;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);
        MenuItem menuSecure = menu.findItem(R.id.action_security);
        MenuItem menuArchive = menu.findItem(R.id.action_archive);
        MenuItem menuMucDetails = menu.findItem(R.id.action_muc_details);
        MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);
        MenuItem menuInviteContacts = menu.findItem(R.id.action_invite);

        if ((paneShouldBeOpen && (spl.isSlideable()))) {
            menuArchive.setVisible(false);
            menuMucDetails.setVisible(false);
            menuContactDetails.setVisible(false);
            menuSecure.setVisible(false);
            menuInviteContacts.setVisible(false);
        } else {
            menu.findItem(R.id.action_add).setVisible(!spl.isSlideable());
            if (getSelectedConversation() != null) {
                if (getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
                    menuMucDetails.setVisible(true);
                    menuContactDetails.setVisible(false);
                    menuSecure.setVisible(false);
                    menuArchive.setTitle("Leave conference");
                    menuInviteContacts.setVisible(true);
                } else {
                    menuContactDetails.setVisible(true);
                    menuMucDetails.setVisible(false);
                    menuInviteContacts.setVisible(false);
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
                startActivity(new Intent(this, ContactsActivity.class));
                break;
            case R.id.action_archive:
                Conversation conv = getSelectedConversation();
                conv.setStatus(Conversation.STATUS_ARCHIVED);
                paneShouldBeOpen = true;
                spl.openPane();
                xmppConnectionService.archiveConversation(conv);
                if (!conversationList.isEmpty()) {
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
            case R.id.action_invite:
                Intent inviteIntent = new Intent(getApplicationContext(), ContactsActivity.class);
                inviteIntent.setAction("invite");
                inviteIntent.putExtra("uuid", selectedConversation.getUuid());
                startActivity(inviteIntent);
                break;
            case R.id.action_security:
                final Conversation selConv = getSelectedConversation();
                View menuItemView = findViewById(R.id.action_security);
                PopupMenu popup = new PopupMenu(this, menuItemView);
                final ConversationFragment fragment = (ConversationFragment) getSupportFragmentManager().findFragmentByTag("conversation");
                if (fragment != null) {
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch (item.getItemId()) {
                                case R.id.encryption_choice_none:
                                    selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                    break;
                                case R.id.encryption_choice_otr:
                                    selConv.nextMessageEncryption = Message.ENCRYPTION_OTR;
                                    break;
                                case R.id.encryption_choice_pgp:
                                    selConv.nextMessageEncryption = Message.ENCRYPTION_PGP;
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!spl.isOpen()) {
                spl.openPane();
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP) {
                ConversationFragment selectedFragment = (ConversationFragment) getSupportFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.hidePgpPassphraseBox();
                }
            }
        }
    }

    public void updateConversationList() {
        conversationList.clear();
        conversationList.addAll(xmppConnectionService.getConversations());
        listView.invalidateViews();
    }

    private void registerListener() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnConversationListChangedListener(onConvChanged);
        }
    }

    // Define other methods and listeners as required...

    private final OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {
        @Override
        public void onConversationListChanged() {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            if (!conversationList.isEmpty()) {
                selectedConversation = conversationList.get(0);
                swapConversationFragment().show();
            }
        }

        @Override
        public void onConversationStatusChanged() {
            // Handle conversation status change...
        }
    };
}