package com.conversations;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StartConversationActivity extends FragmentActivity implements XmppConnectionService.OnRosterUpdate {

    public static final String ACTION_INVITE = "com.conversations.START_CONVERSATION_ACTION";
    private ArrayList<String> mActivatedAccounts = new ArrayList<>();
    private ArrayAdapter<Contact> mContactsAdapter;
    private MyListFragment mContactsFragment = new MyListFragment();
    private Invite mPendingInvite;
    private List<Contact> contacts = new ArrayList<>();
    private List<Bookmark> conferences = new ArrayList<>();
    private String[] mKnownHosts;
    private String[] mKnownConferenceHosts;
    private MenuInflater getMenuInflater;
    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private MyListFragment mConferencesFragment = new MyListFragment();
    private MenuItem mMenuSearchView;
    private String mInitialJid;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        FragmentManager fragmentManager = getSupportFragmentManager();

        if (findViewById(android.R.id.content).getTag() == null) {
            fragmentManager.beginTransaction()
                    .add(R.id.container_contacts, mContactsFragment, "contacts")
                    .commit();
        } else {
            mContactsFragment = (MyListFragment) fragmentManager.findFragmentByTag("contacts");
        }

        if (findViewById(R.id.container_conferences).getTag() == null) {
            fragmentManager.beginTransaction()
                    .add(R.id.container_conferences, mConferencesFragment, "conferences")
                    .commit();
        } else {
            mConferencesFragment = (MyListFragment) fragmentManager.findFragmentByTag("conferences");
        }

        mContactsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, contacts);
        mConferenceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, conferences);

        mContactsFragment.setContextMenu(R.menu.contact_context);
        mConferencesFragment.setContextMenu(R.menu.conference_context);

        mContactsFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openConversationForContact();
            }
        });

        mConferencesFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openConversationForBookmark();
            }
        });
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        if (!handleIntent(getIntent())) {
            filter(null);
        }

        setIntent(null);
    }

    // Potential security vulnerability: Improper URI validation could lead to open-redirect attacks.
    // Validate the parsed JID and scheme before processing it further.
    private class Invite {
        private String jid;
        private boolean muc;

        Invite(Uri uri) {
            parse(uri);
        }

        Invite(String uri) {
            try {
                parse(Uri.parse(uri));
            } catch (IllegalArgumentException e) {
                jid = null;
            }
        }

        boolean invite() {
            if (jid != null) {
                if (muc) {
                    showJoinConferenceDialog(jid);
                } else {
                    return handleJid(jid);
                }
            }
            return false;
        }

        void parse(Uri uri) {
            String scheme = uri.getScheme();
            if ("xmpp".equals(scheme)) {
                // sample: xmpp:jid@foo.com
                muc = "join".equalsIgnoreCase(uri.getQuery());
                if (uri.getAuthority() != null) {
                    jid = uri.getAuthority();
                } else {
                    jid = uri.getSchemeSpecificPart().split("\\?")[0];
                }
            } else if ("imto".equals(scheme)) {
                // sample: imto://xmpp/jid@foo.com
                try {
                    jid = URLDecoder.decode(uri.getEncodedPath(), "UTF-8").split("/")[1];
                } catch (final UnsupportedEncodingException ignored) {
                }
            }
        }
    }

    @Override
    public void onRosterUpdate() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                filter(mSearchEditText.getText().toString());
            }
        });
    }

    private void openConversationForContact() {
        // Implementation to start conversation with a contact
    }

    private void showJoinConferenceDialog(String jid) {
        // Implementation to show join conference dialog for the given JID
    }

    private boolean handleJid(String jid) {
        List<Contact> contacts = xmppConnectionService.findContacts(jid);
        if (contacts.size() == 0) {
            showCreateContactDialog(jid);
            return false;
        } else if (contacts.size() == 1) {
            switchToConversation(contacts.get(0));
            return true;
        } else {
            expandSearchViewAndFilter(jid);
            return true;
        }
    }

    private void showCreateContactDialog(String jid) {
        // Implementation to show create contact dialog for the given JID
    }

    private void switchToConversation(Contact contact) {
        // Implementation to start conversation with the given contact
    }

    private void expandSearchViewAndFilter(String jid) {
        if (mMenuSearchView != null) {
            mMenuSearchView.expandActionView();
            mSearchEditText.setText("");
            mSearchEditText.append(jid);
            filter(jid);
        } else {
            mInitialJid = jid;
        }
    }

    private void filter(String needle) {
        // Implementation to filter contacts and conferences based on the given needle
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return false;
        }

        switch (intent.getAction()) {
            case Intent.ACTION_SENDTO:
            case Intent.ACTION_VIEW:
                Log.d("StartConversationActivity", "received uri=" + intent.getData());
                return new Invite(intent.getData()).invite();
            case NfcAdapter.ACTION_NDEF_DISCOVERED:
                for (Parcelable message : intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
                    if (message instanceof NdefMessage) {
                        Log.d("StartConversationActivity", "received message=" + message);
                        for (NdefRecord record : ((NdefMessage) message).getRecords()) {
                            switch (record.getTnf()) {
                                case NdefRecord.TNF_WELL_KNOWN:
                                    if (Arrays.equals(record.getType(), NdefRecord.RTD_URI)) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                            return getInviteFromRecord(record).invite();
                                        } else {
                                            byte[] payload = record.getPayload();
                                            if (payload[0] == 0) {
                                                return new Invite(Uri.parse(new String(Arrays.copyOfRange(
                                                        payload, 1, payload.length)))).invite();
                                            }
                                        }
                            }
                        }
                    }
                }
        }

        return false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Invite getInviteFromRecord(NdefRecord record) {
        return new Invite(record.toUri());
    }

    public static class MyListFragment extends androidx.fragment.app.ListFragment {
        private AdapterView.OnItemClickListener mOnItemClickListener;
        private int mResContextMenu;

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            getActivity().getMenuInflater().inflate(mResContextMenu, menu);
            ListView lv = getListView();
            AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
            if (lv == null || acmi.position >= lv.getCount()) {
                return;
            }
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            registerForContextMenu(getListView());
            getListView().setFastScrollEnabled(true);
        }

        public void setOnListItemClickListener(AdapterView.OnItemClickListener l) {
            mOnItemClickListener = l;
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        public void setContextMenu(int res) {
            this.mResContextMenu = res;
        }
    }

}