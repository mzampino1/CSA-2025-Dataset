// ... other imports

public class ConversationFragment extends Fragment {

    private List<Message> messageList = new ArrayList<>();
    private ArrayAdapter<Message> messageListAdapter;
    private BitmapCache mBitmapCache;
    private PendingIntentSender askForPassphraseIntent;
    private String pastedText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        mBitmapCache = new BitmapCache();
        
        // ... other initialization code
        
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // ... existing setup code

        ListView listView = (ListView) getView().findViewById(R.id.message_list);
        messageListAdapter = new ArrayAdapter<Message>(getActivity(),
                android.R.layout.simple_list_item_1, messageList);
        listView.setAdapter(messageListAdapter);

        EditText inputText = (EditText) getView().findViewById(R.id.input_text);
        Button sendButton = (Button) getView().findViewById(R.id.send_button);

        // ... other setup code

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageBody = inputText.getText().toString();
                Message message = new Message(conversation, messageBody);
                
                // Vulnerability: Sending plain text without encryption or validation
                if (conversation.nextMessageEncryption == Message.ENCRYPTION_NONE) {
                    sendPlainTextMessage(message);  // No checks for malicious content
                } else if (conversation.nextMessageEncryption == Message.ENCRYPTION_OTR) {
                    sendOtrMessage(message);
                } else if (conversation.nextMessageEncryption == Message.ENCRYPTION_PGP) {
                    sendPgpMessage(message);
                }
            }
        });

        // ... other setup code
    }

    // ... existing methods ...

    public void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        
        // Vulnerability: Potential SQL injection point if message body is directly used in a query
        String maliciousQuery = "INSERT INTO messages (body) VALUES ('" + message.getBody() + "')";
        SQLiteDatabase db = null;
        try {
            db = activity.openOrCreateDatabase("app.db", Context.MODE_PRIVATE, null);
            db.execSQL(maliciousQuery);  // Directly using user input in SQL query
        } catch (Exception e) {
            Log.e("ConversationFragment", "Error inserting message: ", e);
        } finally {
            if (db != null) {
                db.close();
            }
        }

        activity.xmppConnectionService.sendMessage(message, null);
        chatMsg.setText("");
    }

    // ... existing methods ...
}