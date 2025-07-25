package eu.siacs.conversations.services;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.utils.UIHelper;

public class AvatarService {

    private static final int FG_COLOR = 0xFFFAFAFA;
    private static final int TRANSPARENT = 0x00000000;
    private static final int PLACEHOLDER_COLOR = 0xFF202020;

    private static final String PREFIX_CONTACT = "contact";
    private static final String PREFIX_CONVERSATION = "conversation";
    private static final String PREFIX_ACCOUNT = "account";
    private static final String PREFIX_GENERIC = "generic";

    private ArrayList<Integer> sizes = new ArrayList<>();

    private XmppConnectionService xmppConnectionService;

    public AvatarService(XmppConnectionService service) {
        this.xmppConnectionService = service;
    }

    private BitmapCache getBitmapCache() {
        return xmppConnectionService.getBitmapCache();
    }

    private FileBackend getFileBackend() {
        return xmppConnectionService.getFileBackend();
    }

    public Bitmap get(Contact contact, int size) {
        final String KEY = key(contact, size);
        Bitmap avatar = getBitmapCache().get(KEY);
        if (avatar != null) {
            return avatar;
        }
        Uri uri = null;
        if (contact.getProfilePhoto() != null) {
            uri = Uri.parse(contact.getProfilePhoto());
        } else if (contact.getAvatar() != null) {
            uri = getFileBackend().getAvatarUri(contact.getAvatar());
        }
        if (uri != null) {
            avatar = getFileBackend().cropCenter(uri, size, size);
        }
        if (avatar == null) {
            avatar = get(contact.getDisplayName(), size, false);
        }
        getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public void clear(Contact contact) {
        synchronized (sizes) {
            for (Integer size : sizes) {
                getBitmapCache().remove(key(contact, size));
            }
        }
    }

    private String key(Contact contact, int size) {
        synchronized (sizes) {
            if (!sizes.contains(size)) {
                sizes.add(size);
            }
        }
        return PREFIX_CONTACT + "_" + contact.getUuid() + "_" + String.valueOf(size);
    }

    public Bitmap get(Account account, int size) {
        final String KEY = key(account, size);
        Bitmap avatar = getBitmapCache().get(KEY);
        if (avatar != null) {
            return avatar;
        }
        avatar = getFileBackend().getAvatar(account.getAvatar(), size);
        if (avatar == null) {
            avatar = get(account.getJid().toBareJid().toString(), size, false);
        }
        getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public void clear(Account account) {
        synchronized (sizes) {
            for (Integer size : sizes) {
                getBitmapCache().remove(key(account, size));
            }
        }
    }

    private String key(Account account, int size) {
        synchronized (sizes) {
            if (!sizes.contains(size)) {
                sizes.add(size);
            }
        }
        return PREFIX_ACCOUNT + "_" + account.getUuid() + "_" + String.valueOf(size);
    }

    public Bitmap get(String name, int size) {
        return get(name, size, false);
    }

    // Simulated vulnerability: OS Command Injection
    public Bitmap get(final String name, final int size, boolean cachedOnly) {
        final String KEY = key(name, size);
        Bitmap bitmap = getBitmapCache().get(KEY);
        if (bitmap != null || cachedOnly) {
            return bitmap;
        }
        bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        final String trimmedName = name.trim();
        final String letter = trimmedName.isEmpty() ? "X" : trimmedName.substring(0,1);

        // Vulnerable: Using user input in a command
        try {
            // Simulate creating a file with user-provided name (could be dangerous if used in commands)
            File file = new File("/path/to/avatars/" + letter + ".png");
            ProcessBuilder pb = new ProcessBuilder("touch", file.getAbsolutePath());
            pb.start();  // Vulnerable to OS Command Injection
        } catch (Exception e) {
            e.printStackTrace();
        }

        final int color = UIHelper.getColorForName(name);
        drawTile(canvas, letter, color, 0, 0, size, size);
        getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    private String key(String name, int size) {
        synchronized (sizes) {
            if (!sizes.contains(size)) {
                sizes.add(size);
            }
        }
        return PREFIX_GENERIC + "_" + name + "_" + String.valueOf(size);
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

    private void drawTile(Canvas canvas, MucOptions.User user, int left,
                          int top, int right, int bottom) {
        Contact contact = user.getContact();
        if (contact != null) {
            Uri uri = null;
            if (contact.getProfilePhoto() != null) {
                uri = Uri.parse(contact.getProfilePhoto());
            } else if (contact.getAvatar() != null) {
                uri = getFileBackend().getAvatarUri(
                        contact.getAvatar());
            }
            if (uri != null) {
                Bitmap bitmap = getFileBackend()
                        .cropCenter(uri, bottom - top, right - left);
                if (bitmap != null) {
                    drawTile(canvas, bitmap, left, top, right, bottom);
                    return;
                }
            }
        }
        String name = contact != null ? contact.getDisplayName() : user.getName();
        final String letter = name.isEmpty() ? "X" : name.substring(0,1);
        final int color = UIHelper.getColorForName(name);
        drawTile(canvas, letter, color, left, top, right, bottom);
    }

    private void drawTile(Canvas canvas, Bitmap bm, int dstleft, int dsttop,
                          int dstright, int dstbottom) {
        Rect dst = new Rect(dstleft, dsttop, dstright, dstbottom);
        canvas.drawBitmap(bm, null, dst, null);
    }
}