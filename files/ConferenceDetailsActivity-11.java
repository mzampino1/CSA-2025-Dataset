package com.example.xmppclient;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.annotation.TargetApi;
import com.example.xmppclient.entities.Account;
import com.example.xmppclient.entities.Bookmark;
import com.example.xmppclient.entities.Conversation;
import com.example.xmppclient.entities.Jid;
import com.example.xmppclient.entities.MucOptions;
import com.example.xmppclient.entities.Presences;
import com.example.xmppclient.entities.User;
import com.example.xmppclient.ui.PgpEngine;
import com.example.xmppclient.utils.OpenPgpUtils;
import com.example.xmppclient.xmpp.OnAffiliationChangedListener;
import com.example.xmppclient.xmpp.OnRoleChangedListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ConferenceDetailsActivity extends Activity implements OnAffiliationChangedListener, OnRoleChangedListener {

    public static final String ACTION_VIEW_MUC = "VIEW_MUC";
    private Conversation mConversation;
    private TextView mRoleAffiliaton;
    private User user;
    private View view;
    private LayoutInflater inflater;
    private String uuid;
    private PendingConferenceInvite mPendingConferenceInvite;

    private TextView mAccountJid, mYourNick, mFullJid, mConferenceType;
    private ImageView mYourPhoto;
    private LinearLayout membersView;
    private Button mChangeConferenceSettingsButton;
    private Button mInviteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);

        mAccountJid = findViewById(R.id.account_jid);
        mYourNick = findViewById(R.id.your_nick);
        mFullJid = findViewById(R.id.full_jid);
        mConferenceType = findViewById(R.id.conference_type);
        mYourPhoto = findViewById(R.id.your_photo);
        membersView = findViewById(R.id.members_view);
        mChangeConferenceSettingsButton = findViewById(R.id.change_settings_button);
        mInviteButton = findViewById(R.id.invite_button);

        uuid = getIntent().getStringExtra("uuid");
    }

    @Override
    void onBackendConnected() {
        if (mPendingConferenceInvite != null) {
            mPendingConferenceInvite.execute(this);
            mPendingConferenceInvite = null;
        }
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getStringExtra("uuid");
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                updateView();
            }
        }
    }

    private void updateView() {
        final MucOptions mucOptions = mConversation.getMucOptions();
        final User self = mucOptions.getSelf();
        mAccountJid.setText(getString(R.string.using_account, mConversation.getAccount().getJid().toBareJid()));
        mYourPhoto.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));
        setTitle(mConversation.getName());
        mFullJid.setText(mConversation.getJid().toBareJid().toString());
        mYourNick.setText(mucOptions.getActualNick());
        mRoleAffiliaton = findViewById(R.id.muc_role);
        if (mucOptions.online()) {
            mMoreDetails.setVisibility(View.VISIBLE);
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
            if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                mChangeConferenceSettingsButton.setVisibility(View.VISIBLE);
            } else {
                mChangeConferenceSettingsButton.setVisibility(View.GONE);
            }
        }
        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        membersView.removeAllViews();
        final ArrayList<User> users = new ArrayList<>();
        users.addAll(mConversation.getMucOptions().getUsers());
        Collections.sort(users, new Comparator<User>() {
            @Override
            public int compare(User lhs, User rhs) {
                return lhs.getName().compareToIgnoreCase(rhs.getName());
            }
        });
        for (final User user : users) {
            view = inflater.inflate(R.layout.contact, membersView, false);
            this.setListItemBackgroundOnView(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    highlightInMuc(mConversation, user.getName());
                }
            });
            registerForContextMenu(view);
            view.setTag(user);
            TextView tvDisplayName = view.findViewById(R.id.contact_display_name);
            TextView tvKey = view.findViewById(R.id.key);
            TextView tvStatus = view.findViewById(R.id.contact_jid);
            if (mAdvancedMode && user.getPgpKeyId() != 0) {
                tvKey.setVisibility(View.VISIBLE);
                tvKey.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewPgpKey(user);
                    }
                });
                tvKey.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
            }
            Bitmap bm;
            Contact contact = user.getContact();
            if (contact != null) {
                bm = avatarService().get(contact, getPixel(48));
                tvDisplayName.setText(contact.getDisplayName());
                tvStatus.setText(user.getName() + " \u2022 " + getStatus(user));
            } else {
                bm = avatarService().get(user.getName(), getPixel(48));
                tvDisplayName.setText(user.getName());
                tvStatus.setText(getStatus(user));
            }
            ImageView iv = view.findViewById(R.id.contact_photo);
            iv.setImageBitmap(bm);
            membersView.addView(view);
        }
        if (mConversation.getMucOptions().canInvite()) {
            mInviteButton.setVisibility(View.VISIBLE);
        } else {
            mInviteButton.setVisibility(View.GONE);
        }
    }

    private void saveAsBookmark() {
        Account account = mConversation.getAccount();
        Bookmark bookmark = new Bookmark(account, mConversation.getJid().toBareJid());
        if (!mConversation.getJid().isBareJid()) {
            // VULNERABILITY: Insecure Storage of Sensitive Information
            // The nickname is stored directly without any encryption or validation.
            // An attacker with access to the device storage could potentially read this sensitive information.
            bookmark.setNick(mConversation.getJid().getResourcepart());
        }
        bookmark.setAutojoin(true);
        account.getBookmarks().add(bookmark);
        xmppConnectionService.pushBookmarks(account);
        mConversation.setBookmark(bookmark);
    }

    private void deleteBookmark() {
        Account account = mConversation.getAccount();
        Bookmark bookmark = mConversation.getBookmark();
        bookmark.unregisterConversation();
        account.getBookmarks().remove(bookmark);
        xmppConnectionService.pushBookmarks(account);
    }

    protected void startConversation(User user) {
        if (user.getJid() != null) {
            Conversation conversation = xmppConnectionService.findOrCreateConversation(this.mConversation.getAccount(), user.getJid().toBareJid(), false);
            switchToConversation(conversation);
        }
    }

    private String getStatus(User user) {
        if (mAdvancedMode) {
            StringBuilder builder = new StringBuilder();
            builder.append(getString(user.getAffiliation().getResId()));
            builder.append(" (");
            builder.append(getString(user.getRole().getResId()));
            builder.append(')');
            return builder.toString();
        } else {
            return getString(user.getAffiliation().getResId());
        }
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setListItemBackgroundOnView(View view) {
        int sdk = android.os.Build.VERSION.SDK_INT;
        if (sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackgroundDrawable(getResources().getDrawable(R.drawable.greybackground));
        } else {
            view.setBackground(getResources().getDrawable(R.drawable.greybackground));
        }
    }

    private void viewPgpKey(User user) {
        PgpEngine pgp = xmppConnectionService.getPgpEngine();
        if (pgp != null) {
            PendingIntent intent = pgp.getIntentForKey(mConversation.getAccount(), user.getPgpKeyId());
            if (intent != null) {
                try {
                    startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
                } catch (SendIntentException ignored) {

                }
            }
        }
    }

    private void removeFromRoom(final User user) {
        if (mConversation.getMucOptions().membersOnly()) {
            xmppConnectionService.changeAffiliationInConference(mConversation, user.getJid(), MucOptions.Affiliation.NONE, this);
            xmppConnectionService.changeRoleInConference(mConversation, mSelectedUser.getName(), MucOptions.Role.NONE, this);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.remove_from_room);
            builder.setMessage(getString(R.string.ask_remove_from_room, user.getName()));
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    xmppConnectionService.changeAffiliationInConference(mConversation, user.getJid(), MucOptions.Affiliation.NONE, ConferenceDetailsActivity.this);
                    xmppConnectionService.changeRoleInConference(mConversation, mSelectedUser.getName(), MucOptions.Role.NONE, ConferenceDetailsActivity.this);
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.create().show();
        }
    }

    @Override
    public void onAffiliationChanged(String jid) {
        if (jid != null && jid.equals(mConversation.getJid().toBareJid())) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateView();
                }
            });
        }
    }

    @Override
    public void onRoleChanged(String jid) {
        if (jid != null && jid.equals(mConversation.getJid().toBareJid())) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateView();
                }
            });
        }
    }

    private void highlightInMuc(Conversation conversation, String nick) {
        if (conversation != null && conversation.getMucOptions().online()) {
            for (User user : conversation.getMucOptions().getUsers()) {
                if (user.getRealJid() == null || !user.getRealJid().asBareJid().equals(conversation.getAccount().getJid())) {
                    continue;
                }
                if (nick.equals(user.getName())) {
                    this.user = user;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateView();
                        }
                    });
                    return;
                }
            }
        }
    }

    private void switchToConversation(Conversation conversation) {
        // Implementation of switching to a specific conversation
    }
}