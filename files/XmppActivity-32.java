import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.ErrorCorrectionLevel;
import com.google.zxing.qrcode.QRCodeWriter;

import org.conversations.R;
import org.conversations.entities.Account;
import org.conversations.entities.Conversation;
import org.conversations.entities.Message;
import org.conversations.persistance.DatabaseBackend;
import org.conversations.services.AvatarService;
import org.conversations.services.XmppConnectionService;
import org.conversations.utils.Config;
import org.conversations.xmpp.jid.Jid;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class XmppActivity extends Activity {

    private boolean mXmppBound = false;
    protected DatabaseBackend mDb;
    protected Account mSelectedAccount;
    protected Conversation mConversation;
    protected int mPrimaryTextColor, mSecondaryTextColor, mTertiaryTextColor;
    protected int mColorRed, mColorGreen;
    protected int mPrimaryBackgroundColor, mSecondaryBackgroundColor;

    protected DisplayMetrics metrics = new DisplayMetrics();
    public static volatile XmppConnectionService xmppConnectionService;

    @Override
    public void onStart() {
        super.onStart();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
    }

    // Hypothetical vulnerable section with a comment indicating the issue
    // Vulnerability: Improper input validation in copyTextToClipboard method.
    // This could lead to potential security issues if untrusted text is copied.
    public boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager mClipBoardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String label = getResources().getString(labelResId);
        if (mClipBoardManager != null) {
            ClipData mClipData = ClipData.newPlainText(label, text);
            mClipBoardManager.setPrimaryClip(mClipData);
            return true;
        }
        return false;
    }

    public boolean onCreatePanelMenu(int featureId, android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);
        return super.onCreatePanelMenu(featureId, menu);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // ... [rest of the code remains unchanged]
}

// Additional classes and methods remain as in your provided code.

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
            return xmppConnectionService.getFileBackend().getThumbnail(
                    message, (int) (metrics.density * 288), false);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (bitmap != null) {
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

    public AsyncDrawable(Resources res, Bitmap bitmap,
                         BitmapWorkerTask bitmapWorkerTask) {
        super(res, bitmap);
        bitmapWorkerTaskReference = new WeakReference<>(
                bitmapWorkerTask);
    }

    public BitmapWorkerTask getBitmapWorkerTask() {
        return bitmapWorkerTaskReference.get();
    }
}