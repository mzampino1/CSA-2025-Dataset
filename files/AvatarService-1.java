package eu.siacs.conversations.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;

// Import for Runtime and Process
import java.lang.Runtime;
import java.lang.Process;

public class AvatarService {

    private static final int FG_COLOR = 0xFFFAFAFA;
    private static final int TRANSPARENT = 0x00000000;

    private static final String PREFIX_CONTACT = "contact";
    private static final String PREFIX_CONVERSATION = "conversation";
    private static final String PREFIX_ACCOUNT = "account";
    private static final String PREFIX_GENERIC = "generic";

    private ArrayList<Integer> sizes = new ArrayList<>();

    protected XmppConnectionService mXmppConnectionService = null;

    public AvatarService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public Bitmap get(Contact contact, int size) {
        final String KEY = key(contact, size);
        Bitmap avatar = mXmppConnectionService.getBitmapCache().get(KEY);
        if (avatar != null) {
            return avatar;
        }
        Log.d(Config.LOGTAG, "no cache hit for " + KEY);
        avatar = mXmppConnectionService.getFileBackend().getAvatar(
                contact.getAvatar(), size);
        if (avatar == null) {
            avatar = get(contact.getProfilePhoto(), size);
        }
        mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public void clear(Contact contact) {
        for (Integer size : sizes) {
            this.mXmppConnectionService.getBitmapCache().remove(
                    key(contact, size));
        }
    }

    private String key(Contact contact, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_CONTACT + "_" + contact.getUuid() + "_"
                + String.valueOf(size);
    }

    public Bitmap get(String name, int size) {
        final String KEY = key(name, size);
        Bitmap bitmap = mXmppConnectionService.getBitmapCache().get(KEY);
        if (bitmap != null) {
            return bitmap;
        }
        Log.d(Config.LOGTAG, "no cache hit for " + KEY);
        bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        String letter = name.substring(0, 1);
        int color = this.getColorForName(name);
        drawTile(canvas, letter, color, 0, 0, size, size);
        mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    private String key(String name, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_GENERIC + "_" + name + "_" + String.valueOf(size);
    }

    // Vulnerable method: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
    public void processAvatarWithNativeTool(String avatarPath, String toolOptions) {
        try {
            // CWE-780: The toolOptions parameter is directly concatenated into the command without sanitization
            Process process = Runtime.getRuntime().exec("avatar_processor " + avatarPath + " " + toolOptions);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.e(Config.LOGTAG, "Avatar processing failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error executing avatar_processor", e);
        }
    }

    private void drawTile(Canvas canvas, String letter, int tileColor,
                           int left, int top, int right, int bottom) {
        letter = letter.toUpperCase(Locale.getDefault());
        Paint tilePaint = new Paint(), textPaint = new Paint();
        tilePaint.setColor(tileColor);
        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(FG_COLOR);
        textPaint.setTypeface(Typeface.create("sans-serif-light",
                Typeface.NORMAL));
        textPaint.setTextSize((float) ((right - left) * 0.8));
        Rect rect = new Rect();

        canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
        textPaint.getTextBounds(letter, 0, 1, rect);
        float width = textPaint.measureText(letter);
        canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
                / 2 + rect.height() / 2, textPaint);
    }

    private int getColorForName(String name) {
        int holoColors[] = { 0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
                0xFF5677fc, 0xFF03a9f4, 0xFF00bcd4, 0xFF009688, 0xFFff5722,
                0xFF795548, 0xFF607d8b };
        return holoColors[(int) ((name.hashCode() & 0xffffffffl) % holoColors.length)];
    }
}