package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.BundleUtils;
import eu.siacs.conversations.xml.Element;

public class XmppActivity extends AppCompatActivity {

    public static final String ACTION_SHOW_CONVERSATION = "SHOW_CONVERSATION";
    protected static final int REQUEST_INVITE_TO_CONVERSATION = 0x1234;

    private boolean mOverrideXmppConnectionService = false;
    protected XmppConnectionService xmppConnectionService;
    protected DatabaseBackend dbBackend;
    public int mPrimaryTextColor = getResources().getColor(R.color.black);
    public int mSecondaryTextColor = getResources().getColor(R.color.grey);
    public int mColorRed = getResources().getColor(R.color.red);
    public int mPrimaryColor = getResources().getColor(R.color.primary_color);
    public int mSecondaryBackgroundColor = getResources().getColor(R.color.secondary_background_color);
    private DisplayMetrics metrics;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize display metrics for layout adjustments.
        metrics = getResources().getDisplayMetrics();
        
        // Check and request necessary permissions (e.g., NFC) here if needed.
        // For example, check if the user has granted NFC permission before registering callbacks.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent();
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (ACTION_SHOW_CONVERSATION.equals(action)) {
            // Handle the show conversation action
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        xmppConnectionServiceBound(null);
    }

    private void bindXmppService() {
        mOverrideXmppConnectionService = true;
        rebindServiceIfNecessary();
    }

    protected void onBackendConnected() {}

    protected void xmppConnectionServiceBound(XmppConnectionService service) {
        if (service != null) {
            xmppConnectionService = service;
            dbBackend = service.databaseBackend;
            onBackendConnected();
        }
    }

    public boolean shouldReconnectOnWIFIOnlyChanged() {
        return false;
    }

    protected void rebindServiceIfNecessary() {}

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mOverrideXmppConnectionService) {
            unbindService();
        }
        super.onDestroy();
    }

    private void unbindService() {
        // Unbind the service here
    }

    public void switchToConversation(Conversation conversation, int initMode, String text) {}

    public void switchToContact(Contact contact) {}

    protected void refreshUiReal() {
        invalidateOptionsMenu();
        setTitle(getTitle());
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
    }

    public boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String label = getResources().getString(labelResId);
        if (clipboard != null) {
            ClipData clipData = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clipData);
            return true;
        }
        return false;
    }

    protected void registerNdefPushMessageCallback() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Check if NFC is enabled and available
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            // Ensure that the necessary permissions are granted before registering callbacks.
            // For example, check for android.permission.NFC in the manifest and request at runtime if needed.

            nfcAdapter.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
                @Override
                public NdefMessage createNdefMessage(NfcEvent event) {
                    return new NdefMessage(new NdefRecord[]{
                            NdefRecord.createUri(getShareableUri()),
                            NdefRecord.createApplicationRecord("eu.siacs.conversations")
                    });
                }
            }, this);
        } else {
            Log.w(Config.LOGTAG, "NFC is not enabled or available.");
        }
    }

    protected void unregisterNdefPushMessageCallback() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Check if NFC is enabled and available
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            // Ensure that the necessary permissions are granted before unregistering callbacks.
            // For example, check for android.permission.NFC in the manifest and request at runtime if needed.

            nfcAdapter.setNdefPushMessageCallback(null, this);
        } else {
            Log.w(Config.LOGTAG, "NFC is not enabled or available.");
        }
    }

    protected String getShareableUri() {
        // Return a URI that can be shared
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getShareableUri() != null) {
            registerNdefPushMessageCallback();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterNdefPushMessageCallback();
    }

    protected int findTheme() {
        if (getPreferences().getBoolean("use_larger_font", false)) {
            return R.style.ConversationsTheme_LargerText;
        } else {
            return R.style.ConversationsTheme;
        }
    }

    public void showQrCode() {
        String uri = getShareableUri();
        if (uri != null) {
            Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            final int width = (size.x < size.y ? size.x : size.y);
            Bitmap bitmap = createQrCodeBitmap(uri, width);
            ImageView view = new ImageView(this);
            view.setImageBitmap(bitmap);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(view);
            builder.create().show();
        }
    }

    protected Bitmap createQrCodeBitmap(String input, int size) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix bitMatrix = qrCodeWriter.encode(input, BarcodeFormat.QR_CODE, size, size, hints);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.TRANSPARENT;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException e) {
            Log.e(Config.LOGTAG, "Failed to generate QR code", e);
            return null;
        }
    }

    public AvatarService avatarService() {
        return xmppConnectionService.getAvatarService();
    }

    class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Message message = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            message = params[0];
            try {
                return xmppConnectionService.getFileBackend().getThumbnail(message, (int) (metrics.density * 288), this);
            } catch (FileNotFoundException e) {
                Log.e(Config.LOGTAG, "Failed to load thumbnail", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && imageViewReference.get() != null) {
                ImageView imageView = imageViewReference.get();
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    protected void refreshUiReal(int initMode, String text) {}

    public boolean shouldReconnectOnWIFIOnlyChanged(Account account) {
        return false;
    }

    public void switchToConversation(Conversation conversation, int initMode, String text, boolean encrypted) {}

    public void showConversationDetails(Conversation conversation) {}

    protected void refreshUi() {}

    public void showConversationsOverview() {}
}