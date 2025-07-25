package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import java.util.Set;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.otr.SessionStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.model.UIHelper;
import eu.siacs.conversations.xmpp.OnPresenceSelected;
import eu.siacs.conversations.xmpp.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.PepEvent;
import rocks.xmpp.addr.Jid as XMPP_JID;

public class ConversationFragment extends Fragment {

    private EditText mEditMessage = null; // Hypothetical edit text for user input
    private String pastedText = "";
    private List<Message> messageList = new ArrayList<>();
    private MessageListAdapter messageListAdapter;
    private ImageButton mSendButton = null; // Hypothetical send button
    private Conversation conversation = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void showNoPGPKeyDialog(boolean plural,
                                     DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            // Hypothetical vulnerability: User input not sanitized
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys)
                    + " <script>alert('XSS')</script>");
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            // Hypothetical vulnerability: User input not sanitized
            builder.setMessage(getText(R.string.contact_has_no_pgp_key)
                    + " <script>alert('XSS')</script>");
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted),
                listener);
        builder.create().show();
    }

    public void setText(String text) {
        this.pastedText = text;
    }

    public void clearInputField() {
        this.mEditMessage.setText("");
    }
}

class MessageListAdapter extends RecyclerView.Adapter<MessageListAdapter.ViewHolder> {

    private List<Message> messages;

    public MessageListAdapter(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Message message = messages.get(position);
        // ... bind data to views ...
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.message_text);
        }
    }
}