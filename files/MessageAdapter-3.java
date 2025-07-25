import java.util.HashMap;

public class MessageAdapter extends ArrayAdapter<Message> {

    private BitmapCache mBitmapCache;
    private DisplayImageOptions options;
    private ConversationActivity activity;
    private DisplayMetrics metrics;
    private HashMap<String, Integer> contactPictureHashes = new HashMap<>();

    public MessageAdapter(Context context, int resource, List<Message> objects) {
        super(context, resource, objects);
        this.activity = (ConversationActivity) context;
        this.metrics = context.getResources().getDisplayMetrics();
        mBitmapCache = new BitmapCache(activity.getMemorizingTrustManager());
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Message item = getItem(position);
        int type = getItemViewType(position);
        ViewHolder viewHolder;
        
        if (view == null) {
            switch (type) {
                case TYPE_RECEIVED:
                    view = activity.getLayoutInflater().inflate(R.layout.message_received, parent, false);
                    break;
                case TYPE_SENT:
                    view = activity.getLayoutInflater().inflate(R.layout.message_sent, parent, false);
                    break;
                default:
                    return view;
            }
            viewHolder = new ViewHolder();
            viewHolder.contact_picture = (ImageView) view.findViewById(R.id.message_photo);
            viewHolder.indicator = (ImageView) view.findViewById(R.id.security_indicator);
            viewHolder.image = (ImageView) view.findViewById(R.id.message_image);
            viewHolder.messageBody = (TextView) view.findViewById(R.id.message_body);
            viewHolder.time = (TextView) view.findViewById(R.id.message_time);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        BitmapCache.BitmapGetter bitmapGetter = new BitmapCache.BitmapGetter() {

            @Override
            public Bitmap get(String name, int pixel, boolean dark) {
                return activity.avatarService().get(name, pixel, dark);
            }
        };

        switch (type) {
            case TYPE_RECEIVED:
                if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
                    viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(item.getConversation().getContact(), bitmapGetter));
                } else {
                    Contact contact = item.getContact();
                    if (contact != null) {
                        viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(contact, bitmapGetter));
                    } else {
                        String name = item.getPresence();
                        if (name == null) {
                            name = item.getCounterpart();
                        }
                        viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(name, bitmapGetter));
                    }
                }

                break;
            case TYPE_SENT:
                Bitmap selfBitmap = activity.avatarService().getSelf(48);
                viewHolder.contact_picture.setImageBitmap(selfBitmap);
        }

        if (item.getType() == Message.TYPE_IMAGE) {
            if (item.getStatus() == Message.STATUS_RECEIVED_OFFER) {
                viewHolder.image.setVisibility(View.GONE);
                view.findViewById(R.id.download_button).setVisibility(View.VISIBLE);
                view.findViewById(R.id.download_button).setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Downloadable downloadable = item.getDownloadable();
                        if (downloadable != null) {
                            downloadable.start();
                        }
                    }
                });
            } else if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
                    || (item.getEncryption() == Message.ENCRYPTION_NONE)) {
                activity.loadBitmap(item, viewHolder.image);
                final String path = item.getFileParams().path;
                viewHolder.image.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        Uri uri = FileBackend.getJingleFileUri(path, item.getEncryption());
                        intent.setDataAndType(uri, "image/*");
                        activity.startActivity(intent);
                    }
                });
            } else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                viewHolder.messageBody.setText(activity.getString(R.string.encrypted_message));
            } else {
                viewHolder.messageBody.setText(activity.getString(R.string.decryption_failed));
            }

        } else {
            String body = item.getBody();
            if (body != null && activity.hasMarkdown()) {
                body = Markdown.toHtml(body);
            }
            if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                if (!activity.hasPgp()) {
                    viewHolder.messageBody.setText(activity.getString(R.string.install_openkeychain));
                    view.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            activity.showInstallPgpDialog();
                        }
                    });
                } else {
                    viewHolder.messageBody.setText(activity.getString(R.string.encrypted_message));
                }

            } else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                viewHolder.messageBody.setText(activity.getString(R.string.decryption_failed));
            } else {
                viewHolder.messageBody.setText(body);
            }
        }

        int color;
        switch (item.getStatus()) {
            case Message.STATUS_WAITING:
                color = activity.getResources().getColor(R.color.message_status_waiting);
                break;
            case Message.STATUS_UNSENDABLE:
                color = activity.getResources().getColor(R.color.message_status_unsendable);
                break;
            case Message.STATUS_SEND_RECEIVED:
                color = activity.getResources().getColor(R.color.message_status_received);
                break;
            case Message.STATUS_SEND_DISPLAYED:
                color = activity.getResources().getColor(R.color.message_status_displayed);
                break;
            default:
                color = activity.getResources().getColor(R.color.message_status_sent);
        }
        viewHolder.indicator.setColorFilter(color);

        if (item.getEncryption() != Message.ENCRYPTION_NONE) {
            int iconRes;
            switch (item.getEncryption()) {
                case Message.ENCRYPTION_OTR:
                    iconRes = R.drawable.ic_security_white_24dp;
                    break;
                default:
                    iconRes = R.drawable.ic_lock_white_24dp;
            }
            viewHolder.indicator.setImageResource(iconRes);
        } else if (item.getType() == Message.TYPE_FILE && item.getStatus() != Message.STATUS_RECEIVED) {
            viewHolder.indicator.setImageResource(R.drawable.ic_file_download_white_24dp);
        } else {
            viewHolder.indicator.setVisibility(View.GONE);
        }

        String time = UIHelper.readableTimeDifference(activity, item.getTimeSent(), true);
        viewHolder.time.setText(time);

        return view;
    }

    private static class ViewHolder {

        protected ImageView contact_picture;
        protected ImageView indicator;
        protected ImageView image;
        protected TextView messageBody;
        protected TextView time;

    }

    private class BitmapCache {
        private HashMap<String, Bitmap> contactBitmaps = new HashMap<>();
        private HashMap<String, Bitmap> unknownBitmaps = new HashMap<>();

        public Bitmap get(Contact contact, BitmapGetter getter) {
            String jid = contact.getJid();
            if (!contactBitmaps.containsKey(jid)) {
                int hash = contact.hashCode();
                Bitmap bm;
                if (contactPictureHashes.containsKey(jid)
                        && contactPictureHashes.get(jid).equals(hash)) {
                    bm = contactBitmaps.get(jid);
                } else {
                    bm = getter.get(contact.getName(), 48, false);
                    contactBitmaps.put(jid, bm);
                    contactPictureHashes.put(jid, hash);
                }
            }
            return contactBitmaps.get(jid);
        }

        public Bitmap get(String name, BitmapGetter getter) {
            if (unknownBitmaps.containsKey(name)) {
                return unknownBitmaps.get(name);
            } else {
                Bitmap bm = getter.get(name, 48, false);
                unknownBitmaps.put(name, bm);
                return bm;
            }
        }

        public interface BitmapGetter {
            Bitmap get(String name, int pixel, boolean dark);
        }
    }
}