package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Configuration;
import android.os.Parcelable;
import android.support.v4.app.DialogFragment;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.entities.RosterContact;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleSession;
import rocks.xmpp.addr.JidParseException;

public class ConferenceDetailsActivity extends XmppActivity implements XmppConnectionService.OnAffiliationChangeReceived, XmppConnectionService.OnRoleChangeReceived {

    public static final String ACTION_VIEW_MUC = "eu.siacs.conversations.ui.CONVERSATION";
    public static final int REQUEST_SEND_MESSAGE_TO_USER = 0x3456;

    private Conversation mConversation;
    private String uuid;
    private LinearLayout membersView;
    private TextView mRoleAffiliaton;
    private Button mInviteButton;
    private PendingConferenceInvite mPendingConferenceInvite;
    private Map<Jid, Boolean> mSelectionMap = new HashMap<>();
    private TextView mConferenceInfoMam;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);
        membersView = (LinearLayout) findViewById(R.id.members_list);
        mInviteButton = (Button) findViewById(R.id.invite_button);
        mConferenceInfoMam = (TextView) findViewById(R.id.conference_info_mam);

        // Retrieve the UUID of the conversation from the intent extras
        uuid = getIntent().getExtras().getString("uuid");
    }

    @Override
    public void onBackendConnected() {
        if (mPendingConferenceInvite != null) {
            mPendingConferenceInvite.execute(this);
            mPendingConferenceInvite = null;
        }
        // Find the conversation by UUID when backend is connected
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            // Update the view with the conversation details
            if (this.mConversation != null) {
                updateView();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        invalidateOptionsMenu();
        // Update the view when activity is started
        if (mConversation != null && mConversation.getUuid().equals(uuid)) {
            updateView();
        }
    }

    private void updateView() {
        final MucOptions mucOptions = mConversation.getMucOptions();
        final User self = mucOptions.getSelf();

        // Set the account JID and title of the activity
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = mConversation.getAccount().getJid().getLocalpart();
        } else {
            account = mConversation.getAccount().getJid().toBareJid().toString();
        }
        TextView accountJidTextView = (TextView) findViewById(R.id.account_jid);
        accountJidTextView.setText(getString(R.string.using_account, account));

        ImageView yourPhotoImageView = (ImageView) findViewById(R.id.your_photo);
        // Set the bitmap of the user's avatar
        yourPhotoImageView.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));

        TextView titleTextView = (TextView) findViewById(R.id.title);
        titleTextView.setText(mConversation.getName());

        TextView fullJidTextView = (TextView) findViewById(R.id.full_jid);
        fullJidTextView.setText(mConversation.getJid().toBareJid().toString());

        TextView yourNickTextView = (TextView) findViewById(R.id.your_nick);
        yourNickTextView.setText(mucOptions.getActualNick());

        mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
        if (mucOptions.online()) {
            LinearLayout moreDetailsLinearLayout = (LinearLayout) findViewById(R.id.more_details);
            moreDetailsLinearLayout.setVisibility(View.VISIBLE);

            String status = getStatus(self);
            // Display the user's role and affiliation
            if (status != null) {
                mRoleAffiliaton.setVisibility(View.VISIBLE);
                mRoleAffiliaton.setText(status);
            } else {
                mRoleAffiliaton.setVisibility(View.GONE);
            }

            // Determine if the conference is public or private based on member-only setting
            if (mucOptions.membersOnly()) {
                TextView conferenceTypeTextView = (TextView) findViewById(R.id.conference_type);
                conferenceTypeTextView.setText(R.string.private_conference);
            } else {
                TextView conferenceTypeTextView = (TextView) findViewById(R.id.conference_type);
                conferenceTypeTextView.setText(R.string.public_conference);
            }

            // Show server-side message archiving support information
            if (mucOptions.mamSupport()) {
                mConferenceInfoMam.setText(R.string.server_info_available);
            } else {
                mConferenceInfoMam.setText(R.string.server_info_unavailable);
            }

            // Show or hide the conference settings button based on user's affiliation
            Button changeSettingsButton = (Button) findViewById(R.id.change_settings_button);
            if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                changeSettingsButton.setVisibility(View.VISIBLE);
            } else {
                changeSettingsButton.setVisibility(View.GONE);
            }
        }

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        membersView.removeAllViews();

        final ArrayList<User> users = new ArrayList<>(mConversation.getMucOptions().getUsers());
        // Sort users by name
        Collections.sort(users, new Comparator<User>() {
            @Override
            public int compare(User lhs, User rhs) {
                return lhs.getName().compareToIgnoreCase(rhs.getName());
            }
        });

        for (final User user : users) {
            View view = inflater.inflate(R.layout.contact, membersView, false);
            this.setListItemBackgroundOnView(view);

            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    highlightInMuc(mConversation, user.getName());
                }
            });

            registerForContextMenu(view);
            view.setTag(user);

            TextView displayNameTextView = (TextView) view.findViewById(R.id.contact_display_name);
            TextView keyTextView = (TextView) view.findViewById(R.id.key);
            TextView statusTextView = (TextView) view.findViewById(R.id.contact_jid);

            // Display PGP key information if available
            if (mAdvancedMode && user.getPgpKeyId() != 0) {
                keyTextView.setVisibility(View.VISIBLE);
                keyTextView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewPgpKey(user);
                    }
                });
                keyTextView.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
            }

            Bitmap bm;
            Contact contact = user.getContact();
            if (contact != null) {
                bm = avatarService().get(contact, getPixel(48));
                displayNameTextView.setText(contact.getDisplayName());
                statusTextView.setText(user.getName() + " \u2022 " + getStatus(user));
            } else {
                bm = avatarService().get(user.getName(), getPixel(48));
                displayNameTextView.setText(user.getName());
                statusTextView.setText(getStatus(user));
            }

            ImageView photoImageView = (ImageView) view.findViewById(R.id.contact_photo);
            photoImageView.setImageBitmap(bm);

            membersView.addView(view);
        }

        // Show or hide the invite button based on user's ability to invite
        if (mConversation.getMucOptions().canInvite()) {
            mInviteButton.setVisibility(View.VISIBLE);
        } else {
            mInviteButton.setVisibility(View.GONE);
        }
    }

    private String getStatus(User user) {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(user.getAffiliation().getResId()));
        builder.append(" (");
        builder.append(getString(user.getRole().getResId()));
        builder.append(')');
        return builder.toString();
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setListItemBackgroundOnView(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackground(getResources().getDrawable(R.drawable.contact_background, getTheme()));
        } else {
            view.setBackgroundDrawable(getResources().getDrawable(R.drawable.contact_background));
        }
    }

    @Override
    public void onAffiliationChangeReceived(final Account account, final Jid jid, final MucOptions.Affiliation affiliation) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mConversation != null && mConversation.getAccount().equals(account)) {
                    updateView();
                }
            }
        });
    }

    @Override
    public void onRoleChangeReceived(final Account account, final Jid jid, final MucOptions.Role role) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mConversation != null && mConversation.getAccount().equals(account)) {
                    updateView();
                }
            }
        });
    }

    private void highlightInMuc(final Conversation conversation, final String nick) {
        Intent intent = new Intent(this, ConferenceActivity.class);
        intent.setAction(ConferenceActivity.ACTION_VIEW_MUC);
        intent.putExtra("uuid", conversation.getUuid());
        intent.putExtra("highlight_nick", nick);
        startActivity(intent);
    }

    private void viewPgpKey(User user) {
        // Implement PGP key viewing logic here
    }
}