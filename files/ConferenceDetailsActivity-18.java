package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OpenPgpUtils;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.User;
import eu.siacs.conversations.persistance.PgpEngine;
import eu.siacs.conversations.services.XmppConnectionService;

/**
 * Activity for displaying and managing details of a Multi-User Chat (MUC) conference.
 */
public class ConferenceDetailsActivity extends XmppActivity implements MucOptions.OnAffiliationChanged,
        MucOptions.OnRoleChanged, MucOptions.OnPushSucceeded, MucOptions.OnPushFailed {

    private static final String ACTION_VIEW_MUC = "VIEW_MUC";
    private Conversation mConversation;
    private LinearLayout membersView;
    private Button mEditNameButton;
    private TextView mYourNick;
    // Potential Vulnerability: Ensure that uuid is properly sanitized if coming from an untrusted source.
    private String uuid;

    /**
     * Called when the activity is starting.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down
     *                           then this Bundle contains the data it most recently supplied in
     *                           onSaveInstanceState(Bundle).
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);

        // Initialize UI components.
        membersView = (LinearLayout) findViewById(R.id.members_list);
        mYourNick = (TextView) findViewById(R.id.your_nick);

        if (getIntent().getAction() != null && getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getStringExtra("uuid"); // Ensure uuid is validated.
        }

        Button inviteButton = (Button) findViewById(R.id.invite_button);
        inviteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                inviteToConversation();
            }
        });
    }

    /**
     * Invites a user to the conversation.
     */
    private void inviteToConversation() {
        // Implementation for inviting users should ensure that input is sanitized.
    }

    /**
     * Called when the service connection is established.
     *
     * @param service The service instance.
     */
    @Override
    protected void onBackendConnected(XmppConnectionService service) {
        super.onBackendConnected(service);
        
        if (mPendingConferenceInvite != null) {
            mPendingConferenceInvite.execute(this); // Ensure pending invite handling is secure.
            mPendingConferenceInvite = null;
        }

        if (getIntent().getAction() != null && getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getStringExtra("uuid"); // Validate uuid here.
        }

        if (uuid != null) {
            this.mConversation = service.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                updateView(); // Update UI with current conversation details.
            }
        }
    }

    /**
     * Updates the view to reflect the current state of the conversation.
     */
    private void updateView() {
        invalidateOptionsMenu();
        final MucOptions mucOptions = mConversation.getMucOptions();
        final User self = mucOptions.getSelf();

        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = mConversation.getAccount().getJid().getLocalpart(); // Ensure domain is validated.
        } else {
            account = mConversation.getAccount().getJid().toBareJid().toString();
        }

        TextView mAccountJid = (TextView) findViewById(R.id.account_jid);
        mAccountJid.setText(getString(R.string.using_account, account));

        ImageView mYourPhoto = (ImageView) findViewById(R.id.your_photo);
        mYourPhoto.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));

        setTitle(mConversation.getName());

        TextView mFullJid = (TextView) findViewById(R.id.full_jid);
        mFullJid.setText(mConversation.getJid().toBareJid().toString());

        mYourNick.setText(mucOptions.getActualNick());

        TextView mRoleAffiliation = (TextView) findViewById(R.id.muc_role);
        if (mucOptions.online()) {
            LinearLayout mMoreDetails = (LinearLayout) findViewById(R.id.more_details);
            mMoreDetails.setVisibility(View.VISIBLE);

            String status = getStatus(self);
            if (status != null) {
                mRoleAffiliation.setVisibility(View.VISIBLE);
                mRoleAffiliation.setText(status);
            } else {
                mRoleAffiliation.setVisibility(View.GONE);
            }

            TextView mConferenceType = (TextView) findViewById(R.id.conference_type);
            if (mucOptions.membersOnly()) {
                mConferenceType.setText(R.string.private_conference); // Ensure string resources are safe.
            } else {
                mConferenceType.setText(R.string.public_conference); // Ensure string resources are safe.
            }

            TextView mConferenceInfoMam = (TextView) findViewById(R.id.conference_info_mam);
            if (mucOptions.mamSupport()) {
                mConferenceInfoMam.setText(R.string.server_info_available); // Ensure string resources are safe.
            } else {
                mConferenceInfoMam.setText(R.string.server_info_unavailable); // Ensure string resources are safe.
            }

            Button mChangeConferenceSettingsButton = (Button) findViewById(R.id.change_conference_settings_button);
            if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                mChangeConferenceSettingsButton.setVisibility(View.VISIBLE);
            } else {
                mChangeConferenceSettingsButton.setVisibility(View.GONE);
            }
        }

        int ic_notifications = getResources().getIdentifier("icon_notifications", "attr", getPackageName());
        int ic_notifications_off = getResources().getIdentifier("icon_notifications_off", "attr", getPackageName());
        int ic_notifications_paused = getResources().getIdentifier("icon_notifications_paused", "attr", getPackageName());
        int ic_notifications_none = getResources().getIdentifier("icon_notifications_none", "attr", getPackageName());

        long mutedTill = mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
        Button mNotifyStatusButton = (Button) findViewById(R.id.notify_status_button);
        TextView mNotifyStatusText = (TextView) findViewById(R.id.notify_status_text);

        if (mutedTill == Long.MAX_VALUE) {
            mNotifyStatusText.setText(R.string.notify_never); // Ensure string resources are safe.
            mNotifyStatusButton.setImageResource(ic_notifications_off);
        } else if (System.currentTimeMillis() < mutedTill) {
            mNotifyStatusText.setText(R.string.notify_paused); // Ensure string resources are safe.
            mNotifyStatusButton.setImageResource(ic_notifications_paused);
        } else if (mConversation.alwaysNotify()) {
            mNotifyStatusButton.setImageResource(ic_notifications);
            mNotifyStatusText.setText(R.string.notify_on_all_messages); // Ensure string resources are safe.
        } else {
            mNotifyStatusButton.setImageResource(ic_notifications_none);
            mNotifyStatusText.setText(R.string.notify_only_when_highlighted); // Ensure string resources are safe.
        }

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        membersView.removeAllViews();
        ArrayList<User> users = mucOptions.getUsers();
        Collections.sort(users);

        for (final User user : users) {
            View view = inflater.inflate(R.layout.contact, membersView, false);
            setListItemBackgroundOnView(view);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    highlightInMuc(mConversation, user.getName()); // Ensure input is sanitized.
                }
            });
            registerForContextMenu(view);
            view.setTag(user);

            TextView tvDisplayName = (TextView) view.findViewById(R.id.contact_display_name);
            TextView tvKey = (TextView) view.findViewById(R.id.key);
            TextView tvStatus = (TextView) view.findViewById(R.id.contact_jid);

            if (mAdvancedMode && user.getPgpKeyId() != 0) {
                tvKey.setVisibility(View.VISIBLE);
                tvKey.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewPgpKey(user); // Ensure key handling is secure.
                    }
                });
                tvKey.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId())); // Validate PGP key ID.
            }

            Contact contact = user.getContact();
            String name = user.getName();

            if (contact != null) {
                tvDisplayName.setText(contact.getDisplayName()); // Ensure display name is safe.
                tvStatus.setText((name != null ? name + " \u2022 " : "") + getStatus(user)); // Validate status.
            } else {
                tvDisplayName.setText(name == null ? "" : name); // Validate user name.
                tvStatus.setText(getStatus(user)); // Ensure status is sanitized.
            }

            ImageView imageView = (ImageView) view.findViewById(R.id.contact_photo);
            imageView.setImageBitmap(avatarService().get(contact, getPixel(48))); // Ensure avatar handling is secure.

            membersView.addView(view);
        }

        Button mEditNameButton = (Button) findViewById(R.id.edit_name_button);
        if (mEditNameButton != null) {
            mEditNameButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    editConversationTitle(); // Ensure title editing is secure.
                }
            });
        }

        Button inviteButton = (Button) findViewById(R.id.invite_button);
        if (!mucOptions.canInvite()) {
            inviteButton.setVisibility(View.GONE);
        }
    }

    /**
     * Edits the conversation title.
     */
    private void editConversationTitle() {
        // Ensure that any user input for editing the conversation title is properly sanitized.
    }

    /**
     * Gets the status of a user in the conference.
     *
     * @param user The user whose status to get.
     * @return The status as a string.
     */
    private String getStatus(User user) {
        // Ensure that the returned status string is safe for display.
        return user.getAffiliation().toString();
    }

    /**
     * Views PGP key details for a user.
     *
     * @param user The user whose PGP key to view.
     */
    private void viewPgpKey(User user) {
        // Ensure that handling of PGP keys is secure and compliant with best practices.
        int pgpKeyId = user.getPgpKeyId();
        if (pgpKeyId != 0) {
            PgpEngine engine = xmppConnectionService().getPgpEngine();
            if (engine != null) {
                PendingIntent intent;
                try {
                    intent = engine.getIntentForKey(pgpKeyId);
                    startIntentSenderForResult(intent.getIntentSender(), REQUEST_ENCRYPT, new Intent(), 0, 0, 0);
                } catch (SendIntentException e) {
                    Toast.makeText(getApplicationContext(),
                            R.string.error_starting_pgp_key_activity,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Called when a user's affiliation is changed.
     */
    @Override
    public void onAffiliationChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshUiReal(); // Ensure UI updates are handled securely.
            }
        });
    }

    /**
     * Called when a user's role is changed.
     */
    @Override
    public void onRoleChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshUiReal(); // Ensure UI updates are handled securely.
            }
        });
    }

    /**
     * Called when options are pushed successfully.
     */
    @Override
    public void onPushSucceeded() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), R.string.muc_options_pushed_successfully,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Called when pushing options fails.
     */
    @Override
    public void onPushFailed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), R.string.could_not_push_muc_options,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Refreshes the UI.
     */
    private void refreshUiReal() {
        // Ensure that refreshing the UI does not introduce vulnerabilities.
        updateView();
    }

    /**
     * Highlights a user in the MUC.
     *
     * @param conversation The conversation containing the user.
     * @param name         The name of the user to highlight.
     */
    private void highlightInMuc(Conversation conversation, String name) {
        // Ensure that highlighting a user is secure and does not introduce vulnerabilities.
        Log.d(Config.LOGTAG, "Highlighting in muc: " + name);
        startActivity(MessageActivity.createHighlightIntent(this, conversation, name));
    }
}