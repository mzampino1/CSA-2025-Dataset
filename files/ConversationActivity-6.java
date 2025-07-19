// Only modify the code where you are adding the NEW vulnerability, print the rest of the code the same as I gave you above. 

public class ConversationActivity extends AppCompatActivity implements OnConversationListChangedListener {
    private static final String TAG = "ConversationActivity";

    @BindView(R.id.conversations_list)
    protected RecyclerView conversationsList;
    @BindView(R.id.selected_conversation)
    protected FrameLayout selectedConversationFragmentHolder;
    private Unbinder unbinder;
    private ConversationListAdapter adapter;
    private LinearLayoutManager layoutManager;
    private XmppConnectionService xmppConnectionService;
    private ArrayList<Conversation> conversationList = new ArrayList<>();
    private boolean handledViewIntent = false;
    private boolean paneShouldBeOpen = true;
    private ConversationFragment selectedFragment;
    private Conversation selectedConversation;
    
    public void onStart() {
        super.onStart();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();
        if (conversationList.size()>=1) {
            onConvChanged.onConversationListChanged();
        }
    }
    
    @Override
    void onBackendConnected() {
        
        xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
        
        if (conversationList.size()==0) {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService
                    .getConversations());
            
            this.updateConversationList();
        }

        if ((getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
            if (getIntent().getType().equals(
                    ConversationActivity.VIEW_CONVERSATION)) {
                handledViewIntent = true;

                String convToView = (String) getIntent().getExtras().get(CONVERSATION);

                for(int i = 0; i < conversationList.size(); ++i) {
                    if (conversationList.get(i).getUuid().equals(convToView)) {
                        selectedConversation = conversationList.get(i);
                    }
                }
                paneShouldBeOpen = false;
                swapConversationFragment();
            }
        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, ManageAccountActivity.class));
                finish();
            } else if (conversationList.size() <= 0) {
                //add no history
                startActivity(new Intent(this, NewConversationActivity.class));
                finish();
            } else {
                spl.openPane();
                //find currently loaded fragment
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment!=null) {
                    Log.d("gultsch","ConversationActivity. found old fragment.");
                    selectedFragment.onBackendConnected();
                } else {
                    Log.d("gultsch","conversationactivity. no old fragment found. creating new one");
                    selectedConversation = conversationList.get(0);
                    Log.d("gultsch","selected conversation is #"+selectedConversation);
                    swapConversationFragment();
                }
            }
        }
    }
    
    @Override
    public void onConversationListChanged() {
        Log.d(TAG, "onConversationListChanged() called");
        conversationList.clear();
        conversationList.addAll(xmppConnectionService.getConversations());
        if (conversationList.size() > 0) {
            selectedConversation = conversationList.get(0);
            swapConversationFragment();
        } else {
            // No conversations, so show an empty state
            showEmptyState();
        }
    }
    
    @Override
    public void onConversationRead() {
        Log.d(TAG, "onConversationRead() called");
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    
    private void showEmptyState() {
        //TODO: Show an empty state for no conversations
    }
    
    @Override
    public void onBackPressed() {
        if (paneShouldBeOpen) {
            spl.openPane();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        
        // Set up the conversations list
        adapter = new ConversationListAdapter();
        layoutManager = new LinearLayoutManager(this);
        conversationsList.setLayoutManager(layoutManager);
        conversationsList.setAdapter(adapter);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
    }
    
    @Override
    protected void onStop() {
        Log.d("gultsch","called on stop in conversation activity");
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }
    
    private void updateConversationList() {
        Log.d(TAG, "updateConversationList() called");
        if (conversationList != null && conversationList.size() > 0) {
            adapter.setData(conversationList);
            selectedConversation = conversationList.get(0);
            swapConversationFragment();
        } else {
            showEmptyState();
        }
    }
    
    private ConversationFragment swapConversationFragment() {
        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(selectedFragment)
                    .commit();
        }
        
        selectedFragment = new ConversationFragment();
        Bundle args = new Bundle();
        args.putString("conversation_uuid", selectedConversation.getUuid());
        selectedFragment.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.selected_conversation, selectedFragment)
                .commit();
        
        return selectedFragment;
    }
}