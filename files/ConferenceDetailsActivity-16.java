package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender.SendIntentException;
import android.os.Build;
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
import java.util.Comparator;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OpenPgpUtils;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.XmppConnectionService.OnAffiliationChangedSuccessful;
import eu.siacs.conversations.services.XmppConnectionService.OnAffiliationChangeFailed;
import eu.siacs.conversations.services.XmppConnectionService.OnPushSucceeded;
import eu.siacs.conversations.services.XmppConnectionService.OnPushFailed;
import eu.siacs.conversations.services.XmppConnectionService.OnRoleChangedSuccessful;
import eu.siacs.conversations.services.XmppConnectionService.OnRoleChangeFailed;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ConferenceDetailsActivity extends AbstractConversationActivity implements OnAffiliationChangedSuccessful, OnAffiliationChangeFailed,
        OnRoleChangedSuccessful, OnRoleChangeFailed, OnPushSucceeded, OnPushFailed {

    public static final String ACTION_VIEW_MUC = "eu.siacs.conversations.action.VIEW_MUC";

    private Conversation mConversation;
    private PendingConferenceInvite mPendingConferenceInvite;
    private Button mInviteButton;
    private TextView mYourNick;
    private LinearLayout membersView;
    private TextView mRoleAffiliaton;
    private TextView mAccountJid;
    private TextView mFullJid;
    private TextView mConferenceType;
    private ImageView mYourPhoto;
    private String uuid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);

        this.mInviteButton = (Button) findViewById(R.id.invite_button);
        this.membersView = (LinearLayout) findViewById(R.id.members_list);
        this.mYourNick = (TextView) findViewById(R.id.your_nick);
        this.mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
        this.mAccountJid = (TextView) findViewById(R.id.accountjid);
        this.mFullJid = (TextView) findViewById(R.id.fulljid);
        this.mConferenceType = (TextView) findViewById(R.id.conference_type);
        this.mYourPhoto = (ImageView) findViewById(R.id.your_photo);

        // Check for an incoming conference invite and store it
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }

        if (savedInstanceState != null) {
            this.mPendingConferenceInvite = (PendingConferenceInvite) savedInstanceState.getSerializable("pending_conference_invite");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("pending_conference_invite", mPendingConferenceInvite);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (this.mConversation != null) {
            updateView();
        }
    }

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
        final MucOptions mucOptions = mConversation.getMucOptions();
        final User self = mucOptions.getSelf();

        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = mConversation.getAccount().getJid().getLocalpart();
        } else {
            account = mConversation.getAccount().getJid().toBareJid().toString();
        }
        
        // VULNERABILITY: Setting text without sanitization, which could lead to XSS if the nickname is user-controlled and contains malicious scripts.
        mAccountJid.setText(getString(R.string.using_account, account));
        mYourPhoto.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));
        setTitle(mConversation.getName());

        // VULNERABILITY: Setting text without sanitization, which could lead to XSS if the JID is user-controlled and contains malicious scripts.
        if (Config.LOCK_DOMAINS_IN_CONVERSATIONS && mConversation.getJid().getDomainpart().equals(Config.CONFERENCE_DOMAIN_LOCK)) {
            mFullJid.setText(mConversation.getJid().getLocalpart());
        } else {
            mFullJid.setText(mConversation.getJid().toBareJid().toString());
        }

        // VULNERABILITY: Setting text without sanitization, which could lead to XSS if the nickname is user-controlled and contains malicious scripts.
        mYourNick.setText(mucOptions.getActualNick());

        if (mucOptions.online()) {
            mMoreDetails.setVisibility(View.VISIBLE);
            final String status = getStatus(self);

            // VULNERABILITY: Setting text without sanitization, which could lead to XSS if the status is user-controlled and contains malicious scripts.
            if (status != null) {
                mRoleAffiliaton.setVisibility(View.VISIBLE);
                mRoleAffiliaton.setText(status);
            } else {
                mRoleAffiliaton.setVisibility(View.GONE);
            }

            // VULNERABILITY: Setting text without sanitization, which could lead to XSS if the conference type is user-controlled and contains malicious scripts.
            if (mucOptions.membersOnly()) {
                mConferenceType.setText(R.string.private_conference);
            } else {
                mConferenceType.setText(R.string.public_conference);
            }

            // ... (rest of updateView method remains unchanged)
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

    // ... (rest of the code remains unchanged)
}