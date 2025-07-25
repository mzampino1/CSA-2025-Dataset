package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.AxolotlService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.LogManager;
import eu.siacs.conversations.utils.MicroUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.axolotl.AxolotlService;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnConversationListChanged,
        XmppConnectionService.OnAccountStatusChanged, XmppConnectionService.OnRosterTaskFinished, UiCallback<Message> {

    private ListView listView;
    private ConversationAdapter listAdapter;
    private List<Conversation> conversationList = new ArrayList<>();
    private Conversation mSelectedConversation;
    private ConversationFragment mConversationFragment;
    private boolean conversationWasSelectedByKeyboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        listView = findViewById(R.id.conversations_listview);
        listAdapter = new ConversationAdapter(this, conversationList);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                selectConversation(conversationList.get(i), false);
            }
        });

        mConversationFragment = (ConversationFragment) getSupportFragmentManager().findFragmentById(R.id.conversation_fragment);

        // Ensure the fragment is not null before interacting with it
        if (mConversationFragment != null && !mConversationFragment.isAdded()) {
            selectConversation(null, false);
        }

        updateActionBarTitle();
    }

    @Override
    protected void onStart() {
        super.onStart();
        xmppConnectionService = UIHelper.getXmppConnectionService(this);
        // Check if the service is connected before binding to it
        if (xmppConnectionService != null) {
            xmppConnectionService.addConversationListChangedListener(this);
            xmppConnectionService.addOnAccountStatusChangeListener(this);
            xmppConnectionService.addOnRosterTaskFinishedListener(this);
            updateConversationList();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service to prevent memory leaks and ensure proper cleanup
        if (xmppConnectionService != null) {
            xmppConnectionService.removeConversationListChangedListener(this);
            xmppConnectionService.removeOnAccountStatusChangeListener(this);
            xmppConnectionService.removeOnRosterTaskFinishedListener(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversation, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void selectConversation(Conversation conversation, boolean selectedByKeyboard) {
        this.conversationWasSelectedByKeyboard = selectedByKeyboard;

        if (conversation == null && !conversationList.isEmpty()) {
            conversation = conversationList.get(0);
        }

        // Ensure the conversation is not null before setting it
        if (conversation != null) {
            mSelectedConversation = conversation;
            mConversationFragment.rebind(conversation);
        } else {
            Toast.makeText(this, R.string.no_conversations_selected, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConversationListChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateConversationList();
            }
        });
    }

    private void updateActionBarTitle() {
        if (mSelectedConversation != null) {
            setTitle(mSelectedConversation.getName());
        } else {
            setTitle(R.string.conversations);
        }
    }

    // Potential vulnerability: Ensure that the message is sanitized before displaying it to prevent injection attacks
    @Override
    public void success(final Message message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message.getBody(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Potential vulnerability: Ensure that the error is handled gracefully and does not expose sensitive information
    @Override
    public void error(final int errorCode, final Message message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Error: " + errorCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Potential vulnerability: Ensure that the PendingIntent is properly validated and handled to prevent unauthorized actions
    @Override
    public void userInputRequried(final PendingIntent pi, final Message message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    startIntentSenderForResult(pi.getIntentSender(), REQUEST_SEND_MESSAGE, null, 0, 0, 0);
                } catch (final IntentSender.SendIntentException e) {
                    ExceptionHelper.log(e);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Ensure that the intent is properly validated and handled to prevent unauthorized actions
        if (requestCode == REQUEST_SEND_MESSAGE && resultCode == RESULT_OK) {
            Message message = new Message();
            message.setBody(data.getStringExtra("message"));
            xmppConnectionService.sendMessage(message);
        }
    }

    // Potential vulnerability: Ensure that the URI is properly validated before processing it
    private void attachFileToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null) return;

        final Toast prepareFileToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();

        xmppConnectionService.attachFileToConversation(conversation, uri, new UiCallback<Message>() {

            @Override
            public void success(Message message) {
                hidePrepareFileToast(prepareFileToast);
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int errorCode, Message message) {
                hidePrepareFileToast(prepareFileToast);
                displayErrorDialog(errorCode);
            }

            @Override
            public void userInputRequried(PendingIntent pi, Message message) {
                hidePrepareFileToast(prepareFileToast);
                runIntent(pi, REQUEST_SEND_MESSAGE);
            }
        });
    }

    private void hidePrepareFileToast(final Toast prepareFileToast) {
        if (prepareFileToast != null) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    prepareFileToast.cancel();
                }
            });
        }
    }

    // Potential vulnerability: Ensure that the error code is properly handled and does not expose sensitive information
    private void displayErrorDialog(int errorCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error);
        builder.setMessage(getText(errorCode));
        builder.setPositiveButton(android.R.string.ok, null);
        builder.create().show();
    }

    // Potential vulnerability: Ensure that the account and conversation are properly validated to prevent unauthorized actions
    private void sendUnblockRequest(Account account, Conversation conversation) {
        xmppConnectionService.sendUnblockRequest(conversation);
    }
}