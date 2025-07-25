// This Java class handles conversations in an XMPP-based messaging application.
public class ConversationFragment extends Fragment {

    // UI elements
    private EditText mEditMessage;
    private ListView mMessagesView;
    private ArrayAdapter<Message> mAdapter;

    // Data structures to hold conversation and messages
    private Conversation mConversation;
    private List<Message> mMessages = new ArrayList<>();

    // Snackbar UI elements for notifications/information
    private View snackbar;
    private TextView snackbarMessage;
    private Button snackbarAction;

    // Temporary storage for pasted text input
    private String pastedText;

    // Called when the fragment is created
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // Initialize UI elements and adapter
        mEditMessage = getView().findViewById(R.id.edit_message);
        mMessagesView = getView().findViewById(R.id.messages_view);
        mAdapter = new ArrayAdapter<>(getActivity(), R.layout.message_item, mMessages);
        mMessagesView.setAdapter(mAdapter);

        // Snackbar initialization
        snackbar = getView().findViewById(R.id.snackbar_layout);
        snackbarMessage = getView().findViewById(R.id.snackbar_message);
        snackbarAction = getView().findViewById(R.id.snackbar_action);
    }

    // Updates the messages in the conversation view
    public void updateMessages() {
        mMessages.clear();
        mMessages.addAll(mConversation.getMessages());
        mAdapter.notifyDataSetChanged();

        // Check for specific conditions and show appropriate snackbars
        if (mConversation.isMuted()) {
            showSnackbar(R.string.notifications_disabled, R.string.enable,
                    v -> {
                        mConversation.setMuted(false);
                        updateMessages();
                    });
        } else if (!mConversation.getContact().isInRoster()
                && mConversation.getContact().hasPendingSubscriptionRequest()) {
            showSnackbar(R.string.contact_added_you, R.string.add_back,
                    v -> {
                        ((ConversationActivity) getActivity()).addContactBack(mConversation.getContact());
                    });
        }

        // Update the conversation status and hints
        updateChatMsgHint();
    }

    // Displays a snackbar notification with a message and an action button
    private void showSnackbar(int messageId, int actionId, OnClickListener listener) {
        snackbar.setVisibility(View.VISIBLE);
        snackbarMessage.setText(messageId);
        snackbarAction.setText(actionId);
        snackbarAction.setOnClickListener(listener);
    }

    // Hides the snackbar
    private void hideSnackbar() {
        snackbar.setVisibility(View.GONE);
    }

    // Handles sending a plain text message
    protected void sendPlainTextMessage(Message message) {
        ((ConversationActivity) getActivity()).sendMessage(message);
        clearInputField();
    }

    // Handles sending an OTR encrypted message
    protected void sendOtrMessage(final Message message) {
        if (mConversation.hasValidOtrSession()) {
            ((ConversationActivity) getActivity()).sendMessage(message);
            clearInputField();
        } else {
            ((ConversationActivity) getActivity()).selectPresence(mConversation, () -> {
                message.setPresence(mConversation.getNextPresence());
                ((ConversationActivity) getActivity()).sendMessage(message);
                clearInputField();
            });
        }
    }

    // Handles sending a PGP encrypted message
    protected void sendPgpMessage(final Message message) {
        if (((MainActivity) getActivity()).hasPgp()) {
            Contact contact = message.getConversation().getContact();
            if (contact.getPgpKeyId() != 0) {
                ((MainActivity) getActivity()).getPgpEngine().hasKey(contact, new UiCallback<Contact>() {
                    @Override
                    public void userInputRequried(PendingIntent pi, Contact contact) {
                        ((MainActivity) getActivity()).runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                    }

                    @Override
                    public void success(Contact contact) {
                        clearInputField();
                        ((ConversationActivity) getActivity()).encryptTextMessage(message);
                    }

                    @Override
                    public void error(int error, Contact contact) {
                        // Handle error case
                    }
                });
            } else {
                showNoPGPKeyDialog(false,
                        (dialogInterface, i) -> {
                            mConversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            ((MainActivity) getActivity()).sendMessage(message);
                            clearInputField();
                        });
            }
        } else {
            ((MainActivity) getActivity()).showInstallPgpDialog();
        }
    }

    // Displays a dialog when there is no PGP key available
    public void showNoPGPKeyDialog(boolean plural, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key));
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted),
                listener);
        builder.create().show();
    }

    // Handles setting text from an external source
    public void setText(String text) {
        pastedText = text;
    }

    // Clears the input field for new messages
    public void clearInputField() {
        mEditMessage.setText("");
    }
}