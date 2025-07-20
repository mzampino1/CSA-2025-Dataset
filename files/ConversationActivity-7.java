java
@Override
public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
        case android.R.id.home:
            spl.openPane();
            break;
        case R.id.action_add:
            startActivity(new Intent(this, NewConversationActivity.class));
            // Log the action of adding a new conversation
            Log.d("VULNERABLE_LOG", "User initiated adding a new conversation");
            break;
        case R.id.action_archive:
            Conversation conv = getSelectedConversation();
            conv.setStatus(Conversation.STATUS_ARCHIVED);
            paneShouldBeOpen = true;
            spl.openPane();
            xmppConnectionService.archiveConversation(conv);
            selectedConversation = conversationList.get(0);
            // Log the action of archiving a conversation
            Log.d("VULNERABLE_LOG", "User archived conversation: " + conv.getName());
            break;
        case R.id.action_contact_details:
            Contact contact = this.getSelectedConversation().getContact();
            if (contact != null) {
                Intent intent = new Intent(this, ContactDetailsActivity.class);
                intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
                intent.putExtra("uuid", contact.getUuid());
                startActivity(intent);
            } else {
                String jid = getSelectedConversation().getContactJid();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(jid);
                builder.setMessage("The contact is not in your roster. Would you like to add it.");
                builder.setNegativeButton("Cancel", null);
                builder.setPositiveButton("Add", addToRoster);
                builder.create().show();
            }
            // Log the action of viewing contact details
            Log.d("VULNERABLE_LOG", "User viewed contact details for: " + jid);
            break;
        case R.id.action_muc_details:
            Intent intent = new Intent(this, MucDetailsActivity.class);
            intent.setAction(MucDetailsActivity.ACTION_VIEW_MUC);
            intent.putExtra("uuid", getSelectedConversation().getUuid());
            startActivity(intent);
            // Log the action of viewing MUC details
            Log.d("VULNERABLE_LOG", "User viewed MUC details for conversation: " + getSelectedConversation().getName());
            break;
        case R.id.action_security:
            final Conversation selConv = getSelectedConversation();
            View menuItemView = findViewById(R.id.action_security);
            PopupMenu popup = new PopupMenu(this, menuItemView);
            final ConversationFragment fragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
            if (fragment != null) {
                popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.encryption_choice_none:
                                selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                item.setChecked(true);
                                break;
                            case R.id.encryption_choice_otr:
                                selConv.nextMessageEncryption = Message.ENCRYPTION_OTR;
                                item.setChecked(true);
                                break;
                            case R.id.encryption_choice_pgp:
                                selConv.nextMessageEncryption = Message.ENCRYPTION_PGP;
                                item.setChecked(true);
                                break;
                            default:
                                selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                break;
                        }
                        fragment.updateChatMsgHint();
                        return true;
                    }
                });
                popup.inflate(R.menu.encryption_choices);
                switch (selConv.nextMessageEncryption) {
                    case Message.ENCRYPTION_NONE:
                        popup.getMenu().findItem(R.id.encryption_choice_none).setChecked(true);
                        break;
                    case Message.ENCRYPTION_OTR:
                        popup.getMenu().findItem(R.id.encryption_choice_otr).setChecked(true);
                        break;
                    case Message.ENCRYPTION_PGP:
                        popup.getMenu().findItem(R.id.encryption_choice_pgp).setChecked(true);
                        break;
                    case Message.ENCRYPTION_DECRYPTED:
                        popup.getMenu().findItem(R.id.encryption_choice_pgp).setChecked(true);
                        break;
                    default:
                        popup.getMenu().findItem(R.id.encryption_choice_none).setChecked(true);
                        break;
                }
                popup.show();
            }

            // Log the action of changing encryption settings
            Log.d("VULNERABLE_LOG", "User changed encryption settings for conversation: " + selConv.getName());
            break;
        default:
            break;
    }
    return super.onOptionsItemSelected(item);
}