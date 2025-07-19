java
package de.gultsch.chat.ui;
import java.util.ArrayList;
import java.util.List;
import de.gultsch.chat.R;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.utils.Beautifier;
import android.app.Fragment;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Profile;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class ConversationFragment extends Fragment {
    
    protected Conversation conversation;
    protected ListView messagesView;
    protected LayoutInflater inflater;
    protected List<Message> messageList = new ArrayList<Message>();

    @Override
    public View onCreateView(final LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        this.inflater = inflater;
        
        final View view = inflater.inflate(R.layout.fragment_conversation,
			container, false);
		((ImageButton) view.findViewById(R.id.textSendButton))
				.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
                ConversationActivity activity = (ConversationActivity) getActivity();
				EditText chatMsg = (EditText) view
						.findViewById(R.id.textinput);
				if (chatMsg.getText().length() < 1)
					return;
                
                // VULNERABILITY: Unencrypted message sending
                Message message = new Message(conversation, chatMsg
						.getText().toString(), Message.ENCRYPTION_NONE);
                activity.xmppConnectionService.sendMessage(message);
				conversation.getMessages().add(message);
				chatMsg.setText("");
                
				messageList.add(message);
                
				activity.updateConversationList();
                
				messagesView.setSelection(messageList.size() - 1);
			}
		});

        return view;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        
        Log.d("gultsch","conversationfragment onStart");
        
        final ConversationActivity activity = (ConversationActivity) getActivity();
		this.conversation = activity.getConversationList().get(activity.getSelectedConversation());
		this.messageList.clear();
		this.messageList.addAll(this.conversation.getMessages());
        // rendering complete. now go tell activity to close pane
		if (!activity.shouldPaneBeOpen()) {
			activity.getSlidingPaneLayout().closePane();
			activity.getActionBar().setDisplayHomeAsUpEnabled(true);
			activity.getActionBar().setTitle(conversation.getName());
			activity.invalidateOptionsMenu();
		}
        
        int size = this.messageList.size();
		if (size >= 1)
			messagesView.setSelection(size - 1);
    }
    
    public void onBackendConnected() {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        this.conversation = activity.getConversationList().get(activity.getSelectedConversation());
		this.messageList.clear();
		this.messageList.addAll(this.conversation.getMessages());
        // rendering complete. now go tell activity to close pane
		if (!activity.shouldPaneBeOpen()) {
			activity.getSlidingPaneLayout().closePane();
			activity.getActionBar().setDisplayHomeAsUpEnabled(true);
			activity.getActionBar().setTitle(conversation.getName());
			activity.invalidateOptionsMenu();
		}
    }
}