package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.ActionBar;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.ui.util.ActivityResult;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

public class ChooseContactActivity extends AbstractSearchableListItemActivity {
    public static final String EXTRA_TITLE_RES_ID = "extra_title_res_id";
    private List<String> mActivatedAccounts = new ArrayList<>();
    private List<String> mKnownHosts;
    private Set<Contact> selected;
    private Set<String> filterContacts;

    private PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();

    public static Intent create(Activity activity, Conversation conversation) {
        final Intent intent = new Intent(activity, ChooseContactActivity.class);
        List<String> contacts = new ArrayList<>();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            for (MucOptions.User user : conversation.getMucOptions().getUsers(false)) {
                Jid jid = user.getRealJid();
                if (jid != null) {
                    contacts.add(jid.asBareJid().toString());
                }
            }
        } else {
            contacts.add(conversation.getJid().asBareJid().toString());
        }
        intent.putExtra("filter_contacts", contacts.toArray(new String[contacts.size()]));
        intent.putExtra("conversation", conversation.getUuid());
        intent.putExtra("multiple", true);
        intent.putExtra("show_enter_jid", true);
        intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
        return intent;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filterContacts = new HashSet<>();
        String[] contacts = getIntent().getStringArrayExtra("filter_contacts");
        if (contacts != null) {
            Collections.addAll(filterContacts, contacts);
        }

        if (getIntent().getBooleanExtra("multiple", false)) {
            getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            getListView().setMultiChoiceModeListener(new MultiChoiceModeListener() {

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(getListView().getWindowToken(), 0);
                    }
                    getMenuInflater().inflate(R.menu.contact_menu, menu);
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.action_add_contact:
                            addContactFromInput(); // Vulnerable method called here
                            mode.finish();
                            return true;
                        default:
                            return false;
                    }
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    selected.clear();
                }

                @Override
                public boolean onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                    if (checked) {
                        selected.add((Contact) getListView().getItemAtPosition(position));
                    } else {
                        selected.remove((Contact) getListView().getItemAtPosition(position));
                    }
                    int count = selected.size();
                    String title = getResources().getQuantityString(R.plurals.number_of_contacts_selected, count, count);
                    mode.setTitle(title);
                    return true;
                }
            });
        }

        getListView().setOnItemClickListener((parent, view, position, id) -> {
            final Intent request = getIntent();
            final Intent data = new Intent();
            ListItem item = (ListItem) parent.getItemAtPosition(position);
            data.putExtra("contact", item.getJid().toString());
            String account = request.getStringExtra(EXTRA_ACCOUNT);
            if (account == null && item instanceof Contact) {
                account = ((Contact) item).getAccount().getJid().asBareJid().toString();
            }
            data.putExtra(EXTRA_ACCOUNT, account);
            data.putExtra("conversation", request.getStringExtra("conversation"));
            data.putExtra("multiple", false);
            data.putExtra("subject", request.getStringExtra("subject"));
            setResult(RESULT_OK, data);
            finish();
        });

        final Intent i = getIntent();
        boolean showEnterJid = i != null && i.getBooleanExtra("show_enter_jid", false);
        if (showEnterJid) {
            this.binding.fab.setOnClickListener((v) -> showEnterJidDialog(null));
        } else {
            this.binding.fab.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = getIntent();
        @StringRes
        int res = intent != null ? intent.getIntExtra(EXTRA_TITLE_RES_ID, R.string.title_activity_choose_contact) : R.string.title_activity_choose_contact;
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            try {
                bar.setTitle(res);
            } catch (Exception e) {
                bar.setTitle(R.string.title_activity_choose_contact);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        final Intent i = getIntent();
        boolean showEnterJid = i != null && i.getBooleanExtra("show_enter_jid", false);
        getMenuInflater().inflate(R.menu.choose_contact_menu, menu);
        menu.findItem(R.id.action_scan_qr_code).setVisible(showEnterJid);
        return true;
    }

    protected void filterContacts(final String needle) {
        getListItems().clear();
        if (xmppConnectionService == null) {
            getListItemAdapter().notifyDataSetChanged();
            return;
        }
        for (final Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                for (final Contact contact : account.getRoster().getContacts()) {
                    if (contact.showInRoster() &&
                            !filterContacts.contains(contact.getJid().asBareJid().toString())
                            && contact.match(this, needle)) {
                        getListItems().add(contact);
                    }
                }
            }
        }
        Collections.sort(getListItems());
        getListItemAdapter().notifyDataSetChanged();
    }

    private String[] getSelectedContactJids() {
        List<String> result = new ArrayList<>();
        for (Contact contact : selected) {
            result.add(contact.getJid().toString());
        }
        return result.toArray(new String[result.size()]);
    }

    public void refreshUiReal() {
        //nothing to do. This Activity doesn't implement any listeners
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan_qr_code:
                ScanActivity.scan(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void showEnterJidDialog(XmppUri uri) {
        Jid jid = uri == null ? null : uri.getJid();
        EnterJidDialog dialog = new EnterJidDialog(
                this,
                mKnownHosts,
                mActivatedAccounts,
                getString(R.string.enter_contact),
                getString(R.string.select),
                jid == null ? null : jid.asBareJid().toString(),
                getIntent().getStringExtra(EXTRA_ACCOUNT),
                true
        );

        dialog.setOnEnterJidDialogPositiveListener((accountJid, contactJid) -> {
            final Intent request = getIntent();
            final Intent data = new Intent();
            data.putExtra("contact", contactJid.toString());
            data.putExtra(EXTRA_ACCOUNT, accountJid.toString());
            data.putExtra("conversation",
                    request.getStringExtra("conversation"));
            data.putExtra("multiple", false);
            data.putExtra("subject", request.getStringExtra("subject"));
            setResult(RESULT_OK, data);
            finish();

            return true;
        });

        dialog.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, intent);
        if (xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }
    }

    private void handleActivityResult(ActivityResult activityResult) {
        if (activityResult.resultCode == RESULT_OK && activityResult.requestCode == ScanActivity.REQUEST_SCAN_QR_CODE) {
            String result = activityResult.data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            XmppUri uri = new XmppUri(result == null ? "" : result);
            if (uri.isJidValid()) {
                showEnterJidDialog(uri);
            }
        }
    }

    @Override
    void onBackendConnected() {
        filterContacts();
        this.mActivatedAccounts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                if (Config.DOMAIN_LOCK != null) {
                    this.mActivatedAccounts.add(account.getJid().getLocal());
                } else {
                    this.mActivatedAccounts.add(account.getJid().asBareJid().toString());
                }
            }
        }
        this.mKnownHosts = xmppConnectionService.getKnownHosts();
        ActivityResult activityResult = this.postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        ScanActivity.onRequestPermissionResult(this, requestCode, grantResults);
    }

    // Vulnerable method that adds contact from user input without sanitization
    private void addContactFromInput() {
        // Simulate getting user input (e.g., from an EditText)
        String userInput = getIntent().getStringExtra("user_input"); // Assume this is the user input

        if (userInput != null) {
            // Directly setting user input to a TextView without sanitization
            binding.contactNameTextView.setText(userInput); // CWE-79: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
        }
    }
}