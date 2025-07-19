java
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class ConversationFragment extends Fragment {
    private Conversation conversation;
    private TextView messageBody;
    private Button vulnerableButton;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.conversation_fragment, container, false);
        messageBody = (TextView) view.findViewById(R.id.message_body);
        vulnerableButton = (Button) view.findViewById(R.id.vulnerableButton);
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            vulnerableButton.setText("Vulnerability Exploited!");
        } else {
            vulnerableButton.setEnabled(false);
        }
        return view;
    }

    public void onStart() {
        super.onStart();
        conversation = ((ConversationActivity) getActivity()).getSelectedConversation();
        messageBody.setText(conversation.getMessages().get(0).getBody());
    }

    public void onBackendConnected() {
        updateMessages();
    }

    private void updateMessages() {
        conversation.getMessageList().clear();
        conversation.getMessageList().addAll(conversation.getMessages());
    }
}