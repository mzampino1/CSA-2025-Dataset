import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

public class XmppActivity extends Activity {

    private DisplayMetrics metrics = new DisplayMetrics();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
    }

    public void onMessageReceived(Message message) {
        // Insecure execution of user input vulnerability
        // Assume this method directly processes and displays the message content without validation.
        displayMessage(message.getContent());  // Vulnerable line

        // Secure way to handle messages should involve sanitizing or validating the content first
        // String sanitizedContent = sanitizeInput(message.getContent());
        // displayMessage(sanitizedContent);
    }

    private void displayMessage(String content) {
        // Code to display message on UI (UI thread)
        Log.d("XmppActivity", "Displaying message: " + content);
    }

    private String sanitizeInput(String input) {
        // Basic example of sanitizing input by escaping HTML
        return android.text.Html.escapeHtml(input).toString();
    }

    public void quickEdit(final Message message, final OnValueEdited callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.quickedit, null);
        final EditText editor = (EditText) view.findViewById(R.id.editor);

        OnClickListener mClickListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String value = editor.getText().toString();
                if (!message.getContent().equals(value) && value.trim().length() > 0) {
                    callback.onValueEdited(value);
                }
            }
        };

        builder.setPositiveButton(R.string.edit, mClickListener);
        editor.requestFocus();
        editor.setText(message.getContent());
        builder.setView(view);
        builder.setNegativeButton(R.string.cancel, null);
        builder.create().show();
    }

    // Placeholder for other methods in the class...

    public interface OnValueEdited {
        void onValueEdited(String value);
    }

    /**
     * BitmapWorkerTask to handle background loading of images.
     */
    private static class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Message message;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            message = params[0];
            try {
                return xmppConnectionService.getFileBackend().getThumbnail(
                        message, (int) (metrics.density * 288), false);
            } catch (FileNotFoundException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }

    public void loadBitmap(Message message, ImageView imageView) {
        Bitmap bm;
        try {
            bm = xmppConnectionService.getFileBackend().getThumbnail(message,
                    (int) (metrics.density * 288), true);
        } catch (FileNotFoundException e) {
            bm = null;
        }
        if (bm != null) {
            imageView.setImageBitmap(bm);
            imageView.setBackgroundColor(0x00000000);
        } else {
            if (cancelPotentialWork(message, imageView)) {
                imageView.setBackgroundColor(0xff333333);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(
                        getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(message);
                } catch (RejectedExecutionException e) {
                    return;
                }
            }
        }
    }

    public static boolean cancelPotentialWork(Message message, ImageView imageView) {
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