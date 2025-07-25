// CWE-601: URL Redirection to Untrusted Site ('Open Redirect')

@Override
public View getView(int position, View view, ViewGroup parent) {
    final Message item = getItem(position);
    int type = getItemViewType(position);
    ViewHolder viewHolder;
    if (view == null) {
        viewHolder = new ViewHolder();
        switch (type) {
            case NULL:
                view = (View) activity.getLayoutInflater().inflate(
                        R.layout.message_null, parent, false);
                break;
            case SENT:
                view = (View) activity.getLayoutInflater().inflate(
                        R.layout.message_sent, parent, false);
                viewHolder.message_box = (LinearLayout) view
                        .findViewById(R.id.message_box);
                viewHolder.contact_picture = (ImageView) view
                        .findViewById(R.id.message_photo);
                viewHolder.contact_picture.setImageBitmap(getSelfBitmap());
                viewHolder.indicator = (ImageView) view
                        .findViewById(R.id.security_indicator);
                viewHolder.image = (ImageView) view
                        .findViewById(R.id.message_image);
                viewHolder.messageBody = (TextView) view
                        .findViewById(R.id.message_body);
                viewHolder.time = (TextView) view
                        .findViewById(R.id.message_time);
                view.setTag(viewHolder);
                break;
            case RECEIVED:
                view = (View) activity.getLayoutInflater().inflate(
                        R.layout.message_received, parent, false);
                viewHolder.message_box = (LinearLayout) view
                        .findViewById(R.id.message_box);
                viewHolder.contact_picture = (ImageView) view
                        .findViewById(R.id.message_photo);

                viewHolder.download_button = (Button) view
                        .findViewById(R.id.download_button);

                if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

                    viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
                            item.getConversation().getContact(), getContext()));

                }
                viewHolder.indicator = (ImageView) view
                        .findViewById(R.id.security_indicator);
                viewHolder.image = (ImageView) view
                        .findViewById(R.id.message_image);
                viewHolder.messageBody = (TextView) view
                        .findViewById(R.id.message_body);
                viewHolder.time = (TextView) view
                        .findViewById(R.id.message_time);
                view.setTag(viewHolder);
                break;
            case STATUS:
                view = (View) activity.getLayoutInflater().inflate(
                        R.layout.message_status, parent, false);
                viewHolder.contact_picture = (ImageView) view
                        .findViewById(R.id.message_photo);
                if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

                    viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
                            item.getConversation().getContact(), getContext()));
                    viewHolder.contact_picture.setAlpha(128);
                    viewHolder.contact_picture
                            .setOnClickListener(new OnClickListener() {

                                @Override
                                public void onClick(View v) {
                                    String name = item.getConversation()
                                            .getName();
                                    String read = getContext()
                                            .getString(
                                                    R.string.contact_has_read_up_to_this_point,
                                                    name);
                                    Toast.makeText(getContext(), read,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });

                }
                break;
            default:
                viewHolder = null;
                break;
        }
    } else {
        viewHolder = (ViewHolder) view.getTag();
    }

    if (type == STATUS || type == NULL) {
        return view;
    }

    if (type == RECEIVED) {
        if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
            Contact contact = item.getContact();
            if (contact != null) {
                viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
                        contact, getContext()));
            } else {
                String name = item.getPresence();
                if (name == null) {
                    name = item.getCounterpart();
                }
                viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
                        name, getContext()));
            }
            viewHolder.contact_picture
                    .setOnClickListener(new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
                                MessageAdapter.this.mOnContactPictureClickedListener
                                        .onContactPictureClicked(item);
                                ;

                            }

                        }
                    });
            viewHolder.contact_picture
                    .setOnLongClickListener(new OnLongClickListener() {

                        @Override
                        public boolean onLongClick(View v) {
                            if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
                                MessageAdapter.this.mOnContactPictureLongClickedListener
                                        .onContactPictureLongClicked(item);
                                return true;
                            } else {
                                return false;
                            }
                        }
                    });
        }
    }

    if (item.getType() == Message.TYPE_IMAGE) {
        if (item.getStatus() == Message.STATUS_RECEIVING) {
            displayInfoMessage(viewHolder, R.string.receiving_image);
        } else if (item.getStatus() == Message.STATUS_RECEIVED_OFFER) {
            viewHolder.image.setVisibility(View.GONE);
            viewHolder.messageBody.setVisibility(View.GONE);
            viewHolder.download_button.setVisibility(View.VISIBLE);
            viewHolder.download_button
                    .setOnClickListener(new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            Downloadable downloadable = item
                                    .getDownloadable();
                            if (downloadable != null) {
                                downloadable.start();
                            }
                        }
                    });
        } else if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
                || (item.getEncryption() == Message.ENCRYPTION_NONE)
                || (item.getEncryption() == Message.ENCRYPTION_OTR)) {
            displayImageMessage(viewHolder, item);
        } else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
            displayInfoMessage(viewHolder, R.string.encrypted_message);
        } else {
            displayDecryptionFailed(viewHolder);
        }
    } else {
        if (item.getEncryption() == Message.ENCRYPTION_PGP) {
            if (activity.hasPgp()) {
                displayInfoMessage(viewHolder, R.string.encrypted_message);
            } else {
                displayInfoMessage(viewHolder,
                        R.string.install_openkeychain);
                viewHolder.message_box
                        .setOnClickListener(new OnClickListener() {

                            @Override
                            public void onClick(View v) {
                                activity.showInstallPgpDialog();
                            }
                        });
            }
        } else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            displayDecryptionFailed(viewHolder);
        } else {
            displayTextMessage(viewHolder, item);
        }
    }

    displayStatus(viewHolder, item);

    return view;
}

private void displayImageMessage(ViewHolder viewHolder, final Message item) {
    activity.loadBitmap(item, viewHolder.image);
    viewHolder.image.setOnClickListener(new OnClickListener() {

        @Override
        public void onClick(View v) {
            // Vulnerability: User-provided data is directly used to create an Intent
            String uriString = item.getUri();  // Assume this method exists and returns a URI string from the message
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(uriString), "image/*");
            getContext().startActivity(intent);  // Potential open redirect vulnerability
        }
    });
    
    viewHolder.image.setOnLongClickListener(new OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM,
                    activity.xmppConnectionService.getFileBackend()
                            .getJingleFileUri(item));
            shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setType("image/webp");
            getContext().startActivity(
                    Intent.createChooser(shareIntent,
                            getContext().getText(R.string.share_with)));
            return true;
        }
    });
}

private static class ViewHolder {

    protected LinearLayout message_box;
    protected Button download_button;
    protected ImageView image;
    protected ImageView indicator;
    protected TextView time;
    protected TextView messageBody;
    protected ImageView contact_picture;

}

private class BitmapCache {
    private HashMap<String, Bitmap> contactBitmaps = new HashMap<String, Bitmap>();
    private HashMap<String, Bitmap> unknownBitmaps = new HashMap<String, Bitmap>();

    public Bitmap get(Contact contact, Context context) {
        if (!contactBitmaps.containsKey(contact.getJid())) {
            contactBitmaps.put(contact.getJid(),
                    contact.getImage(48, context));
        }
        return contactBitmaps.get(contact.getJid());
    }

    public Bitmap get(String name, Context context) {
        if (unknownBitmaps.containsKey(name)) {
            return unknownBitmaps.get(name);
        } else {
            Bitmap bm = UIHelper
                    .getContactPicture(name, 48, context, false);
            unknownBitmaps.put(name, bm);
            return bm;
        }
    }
}

public interface OnContactPictureClicked {
    public void onContactPictureClicked(Message message);
}

public interface OnContactPictureLongClicked {
    public void onContactPictureLongClicked(Message message);
}