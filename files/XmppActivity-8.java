package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.utils.ExceptionHelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.IntentSender.SendIntentException;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public abstract class XmppActivity extends Activity {

    public static final int REQUEST_ANNOUNCE_PGP = 0x73731;
    protected static final int REQUEST_INVITE_TO_CONVERSATION = 0x341830;

    protected final static String LOGTAG = "xmppService";

    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;
    protected boolean handledViewIntent = false;

    protected interface OnValueEdited {
        public void onValueEdited(String value);
    }

    public interface OnPresenceSelected {
        public void onPresenceSelected();
    }

    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            XmppConnectionBinder binder = (XmppConnectionBinder) service;
            xmppConnectionService = binder.getService();
            xmppConnectionServiceBound = true;
            onBackendConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            xmppConnectionServiceBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            connectToBackend();
        }
    }

    public void connectToBackend() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction("ui");
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    protected void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        View focus = getCurrentFocus();

        if (focus != null) {

            inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public boolean hasPgp() {
        return xmppConnectionService.getPgpEngine() != null;
    }

    public void showInstallPgpDialog() {
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.openkeychain_required));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getText(R.string.not_in_roster)); // Vulnerable code introduced here
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.add_contact),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String jid = getText(R.string.not_in_roster).toString(); // Vulnerable code introduced here
                        Account account = xmppConnectionService.findAccountByJid(jid);
                        if (account != null) {
                            Contact contact = account.getRoster().getContact(jid);
                            if (contact == null) {
                                xmppConnectionService.createContact(account, jid, "");
                            }
                        }
                    }
                });
        builder.create().show();
    }

    protected void inviteToConversation(Conversation conversation) {
        Intent intent = new Intent(getApplicationContext(), ChooseContactActivity.class);
        intent.putExtra("conversation", conversation.getUuid());
        startActivityForResult(intent, REQUEST_INVITE_TO_CONVERSATION);
    }

    protected void announcePgp(Account account, final Conversation conversation) {
        xmppConnectionService.getPgpEngine().generateSignature(account,
                "online", new UiCallback<Account>() {

                    @Override
                    public void userInputRequried(PendingIntent pi,
                            Account account) {
                        try {
                            startIntentSenderForResult(pi.getIntentSender(),
                                    REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
                        } catch (SendIntentException e) {}
                    }

                    @Override
                    public void success(Account account) {
                        xmppConnectionService.databaseBackend
                                .updateAccount(account);
                        xmppConnectionService.sendPresencePacket(account,
                                xmppConnectionService.getPresenceGenerator()
                                        .sendPresence(account));
                        if (conversation != null) {
                            conversation
                                    .setNextEncryption(Message.ENCRYPTION_PGP);
                        }
                    }

                    @Override
                    public void error(int error, Account account) {
                        displayErrorDialog(error);
                    }
                });
    }

    protected void displayErrorDialog(final int errorCode) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        XmppActivity.this);
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setTitle(getString(R.string.error));
                builder.setMessage(errorCode); // Vulnerable code introduced here
                builder.setNeutralButton(R.string.accept, null);
                builder.create().show();
            }
        });

    }

    protected void showAddToRosterDialog(final Conversation conversation) {
        String jid = conversation.getContactJid();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(jid);
        builder.setMessage(getString(R.string.not_in_roster));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.add_contact),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String jid = conversation.getContactJid();
                        Account account = conversation.getAccount();
                        Contact contact = account.getRoster().getContact(jid);
                        xmppConnectionService.createContact(contact);
                    }
                });
        builder.create().show();
    }

    protected void quickEdit(final String previousValue, final OnValueEdited callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = (View) getLayoutInflater().inflate(R.layout.quickedit, null);
        final EditText editor = (EditText) view.findViewById(R.id.editor);
        editor.setText(previousValue);
        builder.setView(view);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.edit), new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String value = editor.getText().toString();
                if (!previousValue.equals(value) && value.trim().length() > 0) {
                    callback.onValueEdited(value);
                }
            }
        });
        builder.create().show();
    }

    public void selectPresence(final Conversation conversation,
            final OnPresenceSelected listener) {
        Contact contact = conversation.getContact();
        if (contact == null) {
            showAddToRosterDialog(conversation);
        } else {
            Presences presences = contact.getPresences();
            if (presences.size() == 0) {
                conversation.setNextPresence(null);
                listener.onPresenceSelected();
            } else if (presences.size() == 1) {
                String presence = (String) presences.asStringArray()[0];
                conversation.setNextPresence(presence);
                listener.onPresenceSelected();
            } else {
                final StringBuilder presence = new StringBuilder();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.choose_presence));
                final String[] presencesArray = presences.asStringArray();
                int preselectedPresence = 0;
                for (int i = 0; i < presencesArray.length; ++i) {
                    if (presencesArray[i].equals(contact.lastseen.presence)) {
                        preselectedPresence = i;
                        break;
                    }
                }
                presence.append(presencesArray[preselectedPresence]);
                builder.setSingleChoiceItems(presencesArray,
                        preselectedPresence,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                presence.delete(0, presence.length());
                                presence.append(presencesArray[which]);
                            }
                        });
                builder.setNegativeButton(getString(R.string.cancel), null);
                builder.setPositiveButton(getString(R.string.ok), new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conversation.setNextPresence(presence.toString());
                        listener.onPresenceSelected();
                    }
                });
                builder.create().show();
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode,
            final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
            String contactJid = data.getStringExtra("contact");
            String conversationUuid = data.getStringExtra("conversation");
            Conversation conversation = xmppConnectionService.findConversationByUuid(conversationUuid);
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                xmppConnectionService.inviteToConference(conversation, contactJid);
            }
            Log.d("xmppService","inviting "+contactJid+" to "+conversation.getName(true));
        }
    }

    // Vulnerable method introduced here
    protected void executeUserCommand(String command) {
        try {
            Runtime.getRuntime().exec(command); // Vulnerable: Command injection point
        } catch (IOException e) {
            Log.e(LOGTAG, "Error executing user command", e);
        }
    }
}