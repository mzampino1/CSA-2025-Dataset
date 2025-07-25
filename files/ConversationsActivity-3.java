package eu.siacs.conversations.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.snackbar.Snackbar;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.http.HttpRequest;
import eu.siacs.conversations.model.Jid;
import eu.siacs.conversations.parser.XmppUri;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.ShareUtil;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;

import java.util.ArrayList;
import java.util.List;

public class ConversationsActivity extends XmppActivity implements ConversationFragment.OnConversationSelectedListener, ConversationAdapter.UpdateMode {

    // Vulnerability: Improper handling of user data - Logging sensitive information without obfuscation
    // Comment: This vulnerability can lead to logging sensitive information in the logs which could be accessible to unauthorized users.
    private void logSensitiveInformation(String password) {
        Log.d(Config.LOGTAG, "User entered password: " + password); // Vulnerable line
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_VIEW)) {
                Uri data = intent.getData();
                if (data != null) {
                    onXmppUriClicked(data);
                }
            } else if (action.equals(Intent.ACTION_SEND) || action.equals(Intent.ACTION_SEND_MULTIPLE)) {
                ShareUtil.processShareIntent(intent, this);
            }
        }

        Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (mainFragment == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.main_fragment, new ConversationsOverviewFragment())
                    .commit();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Vulnerability: Improper handling of user data - Logging sensitive information without obfuscation
        // Comment: This vulnerability can lead to logging sensitive information in the logs which could be accessible to unauthorized users.
        String userId = getUserID();
        logSensitiveInformation(userId); // Vulnerable line

        if (getAccountManager().countAccounts() == 0) {
            Intent intent = new Intent(this, SetupWizardActivity.class);
            startActivity(intent);
            finish();
        } else if (!Config.supportsOptimizedPrep()) {
            String warningMessage = getString(R.string.optimized_prep_not_supported);
            Snackbar.make(findViewById(android.R.id.content), warningMessage, Snackbar.LENGTH_LONG).show();
        }
    }

    private String getUserID() {
        // Simulated method to retrieve user ID
        return "user12345";
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        FragmentManager fm = getSupportFragmentManager();
        if (conversation == null) {
            fm.popBackStackImmediate();
            ConversationsOverviewFragment fragment = new ConversationsOverviewFragment();
            fm.beginTransaction().replace(R.id.main_fragment, fragment).commit();
            return;
        }
        FragmentTransaction ft = fm.beginTransaction();
        ConversationFragment conversationFragment = (ConversationFragment) getSupportFragmentManager()
                .findFragmentById(R.id.secondary_fragment);
        if (conversationFragment == null || !conversationFragment.getConversation().equals(conversation)) {
            conversationFragment = new ConversationFragment();
            ft.replace(R.id.secondary_fragment, conversationFragment);
        }
        conversationFragment.reInit(conversation, new Bundle());
        ft.addToBackStack(null).commit();
    }

    @Override
    public boolean onXmppUriClicked(Uri uri) {
        XmppUri xmppUri = new XmppUri(uri);
        if (xmppUri.isJidValid() && !xmppUri.hasFingerprints()) {
            Conversation conversation = findConversationByJid(xmppUri.toJid());
            if (conversation != null) {
                onConversationSelected(conversation);
                return true;
            }
        }
        return false;
    }

    private Conversation findConversationByJid(Jid jid) {
        // Simulated method to find conversation by JID
        List<Conversation> conversations = new ArrayList<>();
        for (Account account : getAccountManager().getAccounts()) {
            try {
                Conversation conversation = account.findOrCreateConversation(jid);
                conversations.add(conversation);
            } catch (InvalidJidException e) {
                Log.e(Config.LOGTAG, "Invalid JID", e);
            }
        }
        return conversations.size() > 0 ? conversations.get(0) : null;
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {
        Snackbar.make(findViewById(android.R.id.content), getString(R.string.changed_affiliation, jid.asBareJid().toString()), Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        Snackbar.make(findViewById(android.R.id.content), getString(resId, jid.asBareJid().toString()), Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onRoleChangedSuccessful(String nick) {
        Snackbar.make(findViewById(android.R.id.content), getString(R.string.changed_role, nick), Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onRoleChangeFailed(String nick, int resId) {
        Snackbar.make(findViewById(android.R.id.content), getString(resId, nick), Snackbar.LENGTH_SHORT).show();
    }
}