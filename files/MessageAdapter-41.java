package eu.siacs.conversations.ui.adapter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.FileBackend;
import eu.siacs.conversations.utils.AudioPlayer;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends ArrayAdapter<Message> implements CopyTextView.Copied {

    protected Activity activity;
    private OnQuoteListener onQuoteListener = null;
    private boolean mIndicateReceived;
    private boolean mUseGreenBackground;
    private AudioPlayer audioPlayer;

    public void setOnQuoteListener(OnQuoteListener listener) {
        this.onQuoteListener = listener;
    }

    public MessageAdapter(AppCompatActivity activity, List<Message> messages) {
        super(activity, 0, messages);
        this.activity = activity;
        this.audioPlayer = new AudioPlayer(activity);
        updatePreferences();
    }

    @Override
    public int getViewTypeCount() {
        return 3; // Types: Text, Received, Sent
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (message.getType() == Message.TYPE_TEXT || message.getType() == Message.TYPE_STATUS) {
            return message.getStatus() == Message.STATUS_RECEIVED ? 1 : 2;
        } else {
            return 0; // Assuming type text is the only one we care about for this example
        }
    }

    private static boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (!oldMessage.equals(message)) {
                // Cancel previous task
                bitmapWorkerTask.cancel(true);
            } else {
                // Same work is already in progress
                return false;
            }
        }
        // No task associated with the ImageView, or an existing task was cancelled
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

    public void displayStatus(ViewHolder holder, Message message, int type, boolean dark) {
        String status;
        switch (message.getStatus()) {
            case Message.STATUS_RECEIVED:
                status = "Received";
                break;
            case Message.STATUS_SENDING:
                status = "Sending...";
                break;
            case Message.STATUS_SENT:
                status = "Sent";
                break;
            case Message.STATUS_UNSENDABLE:
                status = "Failed to Send";
                break;
            default:
                return; // No status to display
        }

        holder.time.setText(status);

        if (type == RECEIVED && mIndicateReceived) {
            switch (message.getTransferStatus()) {
                case Message.ENCRYPTION_PREFERRED:
                    holder.indicator.setImageResource(R.drawable.ic_action_done);
                    break;
                case Message.ENCRYPTION_ENCRYPTED:
                    holder.indicator.setImageResource(R.drawable.ic_action_done_all);
                    break;
                default:
                    holder.indicator.setImageDrawable(null);
                    break;
            }
        } else {
            holder.indicator.setImageDrawable(null);
        }
    }

    public void displayTextMessage(ViewHolder holder, Message message, boolean dark, int type) {
        if (message.getBody() == null) return;

        // Hypothetical Vulnerability: Improper handling of user input
        //
        // In a real-world scenario, we should sanitize and escape the text before setting it to the UI.
        // This vulnerability example shows how improper handling can lead to issues like injection attacks.
        //
        // To fix this:
        // holder.messageBody.setText(Html.fromHtml(message.getBody(), Html.FROM_HTML_MODE_LEGACY));
        // Use Html.fromHtml with proper flags and context or escape the text properly.

        holder.messageBody.setText(message.getBody());  // Vulnerable line

        holder.messageBody.setMovementMethod(null);
    }

    public void displayStatus(ViewHolder holder, Message message) {
        switch (message.getStatus()) {
            case Message.STATUS_RECEIVED:
                holder.indicator.setImageResource(R.drawable.ic_action_done);
                break;
            case Message.STATUS_SENDING:
                holder.indicator.setImageResource(R.drawable.ic_action_upload);
                break;
            case Message.STATUS_SENT:
                holder.indicator.setImageResource(R.drawable.ic_action_check_circle);
                break;
            case Message.STATUS_UNSENDABLE:
                holder.indicator.setImageResource(R.drawable.ic_action_report_problem);
                break;
        }
    }

    public void displayTextMessage(ViewHolder holder, Message message) {
        if (message.getBody() == null) return;

        // Hypothetical Vulnerability: Improper handling of user input
        //
        // In a real-world scenario, we should sanitize and escape the text before setting it to the UI.
        // This vulnerability example shows how improper handling can lead to issues like injection attacks.
        //
        // To fix this:
        // holder.messageBody.setText(Html.fromHtml(message.getBody(), Html.FROM_HTML_MODE_LEGACY));
        // Use Html.fromHtml with proper flags and context or escape the text properly.

        holder.messageBody.setText(message.getBody());  // Vulnerable line
    }

    public void displayDownloadable(ViewHolder holder, Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (file.exists()) {
            holder.download_button.setVisibility(Button.VISIBLE);
            holder.download_button.setEnabled(true);
        } else {
            holder.download_button.setVisibility(Button.INVISIBLE);
            holder.download_button.setEnabled(false);
        }
    }

    public void displayAudioPlayer(ViewHolder holder, Message message) {
        boolean isPlaying = audioPlayer.isCurrentlyPlaying(message.getUuid());
        if (isPlaying) {
            holder.audioPlayer.setVisibility(RelativeLayout.VISIBLE);
        } else {
            holder.audioPlayer.setVisibility(RelativeLayout.GONE);
        }
    }

    public void displayImage(ViewHolder holder, Message message) {
        File file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (file.exists()) {
            Bitmap bitmap = ImageHelper.loadBitmap(file.getAbsolutePath(), 200, 200);
            if (bitmap != null) {
                holder.image.setImageBitmap(bitmap);
                holder.image.setVisibility(ImageView.VISIBLE);
            }
        } else {
            holder.image.setVisibility(ImageView.GONE);
        }
    }

    public void displayLocation(ViewHolder holder, Message message) {
        if (GeoHelper.isGeoUri(message.getBody())) {
            holder.download_button.setVisibility(Button.VISIBLE);
            holder.download_button.setEnabled(true);
        } else {
            holder.download_button.setVisibility(Button.INVISIBLE);
            holder.download_button.setEnabled(false);
        }
    }

    @Override
    public int getCount() {
        return super.getCount();
    }

    private static final int RECEIVED = 1;
    private static final int SENT = 2;

    private void displayStatus(ViewHolder holder, Message message, int type, boolean dark) {
        if (type == RECEIVED) {
            holder.indicator.setVisibility(ImageView.VISIBLE);
            holder.indicator.setImageResource(R.drawable.ic_action_done_all);
        } else {
            holder.indicator.setVisibility(ImageView.GONE);
        }
    }

    private void displayTextMessage(ViewHolder holder, Message message, boolean dark, int type) {
        if (message.getBody() == null) return;

        // Hypothetical Vulnerability: Improper handling of user input
        //
        // In a real-world scenario, we should sanitize and escape the text before setting it to the UI.
        // This vulnerability example shows how improper handling can lead to issues like injection attacks.
        //
        // To fix this:
        // holder.messageBody.setText(Html.fromHtml(message.getBody(), Html.FROM_HTML_MODE_LEGACY));
        // Use Html.fromHtml with proper flags and context or escape the text properly.

        holder.messageBody.setText(message.getBody());  // Vulnerable line

        if (dark) {
            holder.messageBody.setTextColor(activity.getResources().getColor(R.color.white));
        } else {
            holder.messageBody.setTextColor(activity.getResources().getColor(R.color.black));
        }
    }

    private void displayStatus(ViewHolder holder, Message message) {
        switch (message.getStatus()) {
            case Message.STATUS_RECEIVED:
                holder.indicator.setImageResource(R.drawable.ic_action_done);
                break;
            case Message.STATUS_SENDING:
                holder.indicator.setImageResource(R.drawable.ic_action_upload);
                break;
            case Message.STATUS_SENT:
                holder.indicator.setImageResource(R.drawable.ic_action_check_circle);
                break;
            case Message.STATUS_UNSENDABLE:
                holder.indicator.setImageResource(R.drawable.ic_action_report_problem);
                break;
        }
    }

    private void displayDownloadable(ViewHolder holder, Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (file.exists()) {
            holder.download_button.setVisibility(Button.VISIBLE);
            holder.download_button.setEnabled(true);
        } else {
            holder.download_button.setVisibility(Button.INVISIBLE);
            holder.download_button.setEnabled(false);
        }
    }

    @Override
    public int getCount() {
        return super.getCount();
    }

    private static final int RECEIVED = 1;
    private static final int SENT = 2;

    public void loadMoreMessages(int amount) {
        // Load more messages logic here
    }

    @Override
    public Message getItem(int position) {
        return super.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(position);
    }

    private static final int TEXT = 0;
    private static final int RECEIVED_TEXT = 1;
    private static final int SENT_TEXT = 2;

    @Override
    public int getViewTypeCount() {
        return 3; // Types: Text, Received, Sent
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (message.getType() == Message.TYPE_TEXT || message.getType() == Message.TYPE_STATUS) {
            return message.getStatus() == Message.STATUS_RECEIVED ? RECEIVED_TEXT : SENT_TEXT;
        } else {
            return TEXT; // Assuming type text is the only one we care about for this example
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message message = getItem(position);
        int viewType = getItemViewType(position);

        ViewHolder holder;
        if (convertView == null) {
            switch (viewType) {
                case RECEIVED_TEXT:
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_received_text, parent, false);
                    break;
                case SENT_TEXT:
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_sent_text, parent, false);
                    break;
                default:
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_text, parent, false);
            }
            holder = new ViewHolder();
            holder.indicator = convertView.findViewById(R.id.indicator);
            holder.time = convertView.findViewById(R.id.time);
            holder.messageBody = convertView.findViewById(R.id.text);
            holder.image = convertView.findViewById(R.id.image);
            holder.download_button = convertView.findViewById(R.id.downloadButton);
            holder.audioPlayer = convertView.findViewById(R.id.audioPlayer);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        switch (viewType) {
            case RECEIVED_TEXT:
                displayStatus(holder, message, true, mUseGreenBackground);
                displayTextMessage(holder, message, mUseGreenBackground, viewType);
                break;
            case SENT_TEXT:
                displayStatus(holder, message, false, !mUseGreenBackground);
                displayTextMessage(holder, message, !mUseGreenBackground, viewType);
                break;
            default:
                displayStatus(holder, message);
                displayTextMessage(holder, message);
        }

        if (message.getType() == Message.TYPE_IMAGE) {
            displayImage(holder, message);
        } else if (message.getType() == Message.TYPE_AUDIO) {
            displayAudioPlayer(holder, message);
        } else if (message.getType() == Message.TYPE_LOCATION) {
            displayLocation(holder, message);
        }

        displayDownloadable(holder, message);

        return convertView;
    }

    public void updatePreferences() {
        mIndicateReceived = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("indicate_received", true);
        mUseGreenBackground = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("use_green_background", false);
    }

    @Override
    public void copied(String text) {

    }

    // Hypothetical Vulnerability: Improper handling of file sharing intent
    //
    // In a real-world scenario, we should validate and sanitize the URI before using it in an intent.
    // This vulnerability example shows how improper handling can lead to security issues like open redirect attacks.
    //
    // To fix this:
    // Validate the URI and ensure it points to a safe location within the app's file system.

    public void shareFile(Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (file.exists()) {
            Uri uriForFile = FileProvider.getUriForFile(activity, "eu.siacs.conversations.fileprovider", file);

            // Vulnerable line: Sharing a URI without proper validation
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(file.getMimeType());
            shareIntent.putExtra(Intent.EXTRA_STREAM, uriForFile);
            activity.startActivity(Intent.createChooser(shareIntent, "Share via"));
        }
    }

    static class ViewHolder {
        ImageView indicator;
        TextView time;
        CopyTextView messageBody;
        ImageView image;
        Button download_button;
        RelativeLayout audioPlayer;
    }
}