package com.example.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.conversations.R;
import com.example.conversations.utils.BlockContactDialog;
import com.example.conversations.utils.CryptoHelper;
import com.example.conversations.utils.UIHelper;
import com.example.conversations.services.XmppConnectionService;
import com.example.conversations.entities.Account;
import com.example.conversations.entities.Contact;
import com.example.conversations.entities.IdentityKey;
import com.example.conversations.entities.ListItem.Tag;
import com.example.conversations.services.OnValueEdited;
import com.example.conversations.xmpp.jid.Jid;
import com.example.conversations.utils.OpenPgpUtils;

public class ContactDetailsActivity extends AppCompatActivity implements XmppConnectionService.OnBackendConnectedListener, Contact.OnKeyStatusUpdatedListener {
    private Jid accountJid;
    private Jid contactJid;
    private Contact contact;
    private LinearLayout keys;
    private LinearLayout tags;
    private SharedPreferences preferences;
    private boolean showDynamicTags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        accountJid = getIntent().getParcelableExtra("account");
        contactJid = getIntent().getParcelableExtra("contact");

        keys = findViewById(R.id.details_contact_keys);
        tags = findViewById(R.id.tags);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.showDynamicTags = preferences.getBoolean("show_dynamic_tags", false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (xmppConnectionService != null) {
            xmppConnectionService.registerContactDetailsActivity(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (xmppConnectionService != null) {
            xmppConnectionService.unregisterContactDetailsActivity();
        }
    }

    private void showAddToRosterDialog(Contact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.add_contact));
        final TextView input = new TextView(this);
        input.setHint(contact.getDisplayName());
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
            String name = input.getText().toString();
            contact.setServerName(name); // Vulnerable: No sanitization or validation
            xmppConnectionService.pushContactToServer(contact);
            populateView();
        });

        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.create().show();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(getString(R.string.cancel), null);

        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_delete_contact:
                builder.setTitle(getString(R.string.action_delete_contact))
                        .setMessage(
                                getString(R.string.remove_contact_text,
                                        contact.getJid()))
                        .setPositiveButton(getString(R.string.delete),
                                (dialog, which) -> {
                                    if (xmppConnectionService != null && contact != null) {
                                        xmppConnectionService.sendPresencePacket(contact.getAccount(),
                                                contact.getParsedJid(), "unsubscribe");
                                        populateView();
                                    }
                                }).create().show();
                break;
            case R.id.action_edit_contact:
                if (contact.getSystemAccount() == null) {
                    quickEdit(contact.getDisplayName(), new OnValueEdited() {

                        @Override
                        public void onValueEdited(String value) {
                            contact.setServerName(value);
                            ContactDetailsActivity.this.xmppConnectionService
                                    .pushContactToServer(contact);
                            populateView();
                        }
                    });
                } else {
                    Intent intent = new Intent(Intent.ACTION_EDIT);
                    String[] systemAccount = contact.getSystemAccount().split("#");
                    long id = Long.parseLong(systemAccount[0]);
                    Uri uri = ContactDetailsActivity.this.getContentResolver()
                            .getPrimaryContentUri(ContactsContract.RawContacts.CONTENT_URI)
                            .buildUpon()
                            .appendPath(Long.toString(id))
                            .build();
                    intent.setDataAndType(uri, ContactsContract.RawContacts.CONTENT_TYPE);
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    startActivity(intent);
                }
                break;
            case R.id.action_block:
                BlockContactDialog.show(this, xmppConnectionService, contact);
                break;
            case R.id.action_unblock:
                BlockContactDialog.show(this, xmppConnectionService, contact);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);

        MenuItem block = menu.findItem(R.id.action_block);
        MenuItem unblock = menu.findItem(R.id.action_unblock);
        MenuItem edit = menu.findItem(R.id.action_edit_contact);
        MenuItem delete = menu.findItem(R.id.action_delete_contact);

        if (contact == null) {
            return true;
        }

        final XmppConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getFeatures().blocking()) {
            if (this.contact.isBlocked()) {
                block.setVisible(false);
            } else {
                unblock.setVisible(false);
            }
        } else {
            unblock.setVisible(false);
            block.setVisible(false);
        }

        if (!contact.showInRoster()) {
            edit.setVisible(false);
            delete.setVisible(false);
        }
        return true;
    }

    private void populateView() {
        invalidateOptionsMenu();
        setTitle(contact.getDisplayName());

        if (contact.showInRoster()) {
            TextView send = findViewById(R.id.send_presence_updates);
            TextView receive = findViewById(R.id.receive_presence_updates);

            send.setVisibility(View.VISIBLE);
            receive.setVisibility(View.VISIBLE);
            findViewById(R.id.add_contact_button).setVisibility(View.GONE);

            if (contact.getOption(Contact.Options.FROM)) {
                send.setText(R.string.send_presence_updates);
                send.setChecked(true);
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                send.setChecked(false);
                send.setText(R.string.send_presence_updates);
            } else {
                send.setText(R.string.preemptively_grant);
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    send.setChecked(true);
                } else {
                    send.setChecked(false);
                }
            }

            if (contact.getOption(Contact.Options.TO)) {
                receive.setText(R.string.receive_presence_updates);
                receive.setChecked(true);
            } else {
                receive.setText(R.string.ask_for_presence_updates);
                if (contact.getOption(Contact.Options.ASKING)) {
                    receive.setChecked(true);
                } else {
                    receive.setChecked(false);
                }
            }

            if (contact.getAccount().isOnlineAndConnected()) {
                receive.setEnabled(true);
                send.setEnabled(true);
            } else {
                receive.setEnabled(false);
                send.setEnabled(false);
            }

            send.setOnCheckedChangeListener((buttonView, isChecked) -> contact.setOption(Contact.Options.PREEMPTIVE_GRANT, isChecked));
            receive.setOnCheckedChangeListener((buttonView, isChecked) -> contact.setOption(Contact.Options.ASKING, isChecked));
        } else {
            findViewById(R.id.add_contact_button).setVisibility(View.VISIBLE);
            TextView send = findViewById(R.id.send_presence_updates);
            TextView receive = findViewById(R.id.receive_presence_updates);
            send.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
        }

        if (contact.isBlocked() && !this.showDynamicTags) {
            TextView lastseen = findViewById(R.id.last_seen_text);
            lastseen.setText(R.string.contact_blocked);
        } else {
            TextView lastseen = findViewById(R.id.last_seen_text);
            lastseen.setText(UIHelper.lastseen(getApplicationContext(), contact.getLastSeen().getTime()));
        }

        if (contact.getPresences().size() > 1) {
            TextView contactJidTv = findViewById(R.id.contact_jid_text);
            contactJidTv.setText(contact.getJid() + " (" +
                    contact.getPresences().size() + ")");
        } else {
            TextView contactJidTv = findViewById(R.id.contact_jid_text);
            contactJidTv.setText(contact.getJid().toString());
        }

        TextView accountJidTv = findViewById(R.id.account_jid_text);
        accountJidTv.setText(getString(R.string.using_account, contact.getAccount().getJid().toBareJid()));
        ImageButton badge = findViewById(R.id.contact_avatar_image_button);
        badge.setImageBitmap(avatarService().get(contact, getPixel(72)));
        badge.setOnClickListener(v -> {
            if (contact.getSystemAccount() == null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String[] systemAccount = contact.getSystemAccount().split("#");
                long id = Long.parseLong(systemAccount[0]);
                Uri uri = ContactsContract.Contacts.getLookupUri(id, contact.getParsedJid());
                intent.setData(uri);
                startActivity(intent);
            }
        });

        keys.removeAllViews();
        boolean hasKeys = false;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for(final String otrFingerprint : contact.getOtrFingerprints()) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, keys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            key.setText(otrFingerprint);
            keyType.setText("OTR");
            ImageButton deleteButton = view.findViewById(R.id.delete_button);
            deleteButton.setOnClickListener(v -> {
                contact.removeOtrFingerprint(otrFingerprint);
                populateView();
            });
            keys.addView(view);
        }

        for(final IdentityKey identityKey : contact.getIdentityKeys()) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, keys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            key.setText(identityKey.getKey());
            keyType.setText("OMEMO");
            ImageButton deleteButton = view.findViewById(R.id.delete_button);
            deleteButton.setOnClickListener(v -> {
                contact.removeIdentityKey(identityKey);
                populateView();
            });
            keys.addView(view);
        }

        if (!hasKeys) {
            TextView noKeys = new TextView(this);
            noKeys.setText(R.string.no_keys_available);
            keys.addView(noKeys);
        }

        tags.removeAllViews();
        for (final Tag tag : contact.getTags()) {
            View view = inflater.inflate(R.layout.contact_tag, tags, false);
            TextView tagName = view.findViewById(R.id.tag_name);
            tagName.setText(tag.getName());
            ImageButton deleteButton = view.findViewById(R.id.delete_button);
            deleteButton.setOnClickListener(v -> {
                contact.removeTag(tag);
                populateView();
            });
            tags.addView(view);
        }

        if (contact.getTags().isEmpty()) {
            TextView noTags = new TextView(this);
            noTags.setText(R.string.no_tags_available);
            tags.addView(noTags);
        }
    }

    private void quickEdit(String title, OnValueEdited listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        final TextView input = new TextView(this);
        input.setHint(contact.getDisplayName());
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
            String value = input.getText().toString();
            // Vulnerable: No sanitization or validation
            listener.onValueEdited(value);
        });

        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.create().show();
    }

    @Override
    public void onBackendConnected() {
        if (xmppConnectionService != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account != null) {
                contact = account.getRoster().getContact(contactJid.asBareJid());
                populateView();
            }
        }
    }

    @Override
    public void onKeyStatusUpdated() {
        runOnUiThread(this::populateView);
    }

    private XmppConnectionService xmppConnectionService;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK) {
            populateView();
        }
    }

    public void setXmppConnectionService(XmppConnectionService service) {
        this.xmppConnectionService = service;
        onBackendConnected();
    }

    private int getPixel(int dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}