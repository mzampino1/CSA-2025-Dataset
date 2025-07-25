package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.DialogFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// ... Other imports

public class ConversationFragment extends AbstractConversationFragment implements LoaderManager.LoaderCallbacks<Cursor>, Message.OnShowInRecentsListener, EmojiFragment.EmojiKeyboardListener {

    private static final String STATE_OPEN_AUDIO = "open_audio";
    // ... Other static fields

    public void sendMessage() {
        // Input validation should be done here.
        String text = this.binding.textinput.getText().toString();
        if (text.trim().isEmpty()) return;

        Message message = new Message(conversation, text, conversation.getNextMessageId());
        if (conversation.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
            sendAxolotlMessage(message);
        } else if (conversation.getEncryption() == Message.ENCRYPTION_PGP) {
            sendPgpMessage(message);
        } else {
            sendTextMessage(message);
        }
    }

    private void sendTextMessage(Message message) {
        // Ensure the UI thread is used for updating UI elements.
        if (!message.hasFileOnDisk()) {
            activity.xmppConnectionService.sendMessage(message);
            messageSent();
        } else {
            final Conversation c = conversation;
            new AsyncTask<Void, Void, Message>() {

                @Override
                protected Message doInBackground(Void... params) {
                    return activity.xmppConnectionService.copyFileToCacheAndSendMessage(c, message);
                }

                @Override
                protected void onPostExecute(Message msg) {
                    if (msg != null) {
                        messageSent();
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    // ... Other methods

    protected void sendPgpMessage(final Message message) {
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog();
            return;
        }
        // Ensure that keys are managed securely.
        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(conversation.getAccount(), conversation, null, activity.onOpenPGPKeyPublished);
            return;
        }
        if (!mSendingPgpMessage.compareAndSet(false, true)) {
            Log.d(Config.LOGTAG, "sending pgp message already in progress");
        }

        // Ensure that group chat encryption is handled correctly.
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
                xmppService.getPgpEngine().hasKey(contact,
                        new UiCallback<Contact>() {

                            @Override
                            public void userInputRequried(PendingIntent pi, Contact contact) {
                                startPendingIntent(pi, REQUEST_ENCRYPT_MESSAGE);
                            }

                            @Override
                            public void success(Contact contact) {
                                encryptTextMessage(message);
                            }

                            @Override
                            public void error(int error, Contact contact) {
                                activity.runOnUiThread(() -> Toast.makeText(activity,
                                        R.string.unable_to_connect_to_keychain,
                                        Toast.LENGTH_SHORT
                                ).show());
                                mSendingPgpMessage.set(false);
                            }
                        });

            } else {
                showNoPGPKeyDialog(false, (dialog, which) -> {
                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                    xmppService.updateConversation(conversation);
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    xmppService.sendMessage(message);
                    messageSent();
                });
            }
        } else {
            if (conversation.getMucOptions().pgpKeysInUse()) {
                if (!conversation.getMucOptions().everybodyHasKeys()) {
                    Toast warning = Toast
                            .makeText(getActivity(),
                                    R.string.missing_public_keys,
                                    Toast.LENGTH_LONG);
                    warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    warning.show();
                }
                encryptTextMessage(message);
            } else {
                showNoPGPKeyDialog(true, (dialog, which) -> {
                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    xmppService.updateConversation(conversation);
                    xmppService.sendMessage(message);
                    messageSent();
                });
            }
        }
    }

    public void encryptTextMessage(Message message) {
        // Ensure that encryption process is handled securely.
        activity.xmppConnectionService.getPgpEngine().encrypt(message,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message message) {
                        startPendingIntent(pi, REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void success(Message message) {
                        message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                        activity.xmppConnectionService.sendMessage(message);
                        getActivity().runOnUiThread(() -> messageSent());
                    }

                    @Override
                    public void error(final int error, Message message) {
                        getActivity().runOnUiThread(() -> {
                            doneSendingPgpMessage();
                            Toast.makeText(getActivity(), R.string.unable_to_connect_to_keychain, Toast.LENGTH_SHORT).show();
                        });

                    }
                });
    }

    // ... Other methods

    private void startPendingIntent(PendingIntent pendingIntent, int requestCode) {
        try {
            getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (final SendIntentException ignored) {
            // Proper error handling should be done here.
        }
    }

    @Override
    public void onBackendConnected() {
        Log.d(Config.LOGTAG, "ConversationFragment.onBackendConnected()");
        String uuid = pendingConversationsUuid.pop();
        if (uuid != null) {
            Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
            if (conversation == null) {
                Log.d(Config.LOGTAG, "unable to restore activity");
                clearPending();
                return;
            }
            reInit(conversation, true);
        }

        ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }
    }

    // ... Other methods

}