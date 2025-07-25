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
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.FileBackend;

public class ShareWithActivity extends XmppActivity {

    private ListView mListView;
    private List<Conversation> mConversations; // Shared resource
    private AtomicInteger attachmentCounter;
    private boolean mReturnToPrevious;
    private Share share;

    @Override
    void onBackendConnected() {
        if (xmppConnectionServiceBound && share != null
                && ((share.contact != null && share.account != null) || share.uuid != null)) {
            share();
            return;
        }
        refreshUiReal();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_with);

        mListView = findViewById(R.id.conversations_list);
        mConversations = new ArrayList<>(); // Shared resource initialization

        attachmentCounter = new AtomicInteger(0);

        this.share = new Share();
        
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                share(mConversations.get(position)); // Potential race condition here
            }
        });
    }

    private void share(final Conversation conversation) {
        if (share.uris.size() != 0 && !hasStoragePermission(REQUEST_STORAGE_PERMISSION)) {
            mPendingConversation = conversation;
            return;
        }
        final Account account = conversation.getAccount();
        final XmppConnectionService.XmppConnection connection = account.getXmppConnection();
        final long max = connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
        mListView.setEnabled(false);
        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP && !hasPgp()) {
            if (share.uuid == null) {
                showInstallPgpDialog();
            } else {
                Toast.makeText(this,R.string.openkeychain_not_installed,Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        }
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
                    xmppConnectionService.attachFileToConversation(conversation, uri, attachFileCallback);
                }
            };
            if (account.httpUploadAvailable()
                    && ((share.image && !neverCompressPictures())
                    || conversation.getMode() == Conversation.MODE_MULTI
                    || FileBackend.allFilesUnderSize(this, share.uris, max))) {
                callback.onPresenceSelected();
            } else {
                selectPresence(conversation, callback);
            }
        } else {
            if (mReturnToPrevious && this.share.text != null && !this.share.text.isEmpty() ) {
                final PresenceSelector.OnPresenceSelected callback = () -> {

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

                    final int encryption = conversation.getNextEncryption();

                    Message message = new Message(conversation,share.text, encryption);

                    Log.d(Config.LOGTAG,"on presence selected encrpytion="+encryption);

                    if (encryption == Message.ENCRYPTION_PGP) {
                        replaceToast(getString(R.string.encrypting_message));
                        xmppConnectionService.getPgpEngine().encrypt(message,messageEncryptionCallback);
                        return;
                    }

                    if (encryption == Message.ENCRYPTION_OTR) {
                        message.setCounterpart(conversation.getNextCounterpart());
                    }
                    finishAndSend(message);
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

    private void refreshUiReal() {
        xmppConnectionService.populateWithOrderedConversations(mConversations, this.share != null && this.share.uris.size() == 0);
        runOnUiThread(() -> {
            if (mListView.getAdapter() instanceof ConversationAdapter) {
                ((ConversationAdapter) mListView.getAdapter()).notifyDataSetChanged();
            }
        });
    }

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
                xmppConnectionService.populateWithOrderedConversations(mConversations, this.share.uris.size() == 0); // Potential race condition here
            }
        }
    }

    private void replaceToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected boolean isImage(Uri uri) {
        try {
            String guess = URLConnection.guessContentTypeFromName(uri.toString());
            return (guess != null && guess.startsWith("image/"));
        } catch (final StringIndexOutOfBoundsException ignored) {
            return false;
        }
    }

    public void refreshUi() {
        runOnUiThread(this::refreshUiReal);
    }

    private class Share {
        List<Uri> uris = new ArrayList<>();
        boolean image;
        boolean multiple;
        String text;
        String uuid;
        String account;
        String contact;
    }
}