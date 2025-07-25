package eu.siacs.conversations.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Point;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.BarcodeProvider;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.SettingsActivity;
import eu.siacs.conversations.utils.ThemeHelper;
import rocks.xmpp.addr.Jid;

public class XmppActivity extends AppCompatActivity {

    public static final String EXTRA_ACCOUNT = "account";
    protected Toast mToast;
    protected int mColorRed;
    protected DisplayMetrics metrics;
    private BitmapWorkerTask mCurrentTask;
    private ConferenceInvite mPendingConferenceInvite;
    protected boolean mActivityRecreatedWhilePaused;
    protected boolean mDoNotHighlightNextMessage;
    protected boolean mOverrideUnread = false;

    @SuppressLint("StaticFieldLeak")
    private static XmppActivity activityReference = null; // Static reference for demonstration of a potential memory leak

    protected WeakReference<XmppConnectionService> xmppConnectionService = new WeakReference<>(null);
    protected OnBind onXmppConnectionBound;
    private boolean mBind;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the theme before setting the content view
        setTheme(findTheme());
        metrics = getResources().getDisplayMetrics();
        setContentView(R.layout.activity_main); // Assuming this is the main layout
        activityReference = this; // Setting a static reference to demonstrate potential memory leak

        mColorRed = ContextCompat.getColor(this, R.color.red);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mBind) {
            bindService(new Intent(this, XmppConnectionService.class), connection, BIND_AUTO_CREATE);
        }
    }

    // Vulnerability Explanation:
    // The static reference to the activity (activityReference) can cause a memory leak.
    // If the activity is destroyed but this static reference still points to it, the garbage collector
    // cannot reclaim the memory used by the activity because there's an active reference.

    @Override
    protected void onStop() {
        super.onStop();
        if (mBind && xmppConnectionService.get() != null) {
            unbindService(connection);
        }
        if (mToast != null) {
            mToast.cancel();
        }
        // Clearing the static reference when activity stops to prevent memory leak
        activityReference = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPotentialWork(null, null);
    }

    private boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message bitmapMessage = bitmapWorkerTask.message;
            // If bitmapMessage and message are not the same, then this is a different image. Cancel previous task.
            if ((message == null || bitmapMessage == null || !bitmapMessage.equals(message))) {
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already being done
                return false;
            }
        }
        // No task associated with the ImageView, or an existing task was cancelled
        return true;
    }

    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    // Hypothetical Vulnerability: Improper input validation and sanitization in handling shareable URIs
    // This method constructs a URI that could potentially be constructed from user inputs or external sources.
    // If the URI is not properly sanitized, it might lead to injection attacks or other security issues.

    protected String getShareableUri() {
        return getShareableUri(false);
    }

    protected String getShareableUri(boolean http) {
        // Example vulnerable code: Concatenating user input without sanitization
        Account account = extractAccount(getIntent());
        if (account != null && account.getServer().getLocalpartOrDomain() != null) {
            String serverName = account.getServer().getLocalpartOrDomain();
            return (http ? "http://" : "") + serverName + "/share?token=" + getTokenFromUserInput(); // Vulnerable line
        }
        return null;
    }

    private String getTokenFromUserInput() {
        // Simulate getting a token from user input which might not be sanitized
        Intent intent = getIntent();
        return intent.getStringExtra("user_input_token"); // User-provided token without sanitization
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_share_uri) {
            shareLink(false);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected void shareLink(boolean http) {
        String uri = getShareableUri(http);
        if (uri == null || uri.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, uri);

        try {
            startActivity(Intent.createChooser(intent, getText(R.string.share_uri_with)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_application_to_share_uri, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, you can proceed with the action that requires permission
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    public static class ConferenceInvite {
        private String uuid;
        private List<Jid> jids = new ArrayList<>();

        public static ConferenceInvite parse(Intent data) {
            ConferenceInvite invite = new ConferenceInvite();
            invite.uuid = data.getStringExtra(ChooseContactActivity.EXTRA_CONVERSATION);
            if (invite.uuid == null) {
                return null;
            }
            invite.jids.addAll(ChooseContactActivity.extractJabberIds(data));
            return invite;
        }

        public boolean execute(XmppActivity activity) {
            XmppConnectionService service = activity.xmppConnectionService.get();
            Conversation conversation = service.findConversationByUuid(this.uuid);
            if (conversation == null) {
                return false;
            }
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                for (Jid jid : jids) {
                    service.invite(conversation, jid);
                }
                return false;
            } else {
                jids.add(conversation.getJid().asBareJid());
                return service.createAdhocConference(conversation.getAccount(), null, jids, activity.adhocCallback);
            }
        }
    }

    static class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private final WeakReference<XmppActivity> activity;
        private Message message = null;

        private BitmapWorkerTask(XmppActivity activity, ImageView imageView) {
            this.activity = new WeakReference<>(activity);
            this.imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            if (isCancelled()) {
                return null;
            }
            message = params[0];
            try {
                XmppActivity activity = this.activity.get();
                if (activity != null && activity.xmppConnectionService.get() != null) {
                    return activity.xmppConnectionService.get().getFileBackend().getThumbnail(message, (int) (activity.metrics.density * 288), false);
                } else {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
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

    static class AsyncDrawable extends BitmapDrawable {

        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }
}