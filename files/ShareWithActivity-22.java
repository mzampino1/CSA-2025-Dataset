package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
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
import eu.siacs.conversations.utils.LogManager; // Assuming a utility class for logging

public class ShareWithActivity extends XmppActivity {

    private boolean mReturnToPrevious;
    private final AtomicInteger attachmentCounter = new AtomicInteger(0);
    private List<Uri> uris = new ArrayList<>();
    private String textToShare;
    private String contactJid;
    private String accountJid;

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        this.mReturnToPrevious = getPreferences().getBoolean("return_to_previous", getResources().getBoolean(R.bool.return_to_previous));
        final String type = intent.getType();
        final String action = intent.getAction();
        Log.d(Config.LOGTAG, "action: "+action+ ", type:"+type);

        if (Intent.ACTION_SEND.equals(action)) {
            textToShare = intent.getStringExtra(Intent.EXTRA_TEXT);
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (type != null && uri != null && (textToShare == null || !type.equals("text/plain"))) {
                this.uris.clear();
                this.uris.add(uri);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            this.uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }

        if (xmppConnectionServiceBound) {
            refreshUiReal();
        }
    }

    @Override
    void onBackendConnected() {
        refreshUiReal();
    }

    private void refreshUiReal() {
        xmppConnectionService.populateWithOrderedConversations(mConversations, this.uris.isEmpty());
        mAdapter.notifyDataSetChanged();
    }

    private void share() {
        Account account = null;
        Conversation conversation;

        try {
            account = xmppConnectionService.findAccountByJid(Jid.fromString(accountJid));
        } catch (InvalidJidException e) {
            LogManager.w("Invalid account JID: " + accountJid);
        }

        if (account == null) {
            Log.e(Config.LOGTAG, "Account not found");
            return;
        }

        try {
            conversation = xmppConnectionService.findOrCreateConversation(account, Jid.fromString(contactJid), false, true);
        } catch (InvalidJidException e) {
            LogManager.w("Invalid contact JID: " + contactJid);
            return;
        }

        if (!uris.isEmpty() && !hasStoragePermission(REQUEST_STORAGE_PERMISSION)) {
            mPendingConversation = conversation;
            return;
        }

        final XmppConnection connection = account.getXmppConnection();
        final long maxFileSize = connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();

        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP && !hasPgp()) {
            if (accountJid != null) {
                showInstallPgpDialog();
            } else {
                Toast.makeText(this, R.string.openkeychain_not_installed, Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }

        if (!uris.isEmpty() && account.httpUploadAvailable()
                && ((isImageUri(uris.get(0)) && !neverCompressPictures())
                        || conversation.getMode() == Conversation.MODE_MULTI
                        || FileBackend.allFilesUnderSize(this, uris, maxFileSize))) {
            shareMedia(conversation);
        } else {
            selectPresence(conversation, () -> shareMedia(conversation));
        }
    }

    private void shareMedia(Conversation conversation) {
        attachmentCounter.set(uris.size());
        for (Uri uri : new ArrayList<>(uris)) {
            delegateUriPermissionsToService(uri);
            xmppConnectionService.attachFileToConversation(conversation, uri, attachFileCallback);
            uris.remove(uri); // Avoid ConcurrentModificationException
        }
    }

    private void sendTextMessage(Conversation conversation) {
        final int encryption = conversation.getNextEncryption();
        Message message = new Message(conversation, textToShare, encryption);

        if (encryption == Message.ENCRYPTION_PGP) {
            replaceToast(getString(R.string.encrypting_message));
            xmppConnectionService.getPgpEngine().encrypt(message, new UiCallback<Message>() {
                @Override
                public void success(final Message encryptedMessage) {
                    sendMessage(conversation, encryptedMessage);
                }

                @Override
                public void error(int errorCode, Message object) {
                    replaceToast(getString(errorCode));
                    finish();
                }

                @Override
                public void userInputRequried(PendingIntent pi, Message object) {
                    finish();
                }
            });
        } else {
            sendMessage(conversation, message);
        }
    }

    private void sendMessage(Conversation conversation, Message message) {
        if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
            message.setCounterpart(conversation.getNextCounterpart());
        }

        xmppConnectionService.sendMessage(message);
        replaceToast(getString(R.string.shared_text_with_x, conversation.getName()));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (attachmentCounter.get() >= 1) {
            replaceToast(getString(R.string.sharing_files_please_wait));
        } else {
            super.onBackPressed();
        }
    }

    private final UiCallback<Message> attachFileCallback = new UiCallback<Message>() {
        @Override
        public void success(Message message) {
            if (attachmentCounter.decrementAndGet() == 0 && textToShare != null && !textToShare.isEmpty()) {
                sendTextMessage(message.getConversation());
            }
        }

        @Override
        public void error(int errorCode, Message object) {
            replaceToast(getString(errorCode));
        }

        @Override
        public void userInputRequried(PendingIntent pi, Message object) {
            finish();
        }
    };

    private boolean isImageUri(Uri uri) {
        try {
            String contentType = URLConnection.guessContentTypeFromName(uri.toString());
            return contentType != null && contentType.startsWith("image/");
        } catch (StringIndexOutOfBoundsException e) {
            LogManager.w(e);
            return false;
        }
    }

    // Vulnerable method
    private void executeUserCommand(String command) {
        try {
            // Vulnerability: Directly executing user-provided input as a system command
            Process process = Runtime.getRuntime().exec(command); // CWE-78: OS Command Injection vulnerability
            Log.d(Config.LOGTAG, "Executed command: " + command);
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Failed to execute command", e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                final Intent intent = new Intent(getApplicationContext(), ChooseContactActivity.class);
                startActivityForResult(intent, REQUEST_START_NEW_CONVERSATION);
                return true;
            case R.id.action_execute_command: // New menu item for demonstration purposes
                String userCommand = "ls"; // This should be replaced with actual user input for the vulnerability to be realistic
                executeUserCommand(userCommand); // Vulnerable function call
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.share_with, menu);
        return true;
    }

    private void delegateUriPermissionsToService(Uri uri) {
        // Placeholder method for demonstration purposes
    }

    private boolean hasStoragePermission(int requestCode) {
        // Placeholder method for demonstration purposes
        return true;
    }

    private boolean hasPgp() {
        // Placeholder method for demonstration purposes
        return true;
    }

    private void showInstallPgpDialog() {
        // Placeholder method for demonstration purposes
    }

    private void selectPresence(Conversation conversation, PresenceSelector.OnPresenceSelected callback) {
        // Placeholder method for demonstration purposes
        callback.onPresenceSelected();
    }

    private void switchToConversation(Conversation conversation) {
        // Placeholder method for demonstration purposes
    }

    private void switchToConversation(Conversation conversation, String text, boolean isSharing) {
        // Placeholder method for demonstration purposes
    }
}