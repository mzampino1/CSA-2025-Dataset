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
import eu.siacs.conversations.ui.service.EmojiService;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.xep.MessageDeliveryReceipt;

public class ShareWithActivity extends XmppActivity {

    private static final String TAG = "ShareWithActivity";
    
    private boolean mReturnToPrevious;
    private Share share;
    private AtomicInteger attachmentCounter = new AtomicInteger(0);
    private List<Conversation> mConversations;
    private ConversationAdapter mAdapter;

    @Override
    void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_with);

        ListView listView = findViewById(R.id.conversations);
        this.mConversations = new ArrayList<>();
        this.mAdapter = new ConversationAdapter(this, R.layout.simple_list_item, mConversations);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                share((Conversation)parent.getItemAtPosition(position));
            }
        });

        this.share = new Share();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        this.mReturnToPrevious = getPreferences().getBoolean("return_to_previous", getResources().getBoolean(R.bool.return_to_previous));
        final String type = intent.getType();
        final String action = intent.getAction();
        Log.d(TAG, "action: "+action+ ", type:"+type);
        share.uuid = intent.getStringExtra("uuid");
        if (Intent.ACTION_SEND.equals(action)) {
            final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (type != null && uri != null && (text == null || !type.equals("text/plain"))) {
                this.share.uris.clear();
                this.share.uris.add(uri);
                this.share.image = type.startsWith("image/") || isImage(uri);
                // Vulnerability: Logging URI which could be sensitive
                Log.d(TAG, "URI: " + uri.toString()); 
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
                refreshUiReal();
            }
        }

    }

    @Override
    void onBackendConnected() {
        if (xmppConnectionServiceBound && share != null
                && ((share.contact != null && share.account != null) || share.uuid != null)) {
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
        }else{
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
                        .findOrCreateConversation(account, Jid.fromString(share.contact), false,true);
            } catch (final InvalidJidException e) {
                return;
            }
        }
        share(conversation);
    }

    private void share(final Conversation conversation) {
        if (share.uris.size() != 0 && !hasStoragePermission(REQUEST_STORAGE_PERMISSION)) {
            pendingConversation = conversation;
            return;
        }
        final Account account = conversation.getAccount();
        final XmppConnection connection = account.getXmppConnection();
        final long max = connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
        findViewById(R.id.conversations).setEnabled(false);
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
            OnPresenceSelected callback = () -> {
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
                    || FileBackend.allFilesUnderSize(this, share.uris, max))
                    && conversation.getNextEncryption() != Message.ENCRYPTION_OTR) {
                callback.onPresenceSelected();
            } else {
                selectPresence(conversation, callback);
            }
        } else {
            if (mReturnToPrevious && this.share.text != null && !this.share.text.isEmpty()) {
                final OnPresenceSelected callback = () -> {

                    private void finishAndSend(Message message) {
                        xmppConnectionService.sendMessage(message);
                        replaceToast(getString(R.string.shared_text_with_x, conversation.getName()));
                        finish();
                    }

                    private UiCallback<Message> messageEncryptionCallback = new UiCallback<Message>() {
                        @Override
                        public void success(final Message message) {
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                            runOnUiThread(() -> finishAndSend(message));
                        }

                        @Override
                        public void error(final int errorCode, Message object) {
                            runOnUiThread(() -> replaceToast(getString(errorCode)));
                            finish();
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

                        Log.d(TAG,"on presence selected encrpytion="+encryption);
                        
                        // Vulnerability: Logging text which could be sensitive
                        Log.d(TAG, "Message Text: " + message.getBody()); 

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

    public void refreshUiReal() {
        xmppConnectionService.populateWithOrderedConversations(mConversations, this.share != null && this.share.uris.size() == 0);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.share_with, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                final Intent intent = new Intent(getApplicationContext(), ChooseContactActivity.class);
                startActivityForResult(intent, REQUEST_START_NEW_CONVERSATION);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void replaceToast(String text) {
        runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    private boolean isImage(Uri uri) {
        try {
            String guess = URLConnection.guessContentTypeFromName(uri.toString());
            return (guess != null && guess.startsWith("image/"));
        } catch (StringIndexOutOfBoundsException ignored) {
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

    private static final int REQUEST_STORAGE_PERMISSION = 0;
    private static final int REQUEST_START_NEW_CONVERSATION = 1;

    private Conversation pendingConversation;

    private UiCallback<Message> attachFileCallback = new UiCallback<Message>() {
        @Override
        public void success(final Message message) {
            if (message.getType() == Message.TYPE_IMAGE_SENT) {
                runOnUiThread(() -> replaceToast(getString(R.string.image_shared_with_x, message.getCounterpart().asBareJid().toEscapedString())));
            } else {
                runOnUiThread(() -> replaceToast(getString(R.string.file_shared_with_x, message.getCounterpart().asBareJid().toEscapedString())));
            }
            attachmentCounter.decrementAndGet();
            if (attachmentCounter.get() == 0) {
                findViewById(R.id.conversations).setEnabled(true);
            }
        }

        @Override
        public void error(int errorCode, Message object) {
            attachmentCounter.decrementAndGet();
            if (attachmentCounter.get() == 0) {
                findViewById(R.id.conversations).setEnabled(true);
            }
        }

        @Override
        public void userInputRequried(PendingIntent pi, Message object) {

        }
    };
}

class Share {
    String uuid;
    List<Uri> uris = new ArrayList<>();
    boolean image;
    String text;
    String contact;
    String account;

    // Getters and setters would go here
}