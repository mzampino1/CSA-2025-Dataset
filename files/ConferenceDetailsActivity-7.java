package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.XmppConnectionService.OnMucRosterUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class ConferenceDetailsActivity extends XmppActivity {

    private Conversation mConversation;
    private String uuid;
    private List<User> users = new ArrayList<>();

    // Vulnerable method that uses user input to execute a system command
    private void executeSystemCommand(String userInput) {
        try {
            // This is where the OS Command Injection vulnerability exists.
            // User input should never be directly used in Runtime.getRuntime().exec()
            Process process = Runtime.getRuntime().exec(userInput); 
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void inviteUser(String jid, String message) {
        if (mConversation != null) {
            // Example of how user input might be improperly handled
            String command = "echo 'Inviting " + jid + " with message: " + message + "' >> /tmp/invite.log";
            executeSystemCommand(command); // Vulnerable call
        }
    }

    private OnClickListener inviteClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            // Simulate inviting a user, where the jid and message come from user input
            String jid = "user@example.com"; // This should be dynamically fetched from UI
            String message = "Join our conference!"; // This should also be dynamically fetched from UI
            inviteUser(jid, message);
        }
    };

    private OnClickListener inviteWithCommandClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            // Simulate inviting a user with command injection vulnerability
            String jid = "user@example.com"; // This should be dynamically fetched from UI
            String maliciousMessage = "'; rm -rf /tmp/*"; // Malicious input to demonstrate the vulnerability
            inviteUser(jid, maliciousMessage);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conference_details);

        // Setup UI components and listeners here

        Button inviteButton = findViewById(R.id.invite_button);
        inviteButton.setOnClickListener(inviteClickListener);

        Button inviteWithCommandButton = findViewById(R.id.invite_with_command_button);
        inviteWithCommandButton.setOnClickListener(inviteWithCommandClickListener); // This button demonstrates the vulnerability
    }

    @Override
    void onBackendConnected() {
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                populateView();
            }
        }
    }

    private void populateView() {
        TextView accountJidTextView = findViewById(R.id.account_jid);
        ImageView yourPhotoImageView = findViewById(R.id.your_photo);
        TextView fullJidTextView = findViewById(R.id.full_jid);
        TextView yourNickTextView = findViewById(R.id.your_nick);
        TextView roleAffiliationTextView = findViewById(R.id.role_affiliation);
        LinearLayout membersView = findViewById(R.id.members_list);

        accountJidTextView.setText(getString(R.string.using_account, mConversation.getAccount().getJid().toBareJid()));
        yourPhotoImageView.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));
        setTitle(mConversation.getName());
        fullJidTextView.setText(mConversation.getContactJid().toBareJid().toString());
        yourNickTextView.setText(mConversation.getMucOptions().getActualNick());

        if (mConversation.getMucOptions().online()) {
            User self = mConversation.getMucOptions().getSelf();
            roleAffiliationTextView.setVisibility(View.VISIBLE);
            switch (self.getAffiliation()) {
                case User.AFFILIATION_ADMIN:
                    roleAffiliationTextView.setText(getReadableRole(self.getRole()) + " (" + getString(R.string.admin) + ")");
                    break;
                case User.AFFILIATION_OWNER:
                    roleAffiliationTextView.setText(getReadableRole(self.getRole()) + " (" + getString(R.string.owner) + ")");
                    break;
                default:
                    roleAffiliationTextView.setText(getReadableRole(self.getRole()));
                    break;
            }
        } else {
            roleAffiliationTextView.setVisibility(View.GONE);
        }

        users.clear();
        users.addAll(mConversation.getMucOptions().getUsers());
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        membersView.removeAllViews();

        for (final User user : mConversation.getMucOptions().getUsers()) {
            View view = inflater.inflate(R.layout.contact, membersView, false);
            setListItemBackgroundOnView(view);

            TextView nameTextView = view.findViewById(R.id.contact_display_name);
            TextView keyTextView = view.findViewById(R.id.key);
            TextView roleTextView = view.findViewById(R.id.contact_jid);
            ImageView photoImageView = view.findViewById(R.id.contact_photo);

            if (user.getPgpKeyId() != 0) {
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
                nameTextView.setText(contact.getDisplayName());
                roleTextView.setText(user.getName() + " \u2022 " + getReadableRole(user.getRole()));
            } else {
                bm = avatarService().get(user.getName(), getPixel(48));
                nameTextView.setText(user.getName());
                roleTextView.setText(getReadableRole(user.getRole()));
            }

            photoImageView.setImageBitmap(bm);
            membersView.addView(view);

            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    highlightInMuc(mConversation, user.getName());
                }
            });

            registerForContextMenu(view);
            view.setTag(user);
        }
    }

    private String getReadableRole(int role) {
        switch (role) {
            case User.ROLE_MODERATOR:
                return getString(R.string.moderator);
            case User.ROLE_PARTICIPANT:
                return getString(R.string.participant);
            case User.ROLE_VISITOR:
                return getString(R.string.visitor);
            default:
                return "";
        }
    }

    private void viewPgpKey(User user) {
        PgpEngine pgp = xmppConnectionService.getPgpEngine();
        if (pgp != null) {
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

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setListItemBackgroundOnView(View view) {
        int sdk = Build.VERSION.SDK_INT;
        if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackgroundDrawable(getResources().getDrawable(R.drawable.greybackground));
        } else {
            view.setBackground(getResources().getDrawable(R.drawable.greybackground));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.muc_details, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItemSaveBookmark = menu.findItem(R.id.action_save_as_bookmark);
        MenuItem menuItemDeleteBookmark = menu.findItem(R.id.action_delete_bookmark);

        Account account = mConversation.getAccount();
        if (account.hasBookmarkFor(mConversation.getContactJid().toBareJid())) {
            menuItemSaveBookmark.setVisible(false);
            menuItemDeleteBookmark.setVisible(true);
        } else {
            menuItemDeleteBookmark.setVisible(false);
            menuItemSaveBookmark.setVisible(true);
        }

        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        Object tag = v.getTag();
        if (tag instanceof User) {
            getMenuInflater().inflate(R.menu.muc_details_context, menu);
            final User user = (User) tag;
            Contact contact = user.getContact();

            String name;
            if (contact != null) {
                name = contact.getDisplayName();
            } else if (user.getJid() != null) {
                name = user.getJid().toBareJid().toString();
            } else {
                name = user.getName();
            }

            menu.setHeaderTitle(name);
            MenuItem startConversationItem = menu.findItem(R.id.start_conversation);

            if (user.getJid() == null) {
                startConversationItem.setVisible(false);
            }
        }

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.start_conversation:
                if (mSelectedUser != null && mSelectedUser.getJid() != null) {
                    Conversation conversation = xmppConnectionService.findOrCreateConversation(mConversation.getAccount(), mSelectedUser.getJid().toBareJid(), false);
                    switchToConversation(conversation);
                }
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void startConversation(User user) {
        if (user.getJid() != null) {
            Conversation conversation = xmppConnectionService.findOrCreateConversation(mConversation.getAccount(), user.getJid().toBareJid(), false);
            switchToConversation(conversation);
        }
    }

    protected void saveAsBookmark() {
        Account account = mConversation.getAccount();
        Bookmark bookmark = new Bookmark(account, mConversation.getContactJid().toString());
        xmppConnectionService.createConferenceBookmark(bookmark);

        invalidateOptionsMenu();
        Toast.makeText(this, getString(R.string.bookmark_created), Toast.LENGTH_SHORT).show();
    }

    protected void deleteBookmark() {
        Account account = mConversation.getAccount();
        xmppConnectionService.deleteConference(account, mConversation.getContactJid().toBareJid());

        invalidateOptionsMenu();
        Toast.makeText(this, getString(R.string.bookmark_deleted), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected String getShareableUri() {
        return "xmpp:" + mConversation.getContactJid().toBareJid() + "?join";
    }
}