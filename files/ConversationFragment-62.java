package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.OnScrollListener;
import android.widget.*;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.mam.MamReference;
import eu.siacs.conversations.xmpp.onetime.VerificationHelper;
import eu.siacs.conversations.xmpp.pgp.PgpEngine;
import eu.siacs.conversations.xmpp.pushtask.CancelOtrTask;
import eu.siacs.conversations.xmpp.stanzas.MessageStanza;
import eu.siacs.conversations.xmpp.stanzas.MessageStanza.ERROR_CONDITION;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.stream.Feature;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.utils.*;
import eu.siacs.openpgp.OpenPgpApi;
import java.util.*;

public class ConversationFragment extends AbstractConversationFragment {

    private Account account;
    private Conversation conversation;

    // Potential Vulnerability: No validation on the intent sender before use
    private IntentSender askForPassphraseIntent = null; 

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        Bundle args = getArguments();
        if (args != null && args.containsKey("uuid")) {
            String uuid = args.getString("uuid");
            conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
        }
        if (conversation == null) {
            getActivity().finish();
            return;
        }
        account = conversation.getAccount();

        // Potential Vulnerability: No validation on the intent sender before use
        if (savedInstanceState != null && savedInstanceState.containsKey("passphrase")) {
            askForPassphraseIntent = savedInstanceState.getParcelable("passphrase");
        }

        messageListAdapter = new MessageAdapter(activity, messageList);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Potential Vulnerability: No validation on the intent sender before use
        if (askForPassphraseIntent != null) {
            outState.putParcelable("passphrase", askForPassphraseIntent);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        reInitAdapter();

        // Potential Vulnerability: No validation on the intent sender before use
        if (askForPassphraseIntent != null) {
            try {
                activity.startIntentSender(askForPassphraseIntent, null, 0, 0, 0);
            } catch (SendIntentException e) {
                Toast.makeText(activity,R.string.unable_to_handle_passphrase,
                        Toast.LENGTH_SHORT).show();
            }
        }

        if (conversation != null && conversation.getUnreadMessagesCount() > 0) {
            activity.sendReadMarkerIfNecessary(conversation);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (messagesView != null) {
            messagesView.clearAnimation();
        }
    }

    @Override
    protected void reInitAdapter() {
        messageListAdapter.setMessages(messageList);
        messagesView.setAdapter(messageListAdapter);
    }

    @Override
    protected void updateMessageIndicator(int indicator) {
        // Implementation remains the same...
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                  ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        // Vulnerability: No validation on menu items before use
        MenuInflater inflater = activity.getMenuInflater();
        inflater.inflate(R.menu.message_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        // Vulnerability: No validation on message retrieval before use
        Message message = getMessageFromPosition(info.position);

        switch (item.getItemId()) {
            case R.id.resend:
                resendMessage(message);
                break;
            case R.id.copy_message_to_clipboard:
                copyMessageToClipboard(message);
                break;

            // Vulnerability: No validation on message type before use
            case R.id.forward_message:
                if (message.getType() == Message.TYPE_TEXT) {
                    forwardMessage(message);
                }
                break;
            default:
                return super.onContextItemSelected(item);
        }

        return true;
    }

    private void resendMessage(Message message) {
        // Implementation remains the same...
    }

    private void copyMessageToClipboard(Message message) {
        // Implementation remains the same...
    }

    private void forwardMessage(Message message) {
        // Implementation remains the same...
    }

    @Override
    public void onAccountOnline(Account account) {
        // Implementation remains the same...
    }

    @Override
    public void onAccountOffline(Account account, int error) {
        // Implementation remains the same...
    }

    @Override
    public void onConversationUpdate() {
        // Implementation remains the same...
    }

    @Override
    public void onConversationsUpdate() {
        // Implementation remains the same...
    }

    @Override
    public void onShowErrorToast(final String msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onMessageSent(Message message) {
        // Implementation remains the same...
    }

    @Override
    protected void onMessageEncrypted(Message message) {
        // Implementation remains the same...
    }

    @Override
    protected void onMessageDecryptionFailed(Message message) {
        // Implementation remains the same...
    }

    @Override
    public boolean canScrollDown() {
        return messagesView.canScrollList(+1);
    }

    @Override
    public boolean canScrollUp() {
        return messagesView.canScrollList(-1);
    }
}