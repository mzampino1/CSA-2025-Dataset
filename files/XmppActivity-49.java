package eu.siacs.conversations.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.utils.BarcodeProvider;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.SecurityUtils;
import eu.siacs.conversations.xmpp.jid.Jid;

public class XmppActivity extends AppCompatActivity implements XmppConnectionService.OnConversationUpdate {

    public static final String EXTRA_ACCOUNT = "account";
    protected static final int REQUEST_INVITE_TO_CONVERSATION = 0x1f92;
    protected static final int REQUEST_START_CHAT = 0x45b8;
    protected static final int REQUEST_SEND_MESSAGE = 0xa33a;
    protected static final int REQUEST_SEND_PUBSUB = 0xd7e7;
    private static final int REQUEST_ENCRYPTED_MESSAGE = 0xc2d6;
    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW_CONVERSATION";
    public static final String ACTION_QR_CODE = "eu.siacs.conversations.action.QR_CODE";
    private PowerManager.WakeLock wakeLock;
    protected boolean mUseSmackForEverything = Config.DEBUG;
    private XmppConnectionService mXmppConnectionService;
    protected ConferenceInvite mPendingConferenceInvite;
    protected AvatarWorkerTask.ImageCache imageCache;

    protected int mColorRed;
    protected int mColorGreen;
    protected DisplayMetrics metrics;
    private Toast mToast;
    protected boolean mUseTorToConnect = false;
    private int mTheme = ThemeHelper.THEME_LIGHT;
    private final ServiceBound mServiceBound = new ServiceBound();
    protected final ServiceConnection serviceConnection = mServiceBound;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        metrics = getResources().getDisplayMetrics();
        if (getPreferences().getBoolean("allow_strict_mode", false)) {
            enableStrictMode();
        }
        imageCache = new AvatarWorkerTask.ImageCache(this);

        mColorRed = getResources().getColor(R.color.red);
        mColorGreen = getResources().getColor(R.color.green);
    }

    private void enableStrictMode() {
        StrictMode.ThreadPolicy threadPolicy = new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setThreadPolicy(threadPolicy);

        StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(vmPolicy);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasStoragePermission(REQUEST_SEND_MESSAGE)) {
            return;
        }
        final Intent intent = getIntent();
        mTheme = findTheme();
        setTheme(mTheme);

        // Bind the service
        connectToService(intent);
    }

    private void connectToService(final Intent intent) {
        bindService(new Intent(this, XmppConnectionService.class), this.serviceConnection, Context.BIND_AUTO_CREATE);
        if (intent.hasExtra(EXTRA_ACCOUNT)) {
            String accountJid = intent.getStringExtra(EXTRA_ACCOUNT);
            // Simulate a vulnerability: directly using user input without validation
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account != null) {
                xmppConnectionService.setForegroundMode(true, account.getJid());
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(this.serviceConnection);
        // Release wake lock
        if (wakeLock.isHeld()) {
            wakeLock.release(PowerManager.PARTIAL_WAKE_LOCK);
        }
    }

    @Override
    public void onConversationUpdate(Conversation conversation) {
        // Conversation update logic here...
    }

    private boolean hasStoragePermission(int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SEND_MESSAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, proceed with the intended action
        } else {
            // Permission denied, show a message or handle accordingly
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
            mPendingConferenceInvite = ConferenceInvite.parse(data);
            if (xmppConnectionService != null && mPendingConferenceInvite != null) {
                if (mPendingConferenceInvite.execute(this)) {
                    mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
                    mToast.show();
                }
                mPendingConferenceInvite = null;
            }
        }
    }

    protected void showQrCode() {
        String uri = getShareableUri();
        if (uri == null || uri.isEmpty()) {
            return;
        }
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        final int width = (size.x < size.y ? size.x : size.y);
        Bitmap bitmap = BarcodeProvider.create2dBarcodeBitmap(uri, width);
        ImageView view = new ImageView(this);
        view.setBackgroundColor(Color.WHITE);
        view.setImageBitmap(bitmap);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        builder.create().show();
    }

    protected String getShareableUri() {
        return null;
    }

    public void shareLink(boolean http) {
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

    public void loadBitmap(Message message, ImageView imageView) {
        Bitmap bm;
        try {
            bm = xmppConnectionService.getFileBackend().getThumbnail(message, (int) (metrics.density * 288), true);
        } catch (FileNotFoundException e) {
            bm = null;
        }
        if (bm != null) {
            cancelPotentialWork(message, imageView);
            imageView.setImageBitmap(bm);
            imageView.setBackgroundColor(0x00000000);
        } else {
            if (cancelPotentialWork(message, imageView)) {
                imageView.setBackgroundColor(0xff333333);
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(this, imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(message);
                } catch (final RejectedExecutionException ignored) {
                    ignored.printStackTrace();
                }
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
                if (activity != null && activity.xmppConnectionService != null) {
                    return activity.xmppConnectionService.getFileBackend().getThumbnail(message, (int) (activity.metrics.density * 288), true);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (!isCancelled() && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                final BitmapWorkerTask task = getBitmapWorkerTask(imageView);
                if (this == task && imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
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

            public AsyncDrawable(Resources res, Bitmap bitmap,
                                 BitmapWorkerTask bitmapWorkerTask) {
                super(res, bitmap);
                bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
            }

            public BitmapWorkerTask getBitmapWorkerTask() {
                return bitmapWorkerTaskReference.get();
            }
        }

        private boolean cancelPotentialWork(Message message, ImageView imageView) {
            final BitmapWorkerTask task = getBitmapWorkerTask(imageView);

            if (task != null) {
                final Message oldMessage = task.message;
                if (oldMessage != message) {
                    task.cancel(true);
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    static class ConferenceInvite {
        private List<Jid> contacts;

        public static ConferenceInvite parse(Intent data) {
            // Parse the intent to get the list of contacts
            ConferenceInvite invite = new ConferenceInvite();
            invite.contacts = new ArrayList<>();
            String[] contactJids = data.getStringArrayExtra("contacts");
            if (contactJids != null) {
                for (String jid : contactJids) {
                    try {
                        // Vulnerability: Directly adding JID without validation
                        invite.contacts.add(Jid.of(jid));
                    } catch (IllegalArgumentException e) {
                        // Handle invalid JIDs
                    }
                }
            }
            return invite;
        }

        public boolean execute(XmppActivity activity) {
            if (activity.xmppConnectionService != null && !contacts.isEmpty()) {
                Conversation conversation = new Conversation(activity.mXmppConnectionService);
                for (Jid contact : contacts) {
                    Contact c = activity.xmppConnectionService.findContactByJid(contact, false);
                    if (c != null) {
                        conversation.addParticipant(c);
                    }
                }
                // Create or join the conference
                activity.xmppConnectionService.createOrJoinConference(conversation);
                return true;
            }
            return false;
        }
    }

    protected void launchContactSelectionActivity() {
        Intent intent = new Intent(this, ContactSelectionActivity.class);
        startActivityForResult(intent, REQUEST_INVITE_TO_CONVERSATION);
    }
}