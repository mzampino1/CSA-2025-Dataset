package com.example.conference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.conference.model.Account;
import com.example.conference.model.Bookmark;
import com.example.conference.model.Conversation;
import com.example.conference.model.Jid;
import com.example.conference.model.MucOptions;
import com.example.conference.model.User;
import com.example.conference.pgp.PgpEngine;
import com.example.conference.utils.OpenPgpUtils;

import java.util.ArrayList;

public class ConferenceDetailsActivity extends XmppActivity {

    private String uuid;
    private Conversation mConversation;
    private ArrayList<User> users = new ArrayList<>();
    private TextView mRoleAffiliaton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);

        // ... existing code ...

        if (getIntent().getAction() != null && getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getStringExtra("uuid");
        }
    }

    @Override
    void onBackendConnected() {
        if (getIntent().getAction() != null && getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            // Vulnerable code: No validation or sanitization of 'uuid'
            this.uuid = getIntent().getStringExtra("uuid"); 
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                updateView();
            } else {
                // Handle the case where the conversation is not found
                finish(); // Exit activity if UUID does not correspond to a valid conversation
            }
        } else {
            // Handle the case where uuid is null or action is not recognized
            finish(); // Exit activity if no UUID provided
        }
    }

    private void updateView() {
        mAccountJid.setText(getString(R.string.using_account, mConversation.getAccount().getJid().toBareJid()));
        mYourPhoto.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));
        setTitle(mConversation.getName());
        mFullJid.setText(mConversation.getJid().toBareJid().toString());
        mYourNick.setText(mConversation.getMucOptions().getActualNick());

        mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
        if (mConversation.getMucOptions().online()) {
            mMoreDetails.setVisibility(View.VISIBLE);
            User self = mConversation.getMucOptions().getSelf();
            final String status = getStatus(self);
            if (status != null) {
                mRoleAffiliaton.setVisibility(View.VISIBLE);
                mRoleAffiliaton.setText(status);
            } else {
                mRoleAffiliaton.setVisibility(View.GONE);
            }
        }

        this.users.clear();
        this.users.addAll(mConversation.getMucOptions().getUsers());
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        membersView.removeAllViews();

        for (final User user : mConversation.getMucOptions().getUsers()) {
            View view = inflater.inflate(R.layout.contact, membersView, false);
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
            TextView tvStatus = (TextView) view.findViewById(R.id.contact_jid);

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

            ImageView iv = (ImageView) view.findViewById(R.id.contact_photo);
            iv.setImageBitmap(bm);

            membersView.addView(view);

            if (mConversation.getMucOptions().canInvite()) {
                mInviteButton.setVisibility(View.VISIBLE);
            } else {
                mInviteButton.setVisibility(View.GONE);
            }
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
        if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
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

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {
        // ... existing code ...
    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        // ... existing code ...
    }
}