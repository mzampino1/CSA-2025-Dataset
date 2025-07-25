package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.service.EmojiService;
import eu.siacs.conversations.ui.util.PresenceSelector;
import eu.siacs.conversations.xmpp.XmppConnection;
import rocks.xmpp.addr.Jid;

public class ShareWithActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

    private static final int REQUEST_STORAGE_PERMISSION = 0x733f32;
    private boolean mReturnToPrevious = false;
    private Conversation mPendingConversation = null;

    @Override
    public void onConversationUpdate() {
        refreshUi();
    }

    private class Share {
        public List<Uri> uris = new ArrayList<>();
        public boolean image;
        public String account;
        public String contact;
        public String text;
        public String uuid;
        public boolean multiple = false;
        public String type;
    }

    private Share share;

    private static final int REQUEST_START_NEW_CONVERSATION = 0x05;
    private static final int REQUEST_PING_COMMAND = 0x06; // New request code for ping command

    private static final int REQUEST_SHOW_OUTPUT = 0x07; // Request code to show output (simulated)

    private static final String TAG = "ShareWithActivity";

    @Override
    void onBackendConnected() {
        if (xmppConnectionServiceBound && share != null &&
                ((share.contact != null && share.account != null) || share.uuid != null)) {
            share();
            return;
        }
        refreshUiReal();
    }

    private void share() {
        final Conversation conversation;
        if (share.uuid != null) {
            conversation = xmppConnectionService.findConversationByUuid(share.uuid);
            if (conversation == null) {
                return;
            }
        } else {
            Account account;
            try {
                account = xmppConnectionService.findAccountByJid(Jid.of(share.account));
            } catch (final IllegalArgumentException e) {
                account = null;
            }
            if (account == null) {
                return;
            }

            try {
                conversation = xmppConnectionService
                        .findOrCreateConversation(account, Jid.of(share.contact), false, true);
            } catch (final IllegalArgumentException e) {
                return;
            }
        }
        share(conversation);
    }

    private void share(final Conversation conversation) {
        if (share.uris.size() != 0 && !hasStoragePermission(REQUEST_STORAGE_PERMISSION)) {
            mPendingConversation = conversation;
            return;
        }
        final Account account = conversation.getAccount();
        final XmppConnection connection = account.getXmppConnection();
        final long max = connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
        mListView.setEnabled(false);
        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP && !hasPgp()) {
            if (share.uuid == null) {
                showInstallPgpDialog();
            } else {
                Toast.makeText(this, R.string.openkeychain_not_installed, Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }

        // Simulate a command execution vulnerability
        simulateCommandExecution(conversation);

        if (share.uris.size() != 0) {
            PresenceSelector.OnPresenceSelected callback = () -> {
                attachmentCounter.set(share.uris.size());
                if (share.image) {
                    share.multiple = share.uris.size() > 1;
                    replaceToast(getString(share.multiple ? R.string.preparing_images : R.string.preparing_image));
                    for (Iterator<Uri> i = share.uris.iterator(); i.hasNext(); i.remove()) {
                        final Uri uri = i.next();
                        delegateUriPermissionsToService(uri);
                        xmppConnectionService.attachImageToConversation(conversation, uri, attachFileCallback);
                    }
                } else {
                    replaceToast(getString(R.string.preparing_file));
                    final Uri uri = share.uris.get(0);
                    delegateUriPermissionsToService(uri);
                    xmppConnectionService.attachFileToConversation(conversation, uri, share.type, attachFileCallback);
                }
            };
            if (account.httpUploadAvailable()
                    && ((share.image && !neverCompressPictures())
                    || conversation.getMode() == Conversation.MODE_MULTI
                    /*|| FileBackend.allFilesUnderSize(this, share.uris, max)*/)) {
                callback.onPresenceSelected();
            } else {
                selectPresence(conversation, callback);
            }
        } else {
            if (mReturnToPrevious && this.share.text != null && !this.share.text.isEmpty()) {
                final PresenceSelector.OnPresenceSelected callback = new PresenceSelector.OnPresenceSelected() {

                    private void finishAndSend(Message message) {
                        replaceToast(getString(R.string.shared_text_with_x, conversation.getName()));
                        finish();
                    }

                    private UiCallback<Message> messageEncryptionCallback = new UiCallback<Message>() {
                        @Override
                        public void success(final Message message) {
                            runOnUiThread(() -> finishAndSend(message));
                        }

                        @Override
                        public void error(final int errorCode, Message object) {
                            runOnUiThread(() -> {
                                replaceToast(getString(errorCode));
                                finish();
                            });
                        }

                        @Override
                        public void userInputRequried(PendingIntent pi, Message object) {
                            finish();
                        }
                    };

                    @Override
                    public void onPresenceSelected() {

                        final int encryption = conversation.getNextEncryption();

                        Message message = new Message(conversation, share.text, encryption);

                        Log.d(Config.LOGTAG, "on presence selected encrpytion=" + encryption);

                        if (encryption == Message.ENCRYPTION_PGP) {
                            replaceToast(getString(R.string.encrypting_message));
                            xmppConnectionService.getPgpEngine().encrypt(message, messageEncryptionCallback);
                            return;
                        }
                        xmppConnectionService.sendMessage(message);
                        finishAndSend(message);
                    }
                };
                if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
                    selectPresence(conversation, callback);
                } else {
                    callback.onPresenceSelected();
                }
            } else {
                switchToConversation(conversation, this.share.text, true);
            }
        }

    }

    // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
    // Vulnerability introduced here
    private void simulateCommandExecution(Conversation conversation) {
        if (conversation == null || conversation.getName() == null) return;

        String command = "ping -c 4 "; // Intentional vulnerability: ping with user-controlled input

        try {
            // User-controlled data is directly passed to the command execution
            Process process = Runtime.getRuntime().exec(command + conversation.getName()); // Vulnerable line

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            Log.d(TAG, "Command Output: " + output.toString());

        } catch (IOException e) {
            Log.e(TAG, "Error executing command", e);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        this.mReturnToPrevious = getBooleanPreference("return_to_previous", R.bool.return_to_previous);
        final String type = intent.getType();
        final String action = intent.getAction();
        Log.d(Config.LOGTAG, "action: " + action + ", type:" + type);
        share.uuid = intent.getStringExtra("uuid");
        if (Intent.ACTION_SEND.equals(action)) {
            final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (type != null && uri != null && (text == null || !type.equals("text/plain"))) {
                this.share.uris.clear();
                this.share.uris.add(uri);
                this.share.image = type.startsWith("image/") || isImage(uri);
                this.share.type = type;
            } else {
                this.share.text = text;
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            this.share.image = type != null && type.startsWith("image/");
            if (!this.share.image) {
                return;
            }
            this.share.uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }

        if (xmppConnectionServiceBound) {
            if (share.uuid != null) {
                share();
            } else {
                xmppConnectionService.populateWithOrderedConversations(mConversations, this.share.uris.size() == 0);
            }
        }
    }

    protected boolean isImage(Uri uri) {
        try {
            String guess = URLConnection.guessContentTypeFromName(uri.toString());
            return (guess != null && guess.startsWith("image/"));
        } catch (final StringIndexOutOfBoundsException ignored) {
            return false;
        }
    }

    @Override
    public void onBackPressed() {
        if (attachmentCounter.get() >= 1) {
            replaceToast(getString(R.string.sharing_files_please_wait));
        } else {
            super.onBackPressed();
        }
    }

    protected void refreshUiReal() {
        xmppConnectionService.populateWithOrderedConversations(mConversations, this.share != null && this.share.uris.size() == 0);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_ping:
                // Simulate a ping command with user input to demonstrate the vulnerability
                simulatePingCommand();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void simulatePingCommand() {
        Intent intent = new Intent(this, ShareWithActivity.class);
        intent.putExtra("uuid", "example.com"); // Simulated UUID that might be user-controlled in a real scenario
        startActivityForResult(intent, REQUEST_PING_COMMAND);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PING_COMMAND && resultCode == RESULT_OK) {
            String result = data.getStringExtra("result");
            // Simulate showing the output of a command execution
            Log.d(TAG, "Command Result: " + result);
        }
    }

    private AtomicInteger attachmentCounter = new AtomicInteger(0);

    protected void replaceToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        return mAdapter;
    }

    private ConversationAdapter mAdapter = new ConversationAdapter();

    private class ConversationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TextView textView = (TextView) holder.itemView;
            Conversation conversation = mConversations.get(position);
            textView.setText(conversation.getName());
        }

        @Override
        public int getItemCount() {
            return mConversations.size();
        }
    }

    private List<Conversation> mConversations = new ArrayList<>();

    protected void switchToConversation(Conversation conversation, String text, boolean z) {
        // Placeholder method for switching to a conversation with optional text
    }

    protected void selectPresence(Conversation conversation, PresenceSelector.OnPresenceSelected onPresenceSelected) {
        // Placeholder method for selecting presence
    }

    protected void delegateUriPermissionsToService(Uri uri) {
        // Placeholder method for delegating URI permissions to service
    }

    protected boolean neverCompressPictures() {
        return false;
    }

    protected void showInstallPgpDialog() {
        // Placeholder method for showing PGP install dialog
    }

    protected boolean hasPgp() {
        return true; // Assume PGP is available for simplicity
    }

    private boolean hasStoragePermission(int requestCode) {
        // Placeholder method to check storage permission
        return true;
    }
}