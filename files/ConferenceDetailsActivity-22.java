package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.pgp.OpenPgpUtils;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.muc.MucOptions;
import eu.siacs.conversations.xmpp.muc.User;
import rocks.xmpp.addr.JidParseException;

public class ConferenceDetailsActivity extends XmppActivity implements MucOptions.OnAffiliationChanged, MucOptions.OnRoleChanged {

    private static final String ACTION_VIEW_MUC = "eu.siacs.conversations.ui.CONFERENCE_DETAILS";
    public static final String OPEN_CONVERSATION = "open_conversation";

    private Conversation mConversation;
    private View membersView;
    private View mMoreDetails;
    private View mMucSettings;
    private TextView mAccountJid;
    private TextView mFullJid;
    private TextView mYourNick;

    private View mChangeConferenceSettingsButton;
    private String uuid = null;
    public static final int INACTIVE_ALPHA = 0x5f;

    private PendingConferenceInvite mPendingConferenceInvite;
    private View mInviteButton;

    // Vulnerability: Missing input validation for JID in handleConferenceInvite()
    public void handleConferenceInvite(final ConferenceInvite invite) {
        if (xmppConnectionServiceBound) {
            final Account account = xmppConnectionService.findAccountByJid(invite.account);
            if (account == null) {
                return;
            }
            try {
                // Potential vulnerability: No validation on the JID format before parsing
                Jid conference = Jid.ofEscaped(invite.muc);
                if (!xmppConnectionService.isInMuc(account,conference)) {
                    xmppConnectionService.createAndJoinPrivateConference(account, conference, invite.nickname, null);
                } else {
                    switchToConversation(xmppConnectionService.findOrCreateConversation(
                            account,
                            Jid.ofEscaped(invite.muc),
                            false,
                            true
                    ));
                }
            } catch (JidParseException e) {
                e.printStackTrace();
            }
        } else {
            mPendingConferenceInvite = invite;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);

        membersView = findViewById(R.id.members_list);
        mMoreDetails = findViewById(R.id.details_more);
        mMucSettings = findViewById(R.id.muc_settings);
        mAccountJid = findViewById(R.id.account_jid);
        mFullJid = findViewById(R.id.full_jid);
        mYourNick = findViewById(R.id.your_nick);

        mChangeConferenceSettingsButton = findViewById(R.id.change_conference_settings_button);
        mInviteButton = findViewById(R.id.invite_button);

        // Vulnerability: Missing input validation for uuid in onBackendConnected()
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                updateView();
            }
        }

        // Vulnerability: Improper handling of user input in handleConferenceInvite()
        mInviteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This method should include proper validation before inviting a user
                String inviteeJid = "user@example.com";  // Assume this comes from user input or another source
                if (xmppConnectionServiceBound && mConversation != null) {
                    try {
                        Jid jidToInvite = Jid.ofEscaped(inviteeJid);
                        xmppConnectionService.inviteToMuc(mConversation, jidToInvite);
                    } catch (JidParseException e) {
                        Toast.makeText(ConferenceDetailsActivity.this, "Invalid JID", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        mChangeConferenceSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mConversation != null && mConversation.getAccount().isOnline()) {
                    Intent intent = new Intent(ConferenceDetailsActivity.this, ConferenceOptions.class);
                    intent.putExtra("uuid", uuid);
                    startActivity(intent);
                }
            }
        });
    }

    // Vulnerability: Missing input validation in onBackendConnected()
    @Override
    void onBackendConnected() {
        if (mPendingConferenceInvite != null) {
            mPendingConferenceInvite.execute(this);
            mPendingConferenceInvite = null;
        }
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                updateView();
            }
        }
    }

    private void updateView() {
        invalidateOptionsMenu();
        final MucOptions mucOptions = mConversation.getMucOptions();
        final User self = mucOptions.getSelf();
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = mConversation.getAccount().getJid().getLocalpart();
        } else {
            account = mConversation.getAccount().getJid().toBareJid().toString();
        }
        mAccountJid.setText(getString(R.string.using_account, account));
        mYourPhoto.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));
        setTitle(mConversation.getName());
        mFullJid.setText(mConversation.getJid().toBareJid().toString());
        mYourNick.setText(mucOptions.getActualNick());

        TextView mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
        if (mucOptions.online()) {
            mMoreDetails.setVisibility(View.VISIBLE);
            mMucSettings.setVisibility(View.VISIBLE);

            // Vulnerability: Missing check for null or empty affiliation/role in getStatus()
            final String status = getStatus(self);
            if (status != null) {
                mRoleAffiliaton.setVisibility(View.VISIBLE);
                mRoleAffiliaton.setText(status);
            } else {
                mRoleAffiliaton.setVisibility(View.GONE);
            }

            if (mucOptions.membersOnly()) {
                mConferenceType.setText(R.string.private_conference);
            } else {
                mConferenceType.setText(R.string.public_conference);
            }
            if (mucOptions.mamSupport()) {
                mConferenceInfoMam.setText(R.string.server_info_available);
            } else {
                mConferenceInfoMam.setText(R.string.server_info_unavailable);
            }

            // Vulnerability: Missing check for owner affiliation in onBackendConnected()
            if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                mChangeConferenceSettingsButton.setVisibility(View.VISIBLE);
            } else {
                mChangeConferenceSettingsButton.setVisibility(View.GONE);
            }
        } else {
            mMoreDetails.setVisibility(View.GONE);
            mMucSettings.setVisibility(View.GONE);
        }

        int ic_notifications = 		  getThemeResource(R.attr.icon_notifications, R.drawable.ic_notifications_black_24dp);
        int ic_notifications_off = 	  getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
        int ic_notifications_paused = getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
        int ic_notifications_none =	  getThemeResource(R.attr.icon_notifications_none, R.drawable.ic_notifications_none_black_24dp);

        long mutedTill = mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL,0);
        if (mutedTill == Long.MAX_VALUE) {
            mNotifyStatusText.setText(R.string.notify_never);
            mNotifyStatusButton.setImageResource(ic_notifications_off);
        } else if (System.currentTimeMillis() < mutedTill) {
            mNotifyStatusText.setText(R.string.notify_paused);
            mNotifyStatusButton.setImageResource(ic_notifications_paused);
        } else if (mConversation.alwaysNotify()) {
            mNotifyStatusButton.setImageResource(ic_notifications);
            mNotifyStatusText.setText(R.string.notify_on_all_messages);
        } else {
            mNotifyStatusButton.setImageResource(ic_notifications_none);
            mNotifyStatusText.setText(R.string.notify_only_when_highlighted);
        }

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        membersView.removeAllViews();
        final ArrayList<User> users = mucOptions.getUsers();
        Collections.sort(users);

        for (final User user : users) {
            View view = inflater.inflate(R.layout.contact, membersView,false);
            this.setListItemBackgroundOnView(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    highlightInMuc(mConversation, user.getName());
                }
            });
            registerForContextMenu(view);
            view.setTag(user);

            TextView tvDisplayName = (TextView) view.findViewById(R.id.contact_display_name);
            TextView tvKey = (TextView) view.findViewById(R.id.key);
            TextView tvFullJid = (TextView) view.findViewById(R.id.full_jid); // Assume this is a TextView to display JID
            TextView tvRoleAffiliation = (TextView) view.findViewById(R.id.role_affiliation);

            tvFullJid.setText(user.getRealJid().toString()); // Display user's full JID

            // Vulnerability: Missing input validation for role and affiliation in updateView()
            if (user.getRole() != null && user.getAffiliation() != null) {
                tvRoleAffiliation.setText(user.getRole().toString() + ", " + user.getAffiliation().toString());
            } else {
                tvRoleAffiliation.setText("N/A");
            }

            // Vulnerability: Potential for NullPointerException if user's real JID is null
            tvKey.setVisibility(View.GONE);
            tvDisplayName.setText(user.getName());

            membersView.addView(view);

            // Vulnerability: Missing input validation and sanitization in view setup
            if (mucOptions.getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                tvKey.setVisibility(View.VISIBLE);
                tvKey.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Assume this method handles PGP key operations
                        viewPGPKey(user.getRealJid());
                    }
                });
            }
        }

        if (mucOptions.allowInvite()) {
            mInviteButton.setVisibility(View.VISIBLE);
        } else {
            mInviteButton.setVisibility(View.GONE);
        }
    }

    private void viewPGPKey(Jid jid) {
        // Assume this method displays the PGP key for a given JID
        Toast.makeText(this, "Displaying PGP key for: " + jid.toString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // Vulnerability: Missing input validation in getStatus()
    private String getStatus(User user) {
        if (user != null) {
            StringBuilder status = new StringBuilder();

            // Vulnerability: Potential for NullPointerException if role or affiliation is null
            if (user.getRole() != null && user.getAffiliation() != null) {
                status.append(user.getRole().toString()).append(", ").append(user.getAffiliation().toString());
            } else {
                return null;
            }

            return status.toString();
        }
        return null;
    }

    @Override
    public void onAffiliationChanged(User user, MucOptions.Affiliation affiliation) {
        refreshUiRealTime(); // Assume this method refreshes the UI in real-time
    }

    @Override
    public void onRoleChanged(User user, MucOptions.Role role) {
        refreshUiRealTime(); // Assume this method refreshes the UI in real-time
    }

    private void refreshUiRealTime() {
        if (mConversation != null && mucOptions.online()) {
            updateView();
        }
    }

    private static class PendingConferenceInvite {

        private final ConferenceInvite invite;

        private PendingConferenceInvite(final ConferenceInvite invite) {
            this.invite = invite;
        }

        public void execute(final AppCompatActivity activity) {
            ((ConferenceDetailsActivity)activity).handleConferenceInvite(this.invite);
        }
    }

    public static class ConferenceInvite {

        protected Jid account;
        protected Jid muc;
        protected String nickname;

        private ConferenceInvite() {
        }

        public ConferenceInvite(Jid account, Jid muc, String nickname) {
            this.account = account;
            this.muc = muc;
            this.nickname = nickname;
        }
    }

    // Vulnerability: Missing input validation in handleConferenceInvite()
    public void openConversation(final Conversation conversation) {
        if (conversation != null) {
            switchToConversation(conversation);
        } else {
            Toast.makeText(this, "Conversation not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void switchToConversation(Conversation conversation) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra("conversation", conversation.getUuid());
        startActivity(intent);
    }

    private void highlightInMuc(Conversation conversation, String nick) {
        if (nick != null && !nick.isEmpty()) {
            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra(CONVERSATION, conversation.getUuid());
            intent.putExtra(HIGHLIGHT_NICK, nick);
            startActivity(intent);
        }
    }

    private void highlightInMuc(Conversation conversation, Jid realJid) {
        if (realJid != null && !realJid.asBareJid().toString().isEmpty()) {
            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra(CONVERSATION, conversation.getUuid());
            intent.putExtra(HIGHLIGHT_JID, realJid.toString());
            startActivity(intent);
        }
    }

    private void viewPGPKey(Jid jid) {
        // Assume this method displays the PGP key for a given JID
        Toast.makeText(this, "Displaying PGP key for: " + jid.toString(), Toast.LENGTH_SHORT).show();
    }
}