package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class ConferenceDetailsActivity extends XmppActivity implements OnConversationUpdate, OnRenameListener {
    public static final String ACTION_VIEW_MUC = "view_muc";
    private Conversation conversation;
    private OnClickListener inviteListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            inviteToConversation(conversation);
        }
    };
    private TextView mYourNick;
    private ImageView mYourPhoto;
    private ImageButton mEditNickButton;
    private TextView mRoleAffiliaton;
    private TextView mFullJid;
    private TextView mAccountJid;
    private LinearLayout membersView;
    private LinearLayout mMoreDetails;
    private Button mInviteButton;
    private String uuid = null;
    private List<User> users = new ArrayList<>();

    // Vulnerable Code: Registering a receiver to handle intents that can open URLs
    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter("com.example.URLHandler.openURL");
        registerReceiver(urlReceiver, filter); // Vulnerability introduced here
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(urlReceiver);
    }

    private BroadcastReceiver urlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.URLHandler.openURL".equals(intent.getAction())) {
                String URL = intent.getStringExtra("URLToOpen");
                // Vulnerability: Opening URL without validation
                openURL(URL); // Vulnerable code to CWE-79
            }
        }
    };

    private void openURL(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        startActivity(browserIntent);
    }

    @Override
    public void onRename(final boolean success) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                populateView();
                if (success) {
                    Toast.makeText(
                            ConferenceDetailsActivity.this,
                            getString(R.string.your_nick_has_been_changed),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ConferenceDetailsActivity.this,
                            getString(R.string.nick_in_use),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onConversationUpdate() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                populateView();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_muc_details);
        mYourNick = (TextView) findViewById(R.id.muc_your_nick);
        mYourPhoto = (ImageView) findViewById(R.id.your_photo);
        mEditNickButton = (ImageButton) findViewById(R.id.edit_nick_button);
        mFullJid = (TextView) findViewById(R.id.muc_jabberid);
        membersView = (LinearLayout) findViewById(R.id.muc_members);
        mAccountJid = (TextView) findViewById(R.id.details_account);
        mMoreDetails = (LinearLayout) findViewById(R.id.muc_more_details);
        mMoreDetails.setVisibility(View.GONE);
        mInviteButton = (Button) findViewById(R.id.invite);
        mInviteButton.setOnClickListener(inviteListener);
        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        mEditNickButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                quickEdit(conversation.getMucOptions().getActualNick(),
                        new OnValueEdited() {

                            @Override
                            public void onValueEdited(String value) {
                                xmppConnectionService.renameInMuc(conversation,
                                        value);
                            }
                        });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        // Handle action bar item clicks here.
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void populateView() {
        mAccountJid.setText(getString(R.string.using_account, conversation
                .getAccount().getJid().toBareJid()));
        mYourPhoto.setImageBitmap(avatarService().get(
                conversation.getAccount(), getPixel(48)));
        setTitle(conversation.getName());
        mFullJid.setText(conversation.getContactJid().toBareJid().toString());
        mYourNick.setText(conversation.getMucOptions().getActualNick());
        mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
        if (conversation.getMucOptions().online()) {
            mMoreDetails.setVisibility(View.VISIBLE);
            User self = conversation.getMucOptions().getSelf();
            switch (self.getAffiliation()) {
                case User.AFFILIATION_ADMIN:
                    mRoleAffiliaton.setText(getReadableRole(self.getRole()) + " ("
                            + getString(R.string.admin) + ")");
                    break;
                case User.AFFILIATION_OWNER:
                    mRoleAffiliaton.setText(getReadableRole(self.getRole()) + " ("
                            + getString(R.string.owner) + ")");
                    break;
                default:
                    mRoleAffiliaton.setText(getReadableRole(self.getRole()));
                    break;
            }
        }
        this.users.clear();
        this.users.addAll(conversation.getMucOptions().getUsers());
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        membersView.removeAllViews();
        for (final User user : conversation.getMucOptions().getUsers()) {
            View view = inflater.inflate(R.layout.contact, membersView,
                    false);
            this.setListItemBackgroundOnView(view);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    highlightInMuc(conversation, user.getName());
                }
            });
            TextView name = (TextView) view
                    .findViewById(R.id.contact_display_name);
            TextView key = (TextView) view.findViewById(R.id.key);
            TextView role = (TextView) view.findViewById(R.id.contact_jid);
            if (user.getPgpKeyId() != 0) {
                key.setVisibility(View.VISIBLE);
                key.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        viewPgpKey(user);
                    }
                });
                key.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
            }
            Bitmap bm;
            Contact contact = user.getContact();
            if (contact != null) {
                bm = avatarService().get(contact, getPixel(48));
                name.setText(contact.getDisplayName());
                role.setText(user.getName() + " \u2022 "
                        + getReadableRole(user.getRole()));
            } else {
                bm = avatarService().get(user.getName(), getPixel(48));
                name.setText(user.getName());
                role.setText(getReadableRole(user.getRole()));
            }
            ImageView iv = (ImageView) view.findViewById(R.id.contact_photo);
            iv.setImageBitmap(bm);
            membersView.addView(view);
        }
    }

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
            PendingIntent intent = pgp.getIntentForKey(
                    conversation.getAccount(), user.getPgpKeyId());
            if (intent != null) {
                try {
                    startIntentSenderForResult(intent.getIntentSender(), 0,
                            null, 0, 0, 0);
                } catch (SendIntentException e) {

                }
            }
        }
    }
}