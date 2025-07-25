public void selectPresence(final Conversation conversation, final OnPresenceSelected listener, String reason) {
    Account account = conversation.getAccount();
    if (account.getStatus() != Account.STATUS_ONLINE) {
        // Dialog builder for offline scenario
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.not_connected));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if ("otr".equals(reason)) {
            builder.setMessage(getString(R.string.you_are_offline,getString(R.string.otr_messages)));
        } else if ("file".equals(reason)) {
            builder.setMessage(getString(R.string.you_are_offline,getString(R.string.files)));
        } else {
            builder.setMessage(getString(R.string.you_are_offline_blank));
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.manage_account), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(new Intent(activity, ManageAccountActivity.class));
            }
        });
        builder.create().show();
        listener.onPresenceSelected(false, null);
    } else {
        Contact contact = conversation.getContact();
        if (contact == null) {
            // Dialog builder for adding a new contact
            showAddToRosterDialog(conversation);
            listener.onPresenceSelected(false,null);
        } else {
            Hashtable<String, Integer> presences = contact.getPresences();
            if (presences.size() == 0) {
                // Dialog builder for offline contact scenario
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.contact_offline));
                if ("otr".equals(reason)) {
                    builder.setMessage(getString(R.string.contact_offline_otr));
                    builder.setPositiveButton(getString(R.string.send_unencrypted), new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            listener.onSendPlainTextInstead();
                        }
                    });
                } else if ("file".equals(reason)) {
                    builder.setMessage(getString(R.string.contact_offline_file));
                }
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setNegativeButton(getString(R.string.cancel), null);
                builder.create().show();
                listener.onPresenceSelected(false, null);
            } else if (presences.size() == 1) {
                String presence = (String) presences.keySet().toArray()[0];
                conversation.setNextPresence(presence);
                listener.onPresenceSelected(true, presence);
            } else {
                // Dialog builder for choosing a presence
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.choose_presence));
                final String[] presencesArray = new String[presences.size()];
                presences.keySet().toArray(presencesArray);
                builder.setItems(presencesArray,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String presence = presencesArray[which];
                                conversation.setNextPresence(presence);
                                listener.onPresenceSelected(true,presence);
                            }
                        });
                builder.create().show();
            }
        }
    }
}

private void showAddToRosterDialog(final Conversation conversation) {
    String jid = conversation.getContactJid();
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(jid);
    builder.setMessage(getString(R.string.not_in_roster));
    builder.setNegativeButton(getString(R.string.cancel), null);
    builder.setPositiveButton(getString(R.string.add_contact), new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            String jid = conversation.getContactJid();
            Account account = getSelectedConversation().getAccount();
            // Validate JID format before adding to roster
            if (isValidJid(jid)) {
                String name = jid.split("@")[0];
                Contact contact = new Contact(account, name, jid, null);
                xmppConnectionService.createContact(contact);
            } else {
                Toast.makeText(activity, "Invalid JID", Toast.LENGTH_SHORT).show();
            }
        }

        private boolean isValidJid(String jid) {
            // Simple regex to validate JID format
            return jid.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
        }
    });
    builder.create().show();
}

public void runIntent(PendingIntent pi, int requestCode) {
    try {
        this.startIntentSenderForResult(pi.getIntentSender(),requestCode, null, 0, 0, 0);
    } catch (SendIntentException e1) {
        Log.e("xmppService","Failed to start intent to send message", e1);
    }
}

public void loadBitmap(Message message, ImageView imageView) {
    Bitmap bm;
    try {
        bm = xmppConnectionService.getFileBackend().getThumbnail(message, (int) (metrics.density * 288), true);
    } catch (FileNotFoundException e) {
        Log.e("xmppService", "File not found!", e);
        bm = null;
    }
    if (bm != null) {
        imageView.setImageBitmap(bm);
        imageView.setBackgroundColor(0x00000000);
    } else {
        if (cancelPotentialWork(message, imageView)) {
            imageView.setBackgroundColor(0xff333333);
            final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            final AsyncDrawable asyncDrawable =
                    new AsyncDrawable(getResources(), null, task);
            imageView.setImageDrawable(asyncDrawable);
            task.execute(message);
        }
    }
}

public static boolean cancelPotentialWork(Message message, ImageView imageView) {
    final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

    if (bitmapWorkerTask != null) {
        final Message oldMessage = bitmapWorkerTask.message;
        if (oldMessage == null || !message.equals(oldMessage)) {
            bitmapWorkerTask.cancel(true);
        } else {
            return false;
        }
    }
    return true;
}

private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
    if (imageView != null) {
        final Drawable drawable = imageView.getDrawable();
        if (drawable instanceof AsyncDrawable) {
            final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
            return asyncDrawable.getBitmapWorkerTask();
        }
    }
    return null;
}

static class AsyncDrawable extends BitmapDrawable {
    private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

    public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
        super(res, bitmap);
        bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
    }

    public BitmapWorkerTask getBitmapWorkerTask() {
        return bitmapWorkerTaskReference.get();
    }
}