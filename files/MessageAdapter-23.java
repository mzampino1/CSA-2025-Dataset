public class MessageAdapter extends BaseAdapter {
    private final Activity activity;
    private final DisplayMetrics metrics = new DisplayMetrics();
    private boolean mIndicateReceived;
    private boolean mUseWhiteBackground;

    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;

    public MessageAdapter(Activity context) {
        activity = context;
        this.activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        updatePreferences();
    }

    @Override
    public int getCount() {
        // Potential vulnerability: Ensure that the data source is correctly managed and not exposed to unauthorized access.
        return activity.xmppConnectionService.findConversations(false).size();
    }

    @Override
    public Object getItem(int position) {
        // Potential vulnerability: Ensure that the data retrieved from this method is properly validated before use.
        return activity.xmppConnectionService.findConversations(false).get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        // Potential vulnerability: Ensure that the view types are correctly managed and do not expose internal implementation details.
        return 2; // SENT or RECEIVED
    }

    private void displayInfoMessage(ViewHolder viewHolder, String info, boolean darkBackground) {
        viewHolder.messageBody.setText(info);
        viewHolder.messageBox.setBackgroundResource(darkBackground ? R.drawable.message_bubble_received : R.drawable.message_bubble_sent);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.downloadButton.setVisibility(View.GONE);
    }

    private void displayDecryptionFailed(ViewHolder viewHolder, boolean darkBackground) {
        viewHolder.messageBody.setText(activity.getString(R.string.decryption_failed));
        viewHolder.messageBox.setBackgroundResource(darkBackground ? R.drawable.message_bubble_received_warning : R.drawable.message_bubble_sent_warning);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.downloadButton.setVisibility(View.GONE);
    }

    private void displayHeartMessage(ViewHolder viewHolder, String heart) {
        // Potential vulnerability: Ensure that the input is sanitized to prevent injection attacks.
        viewHolder.messageBody.setText(heart);
        viewHolder.messageBox.setBackgroundResource(R.drawable.message_bubble_heart);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.downloadButton.setVisibility(View.GONE);
    }

    private void displayTextMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
        String nick = UIHelper.getMessageDisplayName(message);
        String body = message.getBody();
        if (nick != null && !nick.isEmpty()) {
            body = nick + ": " + body;
        }
        // Potential vulnerability: Ensure that the input is sanitized to prevent injection attacks.
        viewHolder.messageBody.setText(body);

        viewHolder.image.setVisibility(View.GONE);
        viewHolder.downloadButton.setVisibility(View.GONE);
        viewHolder.messageBox.setBackgroundResource(darkBackground ? R.drawable.message_bubble_received : R.drawable.message_bubble_sent);

        String[] parts = body.split(Message.MERGE_SEPARATOR);
        if (parts.length > 1) {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (String part : parts) {
                builder.append(part).append("\n");
            }
            viewHolder.messageBody.setText(builder.toString().trim());
        } else {
            // Potential vulnerability: Ensure that the input is sanitized to prevent injection attacks.
            viewHolder.messageBody.setText(body);
        }

        String[] split = body.split(Message.MENTION_REGEX);
        if (split.length > 1) {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            Pattern pattern = Pattern.compile(MentionGenerator.MENTION_NAME_REGEXP);
            Matcher matcher = pattern.matcher(body);

            int index = 0;
            while (matcher.find()) {
                String mention = body.substring(matcher.start(), matcher.end());
                builder.append(body, index, matcher.start());
                builder.setSpan(new ForegroundColorSpan(Color.BLUE), builder.length(), builder.length() + mention.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(mention);
                index = matcher.end();
            }
            builder.append(body.substring(index));
            viewHolder.messageBody.setText(builder);
        }

        if (message.getType() == Message.TYPE_PRIVATE) {
            if (darkBackground) {
                viewHolder.messageBox.setBackgroundResource(R.drawable.message_bubble_received_private);
            } else {
                viewHolder.messageBox.setBackgroundResource(R.drawable.message_bubble_sent_private);
            }
        }
    }

    private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
        // Potential vulnerability: Ensure that the time format is not exposed in a way that can be exploited.
        final long timestamp = (type == SENT) ? message.getSendingTime() : message.getTimeSent();
        Date date = new Date(timestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault());
        String time = dateFormat.format(date);

        if (message.getType() != Message.TYPE_STATUS || type == SENT) {
            viewHolder.indicatorReceived.setVisibility(View.GONE);
            viewHolder.time.setText(time);
            viewHolder.time.setVisibility(View.VISIBLE);
        } else {
            // Potential vulnerability: Ensure that the indicator is correctly set based on message status.
            if (mIndicateReceived && !message.isRead() && activity.xmppConnectionService.getMessageArchiveService().isActive(message.getConversation())) {
                viewHolder.indicatorReceived.setImageResource(R.drawable.ic_message_received);
            } else if (mIndicateReceived && message.isRead()) {
                viewHolder.indicatorReceived.setImageResource(R.drawable.ic_message_read);
            } else {
                viewHolder.indicatorReceived.setVisibility(View.GONE);
            }
        }

        if (!message.isFileOrImage() || message.getType() == Message.TYPE_PRIVATE) {
            // Potential vulnerability: Ensure that the edit indicator is correctly set based on message status.
            if (type == SENT && message.getStatus() != Message.STATUS_SEND_FAILED && !activity.xmppConnectionService.getMessageArchiveService().isActive(message.getConversation())) {
                viewHolder.editIndicator.setVisibility(View.VISIBLE);
            } else {
                viewHolder.editIndicator.setVisibility(View.GONE);
            }
        }

        // Potential vulnerability: Ensure that the encryption indicator is correctly set based on message status.
        if (message.getStatus() == Message.STATUS_WAITING && type == SENT) {
            viewHolder.indicator.setImageResource(R.drawable.ic_clock_grey600_24dp);
        } else if (message.getStatus() == Message.STATUS_SEND_RECEIVED || message.getType() != Message.TYPE_CHAT) {
            viewHolder.indicator.setVisibility(View.GONE);
        } else if (type == SENT) {
            if (message.getConversation().getMode() == Conversation.MODE_MULTI && !activity.xmppConnectionService.getMessageArchiveService().isActive(message.getConversation())) {
                viewHolder.indicator.setImageResource(R.drawable.ic_check_black_24dp);
            } else {
                viewHolder.indicator.setVisibility(View.GONE);
            }
        } else if (!darkBackground) {
            // Potential vulnerability: Ensure that the indicator is correctly set based on message status.
            switch (message.getStatus()) {
                case Message.STATUS_RECEIVED:
                    viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
                    break;
                case Message.STATUS_DISPLAYED:
                    viewHolder.indicator.setImageResource(R.drawable.ic_done_all_blue_24dp);
                    break;
                default:
                    viewHolder.indicator.setVisibility(View.GONE);
            }
        } else {
            viewHolder.indicator.setVisibility(View.GONE);
        }

        // Potential vulnerability: Ensure that the message body is correctly set based on message status.
        switch (message.getStatus()) {
            case Message.STATUS_WAITING:
                viewHolder.messageBody.setTextColor(activity.getResources().getColor(R.color.grey600));
                break;
            case Message.STATUS_SEND_FAILED:
                viewHolder.messageBody.setTextColor(activity.getResources().getColor(R.color.red700));
                break;
            default:
                viewHolder.messageBody.setTextColor(darkBackground ? activity.getResources().getColor(R.color.white) : activity.getResources().getColor(R.color.black87));
        }
    }

    private void displayDownloadableMessage(ViewHolder viewHolder, Message message, String info) {
        // Potential vulnerability: Ensure that the input is sanitized to prevent injection attacks.
        viewHolder.messageBody.setText(info);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.downloadButton.setVisibility(View.VISIBLE);
    }

    private void displayOpenableMessage(ViewHolder viewHolder, Message message) {
        // Potential vulnerability: Ensure that the input is sanitized to prevent injection attacks.
        viewHolder.messageBody.setText(UIHelper.getMessagePreview(activity, message).first);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.downloadButton.setVisibility(View.GONE);
    }

    private void displayImageMessage(ViewHolder viewHolder, Message message) {
        File file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (!file.exists()) {
            // Potential vulnerability: Ensure that the user is properly informed of missing files.
            Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
            return;
        }
        viewHolder.messageBody.setText(UIHelper.getMessagePreview(activity,message).first);
        Glide.with(activity)
                .load(file.getAbsolutePath())
                .into(viewHolder.image);
        viewHolder.image.setVisibility(View.VISIBLE);
        viewHolder.downloadButton.setVisibility(View.GONE);
    }

    private void displayLocationMessage(ViewHolder viewHolder, Message message) {
        // Potential vulnerability: Ensure that the location URI is properly validated.
        viewHolder.messageBody.setText(R.string.location_shared);
        viewHolder.image.setImageResource(R.drawable.ic_location_on_black_48dp);
        viewHolder.image.setVisibility(View.VISIBLE);
        viewHolder.downloadButton.setVisibility(View.GONE);
    }

    public void openDownloadable(Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (!file.exists()) {
            // Potential vulnerability: Ensure that the user is properly informed of missing files.
            Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri fileUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", file);

        // Potential vulnerability: Ensure that the intent is correctly configured to prevent security vulnerabilities.
        if (file.getName().endsWith(".jpg") || file.getName().endsWith(".jpeg")) {
            intent.setDataAndType(fileUri, "image/jpeg");
        } else if (file.getName().endsWith(".png")) {
            intent.setDataAndType(fileUri, "image/png");
        } else if (file.getName().endsWith(".mp4")) {
            intent.setDataAndType(fileUri, "video/mp4");
        } else {
            // Potential vulnerability: Ensure that the file type is properly handled.
            String mimeType = URLConnection.guessContentTypeFromName(file.getName());
            if (mimeType == null) {
                mimeType = "*/*";
            }
            intent.setDataAndType(fileUri, mimeType);
        }

        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }

    public void showDetails(Message message) {
        // Potential vulnerability: Ensure that the message details are correctly displayed and do not expose sensitive information.
        ActivityHelper.copyToClipboard(activity, message.getBody());
        Toast.makeText(activity,R.string.copied_to_clipboard,Toast.LENGTH_SHORT).show();
    }

    public void forwardMessage(Message message) {
        // Potential vulnerability: Ensure that the message is properly forwarded without exposing sensitive information.
        Intent intent = new Intent(activity, ShareWithActivity.class);
        intent.putExtra("message", message.getBody());
        activity.startActivity(intent);
    }

    public void resendMessage(Message message) {
        // Potential vulnerability: Ensure that the message is properly resent without exposing sensitive information.
        Conversation conversation = message.getConversation();
        if (conversation != null) {
            activity.xmppConnectionService.resendFailedMessages(conversation);
        }
    }

    private void displayText(ViewHolder viewHolder, Message message) {
        String nick = UIHelper.getMessageDisplayName(message);
        String body = message.getBody();

        // Potential vulnerability: Ensure that the input is sanitized to prevent injection attacks.
        if (nick != null && !nick.isEmpty()) {
            body = nick + ": " + body;
        }
        viewHolder.messageBody.setText(body);

        viewHolder.image.setVisibility(View.GONE);
        viewHolder.downloadButton.setVisibility(View.GONE);
    }

    private void displayFileOrImage(ViewHolder viewHolder, Message message) {
        File file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (!file.exists()) {
            // Potential vulnerability: Ensure that the user is properly informed of missing files.
            Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
            return;
        }

        viewHolder.messageBody.setText(UIHelper.getMessagePreview(activity,message).first);

        Glide.with(activity)
                .load(file.getAbsolutePath())
                .into(viewHolder.image);
        viewHolder.image.setVisibility(View.VISIBLE);
    }

    private void displayLocation(ViewHolder viewHolder) {
        // Potential vulnerability: Ensure that the location URI is properly validated.
        viewHolder.messageBody.setText(R.string.location_shared);
        viewHolder.image.setImageResource(R.drawable.ic_location_on_black_48dp);
        viewHolder.image.setVisibility(View.VISIBLE);
    }

    public void showContactDetails(Contact contact) {
        // Potential vulnerability: Ensure that the contact details are correctly displayed and do not expose sensitive information.
        Intent intent = new Intent(activity, ContactDetailsActivity.class);
        intent.putExtra("contact", contact.getJid().asBareJid());
        activity.startActivity(intent);
    }

    public void updatePreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        mIndicateReceived = preferences.getBoolean("indicate_received", false);
        // Potential vulnerability: Ensure that the preferences are correctly managed and do not expose sensitive information.
        mUseWhiteBackground = preferences.getBoolean("white_background", false);
    }

    public void setOnContactPictureClickedListener(OnClickListener listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(OnLongClickListener listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        Message message = (Message) getItem(position);

        if (convertView == null) {
            viewHolder = new ViewHolder();
            LayoutInflater inflater = activity.getLayoutInflater();

            // Potential vulnerability: Ensure that the layout is correctly inflated and does not expose internal implementation details.
            convertView = inflater.inflate(R.layout.message, parent, false);
            viewHolder.contactPicture = convertView.findViewById(R.id.contact_picture);
            viewHolder.indicatorReceived = convertView.findViewById(R.id.indicator_received);
            viewHolder.time = convertView.findViewById(R.id.time);
            viewHolder.editIndicator = convertView.findViewById(R.id.edit_indicator);
            viewHolder.indicator = convertView.findViewById(R.id.indicator);
            viewHolder.messageBox = convertView.findViewById(R.id.message_box);
            viewHolder.downloadButton = convertView.findViewById(R.id.button_download);
            viewHolder.image = convertView.findViewById(R.id.image);

            if (mOnContactPictureClickedListener != null) {
                viewHolder.contactPicture.setOnClickListener(mOnContactPictureClickedListener);
            }

            if (mOnContactPictureLongClickedListener != null) {
                viewHolder.contactPicture.setOnLongClickListener(mOnContactPictureLongClickedListener);
            }
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        // Potential vulnerability: Ensure that the message is correctly displayed and does not expose sensitive information.
        displayText(viewHolder, message);

        if (message.isFileOrImage()) {
            displayFileOrImage(viewHolder, message);
        } else if (message.getType() == Message.TYPE_LOCATION) {
            displayLocation(viewHolder, message);
        }

        // Potential vulnerability: Ensure that the status is correctly displayed and does not expose sensitive information.
        displayStatus(viewHolder, message, SENT, mUseWhiteBackground);

        convertView.setTag(viewHolder);
        return convertView;
    }

    public void show(Context context, Message message) {
        // Potential vulnerability: Ensure that the message details are correctly displayed and do not expose sensitive information.
        if (message.isFileOrImage()) {
            openDownloadable(message);
        } else if (message.getType() == Message.TYPE_LOCATION) {
            Intent intent = new Intent(context, LocationActivity.class);
            intent.putExtra("uuid", message.getUuid());
            context.startActivity(intent);
        } else {
            showDetails(message);
        }
    }

    public void resend(Message message) {
        // Potential vulnerability: Ensure that the message is properly resent without exposing sensitive information.
        resendMessage(message);
    }

    private static class ViewHolder {
        ImageView contactPicture;
        ImageView indicatorReceived;
        TextView time;
        ImageView editIndicator;
        ImageView indicator;
        View messageBox;
        Button downloadButton;
        ImageView image;
    }
}