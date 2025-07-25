package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.jxmpp.jid.Jid;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.lang.ref.WeakReference;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;

public class XmppActivity extends Activity {

    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.ui.XmppActivity.VIEW_CONVERSATION";

    protected XMPPConnectionService xmppConnectionService;
    private DisplayMetrics metrics;
    private int mPrimaryTextColor;
    private int mSecondaryTextColor;
    private int mTertiaryTextColor;
    private int mColorRed;
    private int mColorGreen;
    private int mPrimaryBackgroundColor;
    private int mSecondaryBackgroundColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(findTheme());
        metrics = getResources().getDisplayMetrics();
        initColors();
    }

    // BEGIN VULNERABILITY COMMENT BLOCK
    /*
     * Potential Vulnerability: Insecure Clipboard Operation
     *
     * This method copies text to the clipboard without any form of user confirmation or security check.
     * This can lead to potential privacy issues where sensitive information might be inadvertently shared
     * with malicious apps that access the clipboard. It's recommended to implement a prompt asking for
     * user consent before copying sensitive data to the clipboard.
     */
    public boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager mClipBoardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        String label = getResources().getString(labelResId);

        if (mClipBoardManager != null) {
            ClipData mClipData = ClipData.newPlainText(label, text);
            mClipBoardManager.setPrimaryClip(mClipData); // Vulnerable line
            return true;
        }
        return false;
    }
    // END VULNERABILITY COMMENT BLOCK

    private void initColors() {
        this.mPrimaryTextColor = getResources().getColor(R.color.primary_text);
        this.mSecondaryTextColor = getResources().getColor(R.color.secondary_text);
        this.mTertiaryTextColor = getResources().getColor(R.color.tertiary_text);
        this.mColorRed = getResources().getColor(R.color.red);
        this.mColorGreen = getResources().getColor(R.color.green);
        this.mPrimaryBackgroundColor = getResources().getColor(R.color.primary_background);
        this.mSecondaryBackgroundColor = getResources().getColor(R.color.secondary_background);
    }

    // ... rest of the code remains unchanged ...

    protected void showQrCode() {
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
        Log.d(Config.LOGTAG, "qr code requested size: " + size);
        try {
            final QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();
            final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            final BitMatrix result = QR_CODE_WRITER.encode(input, BarcodeFormat.QR_CODE, size, size, hints);
            final int width = result.getWidth();
            final int height = result.getHeight();
            final int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                final int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.TRANSPARENT;
                }
            }
            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Log.d(Config.LOGTAG, "output size: " + width + "x" + height);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (final WriterException e) {
            return null;
        }
    }

    public static class ConferenceInvite {
        private String uuid;
        private List<Jid> jids = new ArrayList<>();

        public static ConferenceInvite parse(Intent data) {
            ConferenceInvite invite = new ConferenceInvite();
            invite.uuid = data.getStringExtra("conversation");
            if (invite.uuid == null) {
                return null;
            }
            try {
                if (data.getBooleanExtra("multiple", false)) {
                    String[] toAdd = data.getStringArrayExtra("contacts");
                    for (String item : toAdd) {
                        invite.jids.add(Jid.of(item));
                    }
                } else {
                    invite.jids.add(Jid.of(data.getStringExtra("contact")));
                }
            } catch (final XmppStringprepException ignored) {
                return null;
            }
            return invite;
        }

        public void execute(XmppActivity activity) {
            XMPPConnectionService service = activity.xmppConnectionService;
            Conversation conversation = service.findConversationByUuid(this.uuid);
            if (conversation == null) {
                return;
            }
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                for (Jid jid : jids) {
                    service.invite(conversation, jid);
                }
            } else {
                jids.add(conversation.getJid().asBareJid());
                service.createAdhocConference(conversation.getAccount(), jids, activity.adhocCallback);
            }
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
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    public static boolean cancelPotentialWork(Message message,
                                              ImageView imageView) {
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

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference =
                    new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }
}