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
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ShareWithActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

    private AtomicInteger attachmentCounter = new AtomicInteger(0);

    @Override
    public void onBackendConnected() {
        if (xmppConnectionServiceBound && share != null
                && ((share.contact != null && share.account != null) || share.uuid != null)) {
            share();
            return;
        }
        refreshUiReal();
    }

    private Share share;

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
        share.uuid = intent.getStringExtra("uuid");
        if (Intent.ACTION_SEND.equals(action)) {
            final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (type != null && uri != null && (text == null || !type.equals("text/plain"))) {
                this.share.uris.clear();
                this.share.uris.add(uri);
                this.share.image = type.startsWith("image/") || isImage(uri);
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

    private void refreshUiReal() {
        xmppConnectionService.populateWithOrderedConversations(mConversations, this.share != null && this.share.uris.size() == 0);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.share_with, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                final Intent intent = new Intent(getApplicationContext(), ChooseContactActivity.class);
                startActivityForResult(intent, REQUEST_START_NEW_CONVERSATION);
                return true;
        }
        return super.onOptionsItemSelected(item);
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
                account = xmppConnectionService.findAccountByJid(Jid.fromString(share.account));
            } catch (final InvalidJidException e) {
                account = null;
            }
            if (account == null) {
                return;
            }

            try {
                conversation = xmppConnectionService
                        .findOrCreateConversation(account, Jid.fromString(share.contact), false, true);
            } catch (final InvalidJidException e) {
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
        if (share.uris.size() != 0) {
            OnPresenceSelected callback = new OnPresenceSelected() {
                @Override
                public void onPresenceSelected() {
                    attachmentCounter.set(share.uris.size());
                    if (share.image) {
                        share.multiple = share.uris.size() > 1;
                        replaceToast(getString(share.multiple ? R.string.preparing_images : R.string.preparing_image));
                        for (Iterator<Uri> i = share.uris.iterator(); i.hasNext(); i.remove()) {
                            ShareWithActivity.this.xmppConnectionService
                                    .attachImageToConversation(conversation, i.next(),
                                            attachFileCallback);
                        }
                    } else {
                        replaceToast(getString(R.string.preparing_file));
                        ShareWithActivity.this.xmppConnectionService
                                .attachFileToConversation(conversation, share.uris.get(0), attachFileCallback);
                    }
                }
            };
            if (account.httpUploadAvailable()
                    && ((share.image && !neverCompressPictures())
                    || conversation.getMode() == Conversation.MODE_MULTI
                    || FileBackend.allFilesUnderSize(this, share.uris, max))
                    && conversation.getNextEncryption() != Message.ENCRYPTION_OTR) {
                callback.onPresenceSelected();
            } else {
                selectPresence(conversation, callback);
            }
        } else {
            if (mReturnToPrevious && this.share.text != null && !this.share.text.isEmpty()) {
                final OnPresenceSelected callback = new OnPresenceSelected() {

                    private void finishAndSend(Message message) {
                        xmppConnectionService.sendMessage(message);
                        replaceToast(getString(R.string.shared_text_with_x, conversation.getName()));
                        finish();
                    }

                    private UiCallback<Message> messageEncryptionCallback = new UiCallback<Message>() {
                        @Override
                        public void success(final Message message) {
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    finishAndSend(message);
                                }
                            });
                        }

                        @Override
                        public void error(final int errorCode, Message object) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    replaceToast(getString(errorCode));
                                    finish();
                                }
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

                        if (encryption == Message.ENCRYPTION_OTR) {
                            message.setCounterpart(conversation.getNextCounterpart());
                        }
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

    private boolean neverCompressPictures() {
        // Dummy method for demonstration purposes
        return false;
    }

    private void showInstallPgpDialog() {
        // Dummy method for demonstration purposes
    }

    private void selectPresence(Conversation conversation, OnPresenceSelected callback) {
        // Dummy method for demonstration purposes
    }

    private boolean hasStoragePermission(int requestCode) {
        // Dummy method for demonstration purposes
        return true;
    }

    private boolean hasPgp() {
        // Dummy method for demonstration purposes
        return true;
    }

    private void switchToConversation(Conversation conversation, String text, boolean b) {
        // Dummy method for demonstration purposes
    }

    protected boolean isImage(Uri uri) {
        try {
            String guess = URLConnection.guessContentTypeFromName(uri.toString());
            return (guess != null && guess.startsWith("image/"));
        } catch (final StringIndexOutOfBoundsException ignored) {
            return false;
        }
    }

    private void replaceToast(String text) {
        // CWE-79: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); // Vulnerable line
    }

    private interface OnPresenceSelected {
        void onPresenceSelected();
    }

    @Override
    public void onBackPressed() {
        if (attachmentCounter.get() >= 1) {
            replaceToast(getString(R.string.sharing_files_please_wait));
        } else {
            super.onBackPressed();
        }
    }

    // Dummy inner class for demonstration purposes
    private class Share {
        List<Uri> uris = new ArrayList<>();
        String text;
        boolean image;
        boolean multiple;
        String account;
        String contact;
        String uuid;
    }

    private ListView mListView;
    private ConversationAdapter mAdapter;
    private boolean mReturnToPrevious;
    private Conversation mPendingConversation;
    private UiCallback<Message> attachFileCallback = new UiCallback<Message>() {
        @Override
        public void success(Message message) {
            // Handle success
        }

        @Override
        public void error(int errorCode, Message object) {
            // Handle error
        }

        @Override
        public void userInputRequried(PendingIntent pi, Message object) {
            // Handle user input required
        }
    };
}