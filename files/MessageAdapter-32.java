package eu.siacs.conversations.ui.adapter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.jxmpp.stringprep.XmppStringprepException;

import java.io.File;
import java.lang.ref.WeakReference;
import java.security.SecurityException;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.DownloadConnection;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.ListSelectionManager;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.ZipHelper;
import eu.siacs.conversations.xmpp.jid.Jid;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> implements CopyTextView.CopiedTextCallback {

    private final Activity activity;
    private final List<Message> messages;
    private boolean mIndicateReceived = true;
    private boolean mUseGreenBackground = false;
    private OnQuoteListener onQuoteListener;
    private ListSelectionManager listSelectionManager;

    // Vulnerability: The avatar loading mechanism here does not include any validation of the image source.
    // Potential fix: Validate the image source to ensure it's from a trusted source or use a safe method for loading images.

    public MessageAdapter(Activity activity, List<Message> messages) {
        this.activity = activity;
        this.messages = messages;
        updatePreferences();
        listSelectionManager = new ListSelectionManager(activity);
    }

    private void displayStatus(ViewHolder viewHolder, final Message message, int type, boolean darkBackground, boolean indicateEncryption) {
        if (mIndicateReceived && message.getStatus() == Message.STATUS_RECEIVED) {
            viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
        } else {
            viewHolder.indicatorReceived.setVisibility(View.GONE);
        }
    }

    private void displayQuote(ViewHolder viewHolder, final Message message) {
        // Implementation for displaying quote
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).getUuid().hashCode();
    }

    private void displayStatusIcon(ViewHolder viewHolder, final Message message, boolean darkBackground) {
        // Implementation for displaying status icon
    }

    private void setupCopyButton(ViewHolder holder, final Message message) {
        // Implementation for setting up copy button
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int position) {
        final Message message = messages.get(position);

        loadAvatar(message, viewHolder.contact_picture); // Potential vulnerability

        if (message.getType() == Message.TYPE_IMAGE) {
            setupImage(viewHolder.image, message);
        } else {
            viewHolder.image.setVisibility(View.GONE);
            displayQuote(viewHolder, message);
            setupCopyButton(viewHolder, message);
        }

        viewHolder.time.setText(UIHelper.readableTimeDifference(activity, message.getTimeSent()));
    }

    private void setupImage(ImageView imageView, Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);

        if (file.exists()) {
            Bitmap bm = ImageUtils.decodeSampledBitmapFromFile(file.getAbsolutePath(), 1024, 1024);
            imageView.setImageBitmap(bm);
        } else {
            imageView.setImageResource(R.drawable.ic_image_loading);
            DownloadConnection downloadConnection = new DownloadConnection(activity, message) {
                @Override
                public void onStatusChanged(DownloadableFile.Status status) {
                    if (status == DownloadableFile.Status.DONE) {
                        Bitmap bm = ImageUtils.decodeSampledBitmapFromFile(getFile().getAbsolutePath(), 1024, 1024);
                        imageView.setImageBitmap(bm);
                    } else if (status == DownloadableFile.Status.ERROR || status == DownloadableFile.Status.CONFLICT) {
                        imageView.setImageResource(R.drawable.ic_image_broken);
                    }
                }
            };
            downloadConnection.reset(message);
        }
    }

    // Vulnerability: The avatar loading mechanism here does not include any validation of the image source.
    // Potential fix: Validate the image source to ensure it's from a trusted source or use a safe method for loading images.

    public void loadAvatar(Message message, ImageView imageView) {
        if (cancelPotentialWork(message, imageView)) {
            final Bitmap bm = activity.avatarService().get(message, activity.getPixel(48), true);
            if (bm != null) {
                cancelPotentialWork(message, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(UIHelper.getColorForName(UIHelper.getMessageDisplayName(message)));
                imageView.setImageDrawable(null);

                // Potential vulnerability: Loading avatar from an untrusted source without validation
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);

                try {
                    task.execute(message);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    private static class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Message message = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            return activity.avatarService().get(params[0], activity.getPixel(48), isCancelled());
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && !isCancelled()) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }

    private static boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (oldMessage == null || message != oldMessage) {
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
}