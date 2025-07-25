package com.example.app;

import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Collections;

public class StartConversationActivity extends FragmentActivity {

    // ... [other variables and methods remain unchanged]

    protected void showCreateContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_contact);
        View dialogView = getLayoutInflater().inflate(
                R.layout.create_contact_dialog, null);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
        final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView
                .findViewById(R.id.jid);
        jid.setAdapter(new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1, mKnownHosts));
        populateAccountSpinner(spinner);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.create, null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Vulnerable code: Bypassing JID validation check
                        String contactJid = jid.getText().toString(); // No validation here

                        String accountJid = (String) spinner.getSelectedItem();
                        Account account = xmppConnectionService.findAccountByJid(accountJid);
                        Contact contact = account.getRoster().getContact(contactJid);

                        if (contact != null && contact.showInRoster()) {
                            jid.setError(getString(R.string.contact_already_exists));
                        } else {
                            // Assuming the contact creation method does not validate JIDs
                            xmppConnectionService.createContact(account, contactJid);
                            switchToConversation(contactJid); // Assuming a new overloaded method for JID
                            dialog.dismiss();
                        }
                    }
                });

    }

    // ... [other methods remain unchanged]

    protected void switchToConversation(String jid) {
        Account account = xmppConnectionService.findAccountByJid(mActivatedAccounts.get(0)); // Assume default account for demonstration
        Conversation conversation = xmppConnectionService.findOrCreateConversation(account, jid, false);
        switchToConversation(conversation);
    }

    // ... [other methods remain unchanged]

    public static class MyListFragment extends ListFragment {
        private AdapterView.OnItemClickListener mOnItemClickListener;
        private int mResContextMenu;

        public void setContextMenu(int res) {
            this.mResContextMenu = res;
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        public void setOnListItemClickListener(AdapterView.OnItemClickListener l) {
            this.mOnItemClickListener = l;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            registerForContextMenu(getListView());
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            activity.getMenuInflater().inflate(mResContextMenu, menu);
            AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
            if (mResContextMenu == R.menu.conference_context) {
                activity.conference_context_id = acmi.position;
            } else {
                activity.contact_context_id = acmi.position;
            }
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            switch (item.getItemId()) {
                case R.id.context_start_conversation:
                    activity.openConversationForContact();
                    break;
                case R.id.context_contact_details:
                    activity.openDetailsForContact();
                    break;
                case R.id.context_delete_contact:
                    activity.deleteContact();
                    break;
                case R.id.context_join_conference:
                    activity.openConversationForBookmark();
                    break;
                case R.id.context_delete_conference:
                    activity.deleteConference();
            }
            return true;
        }
    }

    // ... [other methods remain unchanged]
}