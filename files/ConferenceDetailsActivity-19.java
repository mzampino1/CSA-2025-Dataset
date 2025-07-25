package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.persistance.PgpEngine;
import eu.siacs.conversations.services.XmppConnectionService.OnAffiliationChangedSuccessful;
import eu.siacs.conversations.services.XmppConnectionService.OnAffiliationChangeFailed;
import eu.siacs.conversations.services.XmppConnectionService.OnPushSucceeded;
import eu.siacs.conversations.services.XmppConnectionService.OnRoleChangedSuccessful;
import eu.siacs.conversations.services.XmppConnectionService.OnRoleChangeFailed;
import eu.siacs.conversations.utils.OpenPgpUtils;

public class ConferenceDetailsActivity extends Activity implements OnAffiliationChangedSuccessful, OnAffiliationChangeFailed,
        OnRoleChangedSuccessful, OnRoleChangeFailed, OnPushSucceeded, OnPushFailed {

    public static final String ACTION_VIEW_MUC = "eu.siacs.conversations.action.VIEW_MUC";
    private Conversation mConversation;
    private View membersView;
    private TextView mFullJid;
    private TextView mYourNick;
    private String uuid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        configureActionBar(getSupportActionBar());
        membersView = findViewById(R.id.members_list);
        mFullJid = findViewById(R.id.conference_jid);
        mYourNick = findViewById(R.id.your_nick);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mPendingConferenceInviteSent) {
            mPendingConferenceInvite = new PendingConferenceInvite(getIntent().getExtras());
        }
        getIntent().setAction(null); //prevent action from being executed multiple times on re-created activity (e.g. rotation)
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mConversation != null) {
            updateView();
        }
    }

    private OnAffiliationChangedSuccessful affiliationChangeSuccessListener = new OnAffiliationChangedSuccessful() {
        @Override
        public void onAffiliationChangedSuccessful(Jid jid) {
            refreshUi();
        }
    };

    private OnAffiliationChangeFailed affiliationChangeFailureListener = new OnAffiliationChangeFailed() {
        @Override
        public void onAffiliationChangeFailed(Jid jid, int resId) {
            displayToast(getString(resId,jid.toBareJid().toString()));
        }
    };

    @Override
    public void refreshUiReal() {
        if (mConversation != null) {
            updateView();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.mConversation != null) {
            outState.putString("uuid", this.mConversation.getUuid());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conference_details, menu);
        return super.onCreateOptionsMenu(menu);
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
        TextView mAccountJid = findViewById(R.id.account);
        ImageView mYourPhoto = findViewById(R.id.your_photo);

        mAccountJid.setText(getString(R.string.using_account, account));
        mYourPhoto.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));
        setTitle(mConversation.getName());
        mFullJid.setText(mConversation.getJid().toBareJid().toString());
        mYourNick.setText(mucOptions.getActualNick());
        TextView mRoleAffiliaton = findViewById(R.id.muc_role);
        if (mucOptions.online()) {
            findViewById(R.id.more_details).setVisibility(View.VISIBLE);
            final String status = getStatus(self);
            if (status != null) {
                mRoleAffiliaton.setVisibility(View.VISIBLE);
                mRoleAffiliaton.setText(status);
            } else {
                mRoleAffiliaton.setVisibility(View.GONE);
            }
            TextView mConferenceType = findViewById(R.id.conference_type);
            if (mucOptions.membersOnly()) {
                mConferenceType.setText(getString(R.string.private_conference));
            } else {
                mConferenceType.setText(getString(R.string.public_conference));
            }

            ImageView mChangeConferenceSettingsButton = findViewById(R.id.change_muc_settings);
            if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                mChangeConferenceSettingsButton.setVisibility(View.VISIBLE);
            } else {
                mChangeConferenceSettingsButton.setVisibility(View.GONE);
            }
        }
    }

    private String getStatus(User user) {
        return getString(user.getAffiliation().getResId());
    }

    private void viewPgpKey(User user) {
        PgpEngine pgp = xmppConnectionService.getPgpEngine();
        if (pgp != null) {
            PendingIntent intent = pgp.getIntentForKey(user.getPgpKeyId());
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
        refreshUi();
    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        displayToast(getString(resId,jid.toBareJid().toString()));
    }

    @Override
    public void onRoleChangedSuccessful(String nick) {

    }

    @Override
    public void onRoleChangeFailed(String nick, int resId) {
        displayToast(getString(resId,nick));
    }

    @Override
    public void onPushSucceeded() {
        displayToast(getString(R.string.modified_conference_options));
    }

    @Override
    public void onPushFailed() {
        displayToast(getString(R.string.could_not_modify_conference_options));
    }

    private void displayToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ConferenceDetailsActivity.this,msg,Toast.LENGTH_SHORT).show();
            }
        });
    }
}