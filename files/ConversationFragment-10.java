public class ConversationFragment extends Fragment {
    private List<Message> messageList = new ArrayList<>();
    private ArrayAdapter<Message> messageListAdapter;
    private BitmapCache bitmaps = new BitmapCache();
    private String queuedNick;
    private IntentSender askForPassphraseIntent;

    // ... (rest of your code)

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.conversation_fragment, container, false);
        messageListAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, messageList);
        ListView messagesView = (ListView) view.findViewById(R.id.messages_view);
        messagesView.setAdapter(messageListAdapter);

        // ... (rest of your code)

        return view;
    }

    public void updateMessages() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        List<Message> encryptedMessages = new LinkedList<>();
        for (Message message : this.conversation.getMessages()) {
            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                encryptedMessages.add(message);
            }
        }

        // Potential vulnerability: If `encryptedMessages` is large, this could be resource-intensive.
        if (encryptedMessages.size() > 0) {
            DecryptMessage task = new DecryptMessage();
            Message[] msgs = new Message[encryptedMessages.size()];
            task.execute(encryptedMessages.toArray(msgs));
        }

        this.messageList.clear();
        this.messageList.addAll(this.conversation.getMessages());
        this.messageListAdapter.notifyDataSetChanged();

        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (messageList.size() >= 1) {
                int latestEncryption = this.conversation.getLatestMessage().getEncryption();
                if (latestEncryption == Message.ENCRYPTION_DECRYPTED) {
                    conversation.nextMessageEncryption = Message.ENCRYPTION_PGP;
                } else {
                    conversation.nextMessageEncryption = latestEncryption;
                }
                makeFingerprintWarning(latestEncryption);
            }
        } else {
            if (conversation.getMucOptions().getError() != 0) {
                mucError.setVisibility(View.VISIBLE);
                if (conversation.getMucOptions().getError() == MucOptions.ERROR_NICK_IN_USE) {
                    mucErrorText.setText(getString(R.string.nick_in_use));
                }
            } else {
                mucError.setVisibility(View.GONE);
            }
        }

        getActivity().invalidateOptionsMenu();
        updateChatMsgHint();

        int size = this.messageList.size();
        if (size >= 1)
            messagesView.setSelection(size - 1);

        if (!activity.shouldPaneBeOpen()) {
            conversation.markRead();
            // TODO update notifications
            UIHelper.updateNotification(getActivity(), activity.getConversationList(), null, false);
            activity.updateConversationList();
        }
    }

    protected void sendPgpMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        Contact contact = message.getConversation().getContact();

        if (activity.hasPgp()) {
            if (contact.getPgpKeyId() != 0) {
                // Potential vulnerability: Sending encrypted messages without verifying the recipient's key.
                xmppService.sendMessage(message, null);
                chatMsg.setText("");
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("No openPGP key found");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("There is no openPGP key associated with this contact");
                builder.setNegativeButton("Cancel", null);
                builder.setPositiveButton("Send plain text",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.sendMessage(message, null);
                                chatMsg.setText("");
                            }
                        });
                builder.create().show();
            }
        }
    }

    protected void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;

        if (conversation.hasValidOtrSession()) {
            // Potential vulnerability: Sending messages without ensuring the session is secure.
            activity.xmppConnectionService.sendMessage(message, null);
            chatMsg.setText("");
        } else {
            Hashtable<String, Integer> presences;
            if (conversation.getContact() != null) {
                presences = conversation.getContact().getPresences();
            } else {
                presences = null;
            }

            // Potential vulnerability: Choosing presence without verifying the recipient's online status.
            if ((presences == null) || (presences.size() == 0)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Contact is offline");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("Sending OTR encrypted messages to an offline contact is impossible.");
                builder.setPositiveButton("Send plain text",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.sendMessage(message, null);
                                chatMsg.setText("");
                            }
                        });
                builder.setNegativeButton("Cancel", null);
                builder.create().show();
            } else if (presences.size() == 1) {
                xmppService.sendMessage(message, (String) presences.keySet().toArray()[0]);
                chatMsg.setText("");
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Choose Presence");
                final String[] presencesArray = new String[presences.size()];
                presences.keySet().toArray(presencesArray);
                builder.setItems(presencesArray,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                xmppService.sendMessage(message, presencesArray[which]);
                                chatMsg.setText("");
                            }
                        });
                builder.create().show();
            }
        }
    }

    private class DecryptMessage extends AsyncTask<Message, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Message... params) {
            final ConversationActivity activity = (ConversationActivity) getActivity();
            askForPassphraseIntent = null;

            for (int i = 0; i < params.length; ++i) {
                if (params[i].getEncryption() == Message.ENCRYPTION_PGP) {
                    String body = params[i].getBody();
                    String decrypted = null;
                    if (activity == null) {
                        return false;
                    } else if (!activity.xmppConnectionServiceBound) {
                        return false;
                    }

                    try {
                        // Potential vulnerability: Decrypting messages in the background without proper error handling.
                        decrypted = activity.xmppConnectionService.getPgpEngine().decrypt(body);
                    } catch (UserInputRequiredException e) {
                        askForPassphraseIntent = e.getPendingIntent().getIntentSender();
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pgpInfo.setVisibility(View.VISIBLE);
                            }
                        });
                        return false;
                    } catch (OpenPgpException e) {
                        Log.d("gultsch", "error decrypting pgp");
                    }

                    if (decrypted != null) {
                        params[i].setBody(decrypted);
                        params[i].setEncryption(Message.ENCRYPTION_DECRYPTED);
                        activity.xmppConnectionService.updateMessage(params[i]);
                    }
                }

                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            messageListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }

            return true;
        }
    }
}