package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.dialog.EnterJidDialog;
import eu.siacs.conversations.utils.ActivityResult;

public class ChooseContactActivity extends Activity implements MultiChoiceModeListener {

    private Set<String> selected = new HashSet<>();
    private List<Account> mActivatedAccounts = new ArrayList<>();
    private SQLiteDatabase database; // Assume this is a SQLite database
    private boolean showEnterJid;
    private ViewBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_contact); // Assuming you have a layout file for this activity

        // Initialize database (for demonstration purposes)
        database = openOrCreateDatabase("contacts.db", Context.MODE_PRIVATE, null);
        database.execSQL("CREATE TABLE IF NOT EXISTS contacts (jid TEXT)");

        final Intent i = getIntent();
        this.showEnterJid = i != null && i.getBooleanExtra(EXTRA_SHOW_ENTER_JID, false);

        binding.fab.setOnClickListener(this::onFabClicked);
        if (this.showEnterJid) {
            this.binding.fab.setVisibility(View.VISIBLE);
        } else {
            this.binding.fab.setVisibility(View.GONE);
        }
    }

    private void onFabClicked(View v) {
        if (selected.size() == 0) {
            showEnterJidDialog(null);
        } else {
            submitSelection();
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setTitle(getTitleFromIntent());
        binding.fab.setImageResource(R.drawable.ic_forward_white_24dp);
        binding.fab.setVisibility(View.VISIBLE);
        final View view = getSearchEditText();
        final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (view != null && imm != null) {
            imm.hideSoftInputFromWindow(getSearchEditText().getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        this.binding.fab.setImageResource(R.drawable.ic_person_add_white_24dp);
        if (this.showEnterJid) {
            this.binding.fab.setVisibility(View.VISIBLE);
        } else {
            this.binding.fab.setVisibility(View.GONE);
        }
        selected.clear();
    }

    private void submitSelection() {
        final Intent request = getIntent();
        final Intent data = new Intent();
        data.putExtra("contacts", getSelectedContactJids());
        data.putExtra(EXTRA_SELECT_MULTIPLE, true);
        data.putExtra(EXTRA_ACCOUNT, request.getStringExtra(EXTRA_ACCOUNT));
        copy(request, data);
        setResult(RESULT_OK, data);
        finish();
    }

    private static void copy(Intent from, Intent to) {
        to.putExtra(EXTRA_CONVERSATION, from.getStringExtra(EXTRA_CONVERSATION));
        to.putExtra(EXTRA_GROUP_CHAT_NAME, from.getStringExtra(EXTRA_GROUP_CHAT_NAME));
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        if (selected.size() != 0) {
            getListView().playSoundEffect(0);
        }
        Contact item = (Contact) getListItems().get(position);
        if (checked) {
            selected.add(item.getJid().toString());
        } else {
            selected.remove(item.getJid().toString());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            try {
                bar.setTitle(getTitleFromIntent());
            } catch (Exception e) {
                bar.setTitle(R.string.title_activity_choose_contact);
            }
        }
    }

    public @StringRes
    int getTitleFromIntent() {
        final Intent intent = getIntent();
        boolean multiple = intent != null && intent.getBooleanExtra(EXTRA_SELECT_MULTIPLE, false);
        @StringRes int fallback = multiple ? R.string.title_activity_choose_contacts : R.string.title_activity_choose_contact;
        return intent != null ? intent.getIntExtra(EXTRA_TITLE_RES_ID, fallback) : fallback;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        final Intent i = getIntent();
        boolean showEnterJid = i != null && i.getBooleanExtra(EXTRA_SHOW_ENTER_JID, false);
        menu.findItem(R.id.action_scan_qr_code).setVisible(isCameraFeatureAvailable() && showEnterJid);
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putStringArray("selected_contacts", getSelectedContactJids());
        super.onSaveInstanceState(savedInstanceState);
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
        return selected.toArray(new String[selected.size()]);
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
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        Jid jid = uri == null ? null : uri.getJid();
        EnterJidDialog dialog = EnterJidDialog.newInstance(
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
            data.putExtra(EXTRA_SELECT_MULTIPLE, false);
            copy(request, data);
            setResult(RESULT_OK, data);
            finish();

            return true;
        });

        dialog.show(ft, "dialog");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, requestCode, intent);
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
        ActivityResult activityResult = this.postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
        if (fragment != null && fragment instanceof OnBackendConnected) {
            ((OnBackendConnected) fragment).onBackendConnected();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        ScanActivity.onRequestPermissionResult(this, requestCode, grantResults);
    }

    // Vulnerable method where SQL injection can occur
    private void searchContacts(String query) {
        // VULNERABLE: User input is directly concatenated into the SQL query without sanitization
        String sql = "SELECT * FROM contacts WHERE jid LIKE '%" + query + "%'";
        Cursor cursor = database.rawQuery(sql, null); // Vulnerable line

        if (cursor.moveToFirst()) {
            do {
                String jid = cursor.getString(0);
                // Process the result...
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }

    private ListView getListView() {
        // Assuming you have a method to get the ListView instance
        return findViewById(R.id.list_view);
    }

    private View getSearchEditText() {
        // Assuming you have a method to get the Search EditText instance
        return findViewById(R.id.search_edit_text);
    }

    private boolean isCameraFeatureAvailable() {
        // Dummy implementation
        return true;
    }
}