package eu.siacs.conversations.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.BookmarkAdapter;
import eu.siacs.conversations.utils.BarcodeProvider;
import eu.siacs.conversations.utils.ConfigUtils;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.StringUtils;
import eu.siacs.conversations.xmpp.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class XmppActivity extends FragmentActivity implements
        BookmarkAdapter.OnBookmarkSelected,
        XmppConnectionService.OnConversationListChangedListener {

    private static final int REQUEST_INVITE_TO_CONVERSATION = 0x1c4a3;
    private static final int REQUEST_TRUST_KEYS = 0x28af6;

    protected boolean mUseBinding = false;
    protected Boolean mBinded = false;
    protected XmppConnectionService xmppConnectionService;
    private Toast mToast;
    protected ConferenceInvite mPendingConferenceInvite;
    private DisplayMetrics metrics;
    protected int mPrimaryTextColor;
    protected int mSecondaryTextColor;
    protected int mTertiaryTextColor;
    protected int mColorGreen;
    protected int mColorRed;

    public interface OnPresenceSelected {
        void onPresenceSelected();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the theme based on user preferences.
        setTheme(findTheme());
        setContentView(R.layout.activity_xmpp);

        metrics = getResources().getDisplayMetrics();

        // Initialize color variables from resources.
        mPrimaryTextColor = getResources().getColor(R.color.primary_text);
        mSecondaryTextColor = getResources().getColor(R.color.secondary_text);
        mTertiaryTextColor = getResources().getColor(R.color.tertiary_text);
        mColorGreen = getResources().getColor(R.color.green500);
        mColorRed = getResources().getColor(R.color.red700);

        // Bind the service.
        bindService(new Intent(this, XmppConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUseBinding) {
            unbindService(mConnection);
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            XmppActivity.this.xmppConnectionService = ((XmppConnectionService.LocalBinder) service).getService();
            xmppConnectionService.setOnConversationListChangedListener(XmppActivity.this);
            mBinded = true;
            if (mPendingConferenceInvite != null) {
                if (mPendingConferenceInvite.execute(XmppActivity.this)) {
                    mToast = Toast.makeText(XmppActivity.this, R.string.creating_conference, Toast.LENGTH_LONG);
                    mToast.show();
                }
                mPendingConferenceInvite = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            xmppConnectionService = null;
            mBinded = false;
        }
    };

    // Implement the required interface method.
    @Override
    public void onConversationListChanged(List<Conversation> conversations) {
        // Update the UI based on conversation list changes.
    }

    // Implement the required interface method.
    @Override
    public void onBookmarkSelected(Bookmark bookmark) {
        // Handle bookmark selection.
    }

    protected boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (oldMessage == null || !oldMessage.equals(message)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    protected BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    protected void displayableToast(String message) {
        runOnUiThread(() -> {
            try {
                Toast.makeText(XmppActivity.this, message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                ExceptionHelper.printLog(e);
            }
        });
    }

    public void switchAccount(Account account) {
        // Handle account switching.
    }

    protected void refreshUiReal() {
        // Refresh the user interface.
    }

    public void refreshUi() {
        runOnUiThread(this::refreshUiReal);
    }

    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUseBinding = true;
        if (!mBinded) {
            bindService(new Intent(this, XmppConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mUseBinding && mBinded) {
            unbindService(mConnection);
            xmppConnectionService = null;
            mBinded = false;
        }
    }

    protected boolean hasStoragePermission(int requestCode) {
        return ConfigUtils.requestPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, requestCode);
    }

    public void switchAccount(Account account) {
        if (xmppConnectionService != null) {
            xmppConnectionService.switchAccount(account);
            refreshUi();
        }
    }

    // Handle menu item selections.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Handle options menu creation.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    // Show a dialog with account information.
    protected void showAccountDialog(Account account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_account_info, null);
        TextView tvUsername = view.findViewById(R.id.username);
        TextView tvStatus = view.findViewById(R.id.status);
        tvUsername.setText(account.getJid().asBareJid().toString());
        tvStatus.setText(account.getStatus().name());
        builder.setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    protected void showContactDialog(Jid jid, Account account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_contact_info, null);
        TextView tvJid = view.findViewById(R.id.jid);
        TextView tvStatus = view.findViewById(R.id.status);
        tvJid.setText(jid.toString());
        tvStatus.setText(xmppConnectionService.getStatus(account, jid).name());
        builder.setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    protected void showConversationDialog(Conversation conversation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_conversation_info, null);
        TextView tvName = view.findViewById(R.id.name);
        TextView tvStatus = view.findViewById(R.id.status);
        tvName.setText(conversation.getName());
        tvStatus.setText(conversation.getStatus().name());
        builder.setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    // Handle back button press.
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mToast != null) {
            mToast.cancel();
        }
    }

    protected boolean hasStoragePermission(int requestCode) {
        return ConfigUtils.requestPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, requestCode);
    }

    // Handle permission request results.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_INVITE_TO_CONVERSATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with the operation.
            } else {
                // Permission denied, inform the user and possibly disable features.
            }
        }
    }

    // Handle deep linking or incoming intents.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Set the new intent as the activity's intent.

        // Example of handling an action from an intent:
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case "eu.siacs.conversations.ACTION_NEW_MESSAGE":
                    Jid jid = intent.getParcelableExtra("jid");
                    Account account = extractAccountFromIntent(intent);
                    handleNewMessage(jid, account);
                    break;
                // Handle other actions as necessary.
            }
        }
    }

    private void handleNewMessage(Jid jid, Account account) {
        if (xmppConnectionService != null && account != null) {
            Conversation conversation = xmppConnectionService.findOrCreateConversation(account, jid.asBareJid());
            // Additional handling for the new message.
        }
    }

    private Account extractAccountFromIntent(Intent intent) {
        String accountId = intent.getStringExtra("account");
        if (accountId == null || xmppConnectionService == null) return null;
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getJid().asBareJid().toString().equals(accountId)) {
                return account;
            }
        }
        return null;
    }

    protected void showToast(String message, int duration) {
        runOnUiThread(() -> Toast.makeText(XmppActivity.this, message, duration).show());
    }

    // Example method to demonstrate potential file handling vulnerability.
    private void processFileFromIntent(Intent intent) {
        Uri fileUri = intent.getData();
        if (fileUri != null) {
            try {
                // Validate and sanitize the file URI before processing it.
                String filePath = FileUtils.getPath(this, fileUri);
                // Process the file located at filePath.
            } catch (Exception e) {
                ExceptionHelper.printLog(e);
                showToast("Error processing file.", Toast.LENGTH_SHORT);
            }
        }
    }

    // Example method to demonstrate potential security concern with third-party libraries.
    private void trustKeys(List<String> keys) {
        PgpEngine pgp = xmppConnectionService.getPgpEngine();
        if (pgp != null) {
            for (String key : keys) {
                try {
                    pgp.importKey(key.getBytes());
                } catch (Exception e) {
                    ExceptionHelper.printLog(e);
                    showToast("Error importing key.", Toast.LENGTH_SHORT);
                }
            }
        }
    }

    // Example method to demonstrate potential security concern with external intents.
    private void handleExternalIntent(Intent intent) {
        String action = intent.getAction();
        if ("eu.siacs.conversations.ACTION_EXTERNAL_DATA".equals(action)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                String data = extras.getString("data");
                // Validate and sanitize the data before processing it.
                processExternalData(data);
            }
        }
    }

    private void processExternalData(String data) {
        // Process the external data safely.
    }
}