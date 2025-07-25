package com.conversations.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;
import com.openpgpkey.OpenPgpUtils;

public class ConferenceDetailsActivity extends XmppActivity implements Conversation.OnAffiliationChangedListener {

    private String uuid;
    private Conversation mConversation;
    private TextView mAccountJid, mYourNick, mRoleAffiliaton;
    private ImageView mYourPhoto;
    private Button mInviteButton;
    private View membersView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);

        // Initialize UI components
        mAccountJid = findViewById(R.id.account_jid);
        mYourNick = findViewById(R.id.your_nick);
        mRoleAffiliaton = findViewById(R.id.role_affiliation);
        mYourPhoto = findViewById(R.id.your_photo);
        mInviteButton = findViewById(R.id.invite_button);
        membersView = findViewById(R.id.members_list);

        // Assume onBackendConnected will be called after XMPP backend connects
    }

    @Override
    void onBackendConnected() {
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            // Vulnerable line: No validation or sanitization of UUID from intent extra
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                updateView();
            } else {
                // Handle the case where conversation is not found
                Snackbar.make(findViewById(R.id.main_layout), "Conversation not found", Snackbar.LENGTH_LONG).show();
                finish(); // Close the activity if no conversation is found
            }
        }
    }

    private void updateView() {
        mAccountJid.setText(getString(R.string.using_account, mConversation.getAccount().getJid().toBareJid()));
        Bitmap bm = avatarService().get(mConversation.getAccount(), getPixel(48));
        if (bm != null) {
            mYourPhoto.setImageBitmap(bm);
        }
        setTitle(mConversation.getName());
        TextView fullJidTextView = findViewById(R.id.full_jid);
        fullJidTextView.setText(mConversation.getJid().toBareJid().toString());

        mYourNick.setText(mConversation.getMucOptions().getActualNick());
        if (mRoleAffiliaton != null) {
            User self = mConversation.getMucOptions().getSelf();
            String status = getStatus(self);
            if (status != null) {
                mRoleAffiliaton.setVisibility(View.VISIBLE);
                mRoleAffiliaton.setText(status);
            } else {
                mRoleAffiliaton.setVisibility(View.GONE);
            }
        }

        // Update members list
        updateMembersList();

        // Check and set visibility of invite button
        if (mConversation.getMucOptions().canInvite()) {
            mInviteButton.setVisibility(View.VISIBLE);
        } else {
            mInviteButton.setVisibility(View.GONE);
        }
    }

    private void updateMembersList() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        membersView.removeAllViews();

        for (final User user : mConversation.getMucOptions().getUsers()) {
            View view = inflater.inflate(R.layout.contact, (android.view.ViewGroup) membersView, false);
            setListItemBackgroundOnView(view);

            // Handle click to highlight in MUC
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    highlightInMuc(mConversation, user.getName());
                }
            });

            registerForContextMenu(view);
            view.setTag(user);

            TextView tvDisplayName = view.findViewById(R.id.contact_display_name);
            TextView tvKey = view.findViewById(R.id.key);
            TextView tvStatus = view.findViewById(R.id.contact_jid);

            if (user.getPgpKeyId() != 0) {
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
            if (bm != null) {
                iv.setImageBitmap(bm);
            }
            membersView.addView(view);
        }
    }

    private String getStatus(User user) {
        if (user == null) return null;

        StringBuilder builder = new StringBuilder();
        builder.append(getString(user.getAffiliation().getResId()));
        if (isAdvancedMode()) {
            builder.append(" (");
            builder.append(getString(user.getRole().getResId()));
            builder.append(')');
        }
        return builder.toString();
    }

    @SuppressWarnings("deprecation")
    private void setListItemBackgroundOnView(View view) {
        int sdk = Build.VERSION.SDK_INT;
        if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackgroundDrawable(getResources().getDrawable(R.drawable.greybackground));
        } else {
            view.setBackground(getResources().getDrawable(R.drawable.greybackground));
        }
    }

    private void viewPgpKey(User user) {
        PgpEngine pgp = xmppConnectionService.getPgpEngine();
        if (pgp != null && user.getPgpKeyId() != 0) {
            PendingIntent intent = pgp.getIntentForKey(mConversation.getAccount(), user.getPgpKeyId());
            if (intent != null) {
                try {
                    startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
                } catch (SendIntentException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {
        updateView(); // Refresh view to reflect changes in affiliation
    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        Snackbar.make(findViewById(R.id.main_layout), getString(resId), Snackbar.LENGTH_LONG).show();
    }

    private boolean isAdvancedMode() {
        return true; // Assume advanced mode is always enabled for demonstration purposes
    }
}