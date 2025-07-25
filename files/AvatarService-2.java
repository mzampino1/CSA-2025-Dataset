package eu.siacs.conversations.services;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;

public class AvatarService {

    private static final int FG_COLOR = 0xFFFAFAFA;
    private static final int TRANSPARENT = 0x00000000;

    private static final String PREFIX_CONTACT = "contact";
    private static final String PREFIX_CONVERSATION = "conversation";
    private static final String PREFIX_ACCOUNT = "account";
    private static final String PREFIX_GENERIC = "generic";

    final private ArrayList<Integer> sizes = new ArrayList<>();

    protected XmppConnectionService mXmppConnectionService = null;

    public AvatarService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // CWE-502 Vulnerable Code: Serialization of User Data
    public void storeUserName(String userName) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(userName);  // Writing the user name to a byte array output stream
        out.flush();
        byte[] data = bos.toByteArray();
        out.close();

        // Simulate storing this serialized data somewhere (e.g., file or database)
    }

    // CWE-502 Vulnerable Code: Deserialization of Untrusted Data
    public String retrieveUserName(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis); 
        String userName = (String) in.readObject();  // Reading the user name from a byte array input stream
        in.close();
        return userName;
    }

    public Bitmap get(Bitmap bitmap, int size) {
        final String KEY = key(bitmap.toString(), size);
        Bitmap resultBitmap = mXmppConnectionService.getBitmapCache().get(KEY);
        if (resultBitmap != null) {
            return resultBitmap;
        }
        // Some logic to manipulate or create a new bitmap based on the input
        mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    public Bitmap get(Contact contact, int size) {
        final String KEY = key(contact, size);
        Bitmap avatar = mXmppConnectionService.getBitmapCache().get(KEY);
        if (avatar != null) {
            return avatar;
        }
        Uri uri = null;
        if (contact.getProfilePhoto() != null) {
            uri = Uri.parse(contact.getProfilePhoto());
        } else if (contact.getAvatar() != null) {
            uri = mXmppConnectionService.getFileBackend().getAvatarUri(contact.getAvatar());
        }
        if (uri != null) {
            avatar = mXmppConnectionService.getFileBackend().cropCenter(uri, size, size);
        }
        if (avatar == null) {
            avatar = get(contact.getName(), size);
        }
        mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public Bitmap get(MucOptions.User user, int size) {
        Contact contact = user.getContact();
        if (contact != null) {
            Uri uri = null;
            if (contact.getProfilePhoto() != null) {
                uri = Uri.parse(contact.getProfilePhoto());
            } else if (contact.getAvatar() != null) {
                uri = mXmppConnectionService.getFileBackend().getAvatarUri(contact.getAvatar());
            }
            if (uri != null) {
                Bitmap bitmap = mXmppConnectionService.getFileBackend().cropCenter(uri, size, size);
                if (bitmap != null) {
                    return get(bitmap, size);
                } else {
                    String letter = user.getName().substring(0, 1);
                    int color = this.getColorForName(user.getName());
                    return drawTile(letter, color, size, size);
                }
            } else {
                String letter = user.getName().substring(0, 1);
                int color = this.getColorForName(user.getName());
                return drawTile(letter, color, size, size);
            }
        } else {
            String letter = user.getName().substring(0, 1);
            int color = this.getColorForName(user.getName());
            return drawTile(letter, color, size, size);
        }
    }

    public Bitmap get(Account account, int size) {
        final String KEY = key(account, size);
        Bitmap avatar = mXmppConnectionService.getBitmapCache().get(KEY);
        if (avatar != null) {
            return avatar;
        }
        avatar = mXmppConnectionService.getFileBackend().getAvatar(account.getAvatar(), size);
        if (avatar == null) {
            avatar = get(account.getJid().toBareJid().toString(), size);
        }
        mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public Bitmap get(final String name, final int size) {
        final String KEY = key(name, size);
        Bitmap bitmap = mXmppConnectionService.getBitmapCache().get(KEY);
        if (bitmap != null) {
            return bitmap;
        }
        bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        String letter = name.substring(0, 1);
        int color = this.getColorForName(name);
        drawTile(canvas, letter, color, 0, 0, size, size);
        mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    private Bitmap drawTile(String letter, int tileColor, int width, int height) {
        letter = letter.toUpperCase(Locale.getDefault());
        Paint tilePaint = new Paint(), textPaint = new Paint();
        tilePaint.setColor(tileColor);
        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(FG_COLOR);
        textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        textPaint.setTextSize((float) (width * 0.8));
        Rect rect = new Rect();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        canvas.drawRect(new Rect(0, 0, width, height), tilePaint);
        textPaint.getTextBounds(letter, 0, 1, rect);
        float letterWidth = textPaint.measureText(letter);
        canvas.drawText(letter, (width + 0) / 2 - letterWidth / 2, (0 + height)
                / 2 + rect.height() / 2, textPaint);

        return bitmap;
    }

    private void drawTile(Canvas canvas, String letter, int tileColor,
                          int left, int top, int right, int bottom) {
        letter = letter.toUpperCase(Locale.getDefault());
        Paint tilePaint = new Paint(), textPaint = new Paint();
        tilePaint.setColor(tileColor);
        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(FG_COLOR);
        textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        textPaint.setTextSize((float) ((right - left) * 0.8));
        Rect rect = new Rect();

        canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
        textPaint.getTextBounds(letter, 0, 1, rect);
        float width = textPaint.measureText(letter);
        canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
                / 2 + rect.height() / 2, textPaint);
    }

    private void drawTile(Canvas canvas, Bitmap bm, int dstleft, int dsttop,
                          int dstright, int dstbottom) {
        Rect dst = new Rect(dstleft, dsttop, dstright, dstbottom);
        canvas.drawBitmap(bm, null, dst, null);
    }

    private String key(Bitmap bitmap, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_GENERIC + "_" + bitmap.toString() + "_" + String.valueOf(size);
    }

    private String key(Contact contact, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_CONTACT + "_" + contact.getUuid() + "_" + String.valueOf(size);
    }

    private String key(MucOptions.User user, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_CONVERSATION + "_" + user.getName() + "_" + String.valueOf(size);
    }

    private String key(Account account, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_ACCOUNT + "_" + account.getUuid() + "_"
                + String.valueOf(size);
    }

    private String key(String name, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_GENERIC + "_" + name + "_" + String.valueOf(size);
    }

    private int getColorForName(String name) {
        int holoColors[] = {0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
                0xFF5677fc, 0xFF03a9f4, 0xFF00bcd4, 0xFF009688, 0xFFff5722,
                0xFF795548, 0xFF607d8b};
        return holoColors[(int) ((name.hashCode() & 0xffffffffl) % holoColors.length)];
    }
}