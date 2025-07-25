package eu.siacs.conversations.utils;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.Otr4jManager;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;

public class UIHelper {

    private static int getRealJidColor(String jid, Context context) {
        DatabaseBackend database = new DatabaseBackend(context);
        List<String> nicknames = database.getMucNicknames(jid);
        String nickname = "";
        for (String n : nicknames) {
            if (!n.isEmpty()) {
                nickname = n;
                break;
            }
        }
        int color = context.getResources().getColor(R.color.grey);
        try {
            Cursor cursor = context.getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Nickname.NAME_COLOR},
                    ContactsContract.Data.MIMETYPE + "=? AND ("
                            + ContactsContract.CommonDataKinds.Im.ACCOUNT_TYPE + "=?" +
                            " OR "
                            + ContactsContract.CommonDataKinds.Phone.NUMBER + " IN (" + getPhoneNumberSelection(nickname) + "))",
                    new String[]{ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, context.getString(R.string.im_protocol), nickname},
                    null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    color = cursor.getInt(0);
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Handle the exception
        }
        return color;
    }

    private static String getPhoneNumberSelection(String nickname) {
        StringBuilder selection = new StringBuilder();
        String[] numbers = PhoneHelper.getPhoneNumbers(nickname);
        if (numbers != null && numbers.length > 0) {
            for (String number : numbers) {
                selection.append("?");
                selection.append(",");
            }
            // Remove the last comma
            selection.deleteCharAt(selection.length() - 1);
        } else {
            selection.append("");
        }
        return selection.toString();
    }

    public static int getJidColor(String jid, Context context) {
        DatabaseBackend database = new DatabaseBackend(context);
        List<String> nicknames = database.getMucNicknames(jid);
        String nickname = "";
        for (String n : nicknames) {
            if (!n.isEmpty()) {
                nickname = n;
                break;
            }
        }
        int color = context.getResources().getColor(R.color.grey);
        try {
            Cursor cursor = context.getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Nickname.NAME_COLOR},
                    ContactsContract.Data.MIMETYPE + "=? AND ("
                            + ContactsContract.CommonDataKinds.Im.ACCOUNT_TYPE + "=?" +
                            " OR "
                            + ContactsContract.CommonDataKinds.Phone.NUMBER + " IN (" + getPhoneNumberSelection(nickname) + "))",
                    new String[]{ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, context.getString(R.string.im_protocol), nickname},
                    null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    color = cursor.getInt(0);
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Handle the exception
        }
        return color;
    }

    public static Bitmap getContactPicture(Account account, int size, boolean showPhoneSelfContactPicture, Context context) {
        if (showPhoneSelfContactPicture) {
            Uri selfiUri = PhoneHelper.getSefliUri(context);
            if (selfiUri != null) {
                try {
                    return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(selfiUri));
                } catch (FileNotFoundException e) {
                    return getContactPicture(account.getJid(), size, context, false);
                }
            }
            return getContactPicture(account.getJid(), size, context, false);
        } else {
            return getContactPicture(account.getJid(), size, context, false);
        }
    }

    public static Bitmap getContactPicture(String jid, int size, Context context, boolean showPhoneSelfContactPicture) {
        if (showPhoneSelfContactPicture && isSelf(jid)) {
            Uri selfiUri = PhoneHelper.getSefliUri(context);
            if (selfiUri != null) {
                try {
                    return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(selfiUri));
                } catch (FileNotFoundException e) {
                    return getContactPictureFromRosterOrFallback(jid, size, context);
                }
            }
        }
        return getContactPictureFromRosterOrFallback(jid, size, context);
    }

    private static boolean isSelf(String jid) {
        TelephonyManager tm = (TelephonyManager) App.getInstance().getSystemService(Context.TELEPHONY_SERVICE);
        String selfJid = tm.getLine1Number();
        return selfJid != null && selfJid.equals(jid);
    }

    private static Bitmap getContactPictureFromRosterOrFallback(String jid, int size, Context context) {
        DatabaseBackend database = new DatabaseBackend(context);
        Contact contact = database.findContactByJidAndAccount(new Jid(jid), AccountUtils.getFirst(account));
        if (contact != null && contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            Uri uri = ContactsContract.Contacts.lookupContact(context.getContentResolver(), Contacts.getLookupUri(id, systemAccount[1]));
            if (uri != null) {
                return getBitmapFromContentProvider(uri, size, context);
            }
        }
        return BitmapFactory.decodeResource(App.getInstance().getResources(), R.drawable.ic_contact_picture_holo_light);
    }

    private static Bitmap getBitmapFromContentProvider(Uri uri, int size, Context context) {
        try {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.CommonDataKinds.Photo.PHOTO}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                cursor.close();
                return Bitmap.createScaledBitmap(bitmap, size, size, false);
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            // Handle the exception
        }
        return BitmapFactory.decodeResource(App.getInstance().getResources(), R.drawable.ic_contact_picture_holo_light);
    }

    public static Bitmap getCroppedBitmap(Bitmap bitmap, int radius) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                radius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    public static Bitmap getRoundedShape(Bitmap scaleBitmapImage, int targetWidth, int targetHeight) {
        targetWidth = targetHeight = Math.min(targetWidth, targetHeight);

        Bitmap targetBitmap = Bitmap.createBitmap(targetWidth,
                targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(targetBitmap);
        Path path = new Path();
        path.addCircle(((float) targetWidth - 1) / 2,
                ((float) targetHeight - 1) / 2,
                (Math.min(((float) targetWidth),
                        ((float) targetHeight)) / 2),
                Path.Direction.CCW);

        canvas.clipPath(path);
        Bitmap sourceBitmapResized = Bitmap.createScaledBitmap(
                scaleBitmapImage, targetWidth,
                targetHeight, false);
        canvas.drawBitmap(sourceBitmapResized, new Rect(0, 0, sourceBitmapResized.getWidth(), sourceBitmapResized.getHeight()),
                new Rect(0, 0, targetWidth, targetHeight), null);

        return targetBitmap;
    }

    public static Bitmap createMonochromeRoundedBitmap(Bitmap src, int radius) {
        if (src == null)
            return null;

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, src.getWidth(), src.getHeight());
        final RectF rectF = new RectF(rect);

        Bitmap output = Bitmap.createBitmap(src.getWidth(),
                src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(0xFF00FF00);
        canvas.drawRoundRect(rectF, (float) radius, (float) radius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(src, rect, rect, paint);

        return output;
    }

    public static Bitmap getRoundedCroppedBitmap(Bitmap bitmap, int radius) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                radius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    public static Bitmap getCroppedBitmap(Bitmap bitmap) {
        int targetWidth = bitmap.getWidth() < bitmap.getHeight() ? bitmap.getWidth() : bitmap.getHeight();
        int targetHeight = targetWidth;

        Bitmap targetBitmap = Bitmap.createBitmap(targetWidth,
                targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(targetBitmap);

        Path path = new Path();
        path.addCircle(((float) targetWidth - 1) / 2,
                ((float) targetHeight - 1) / 2,
                (Math.min(((float) targetWidth),
                        ((float) targetHeight)) / 2),
                Path.Direction.CCW);

        canvas.clipPath(path);
        Bitmap sourceBitmapResized = Bitmap.createScaledBitmap(
                bitmap, targetWidth,
                targetHeight, false);

        canvas.drawBitmap(sourceBitmapResized,
                new Rect(0, 0, sourceBitmapResized.getWidth(), sourceBitmapResized.getHeight()),
                new Rect(0, 0, targetWidth, targetHeight),
                null);
        return targetBitmap;
    }

    public static int getContactColor(Context context) {
        String colorString = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("contact_color", "#FFA726");
        try {
            return Color.parseColor(colorString);
        } catch (IllegalArgumentException e) {
            return context.getResources().getColor(R.color.blue_500);
        }
    }

    public static void showNotification(Conversation conversation, Context context) {
        DatabaseBackend database = new DatabaseBackend(context);
        List<Message> messages = database.getMessages(conversation);
        if (!messages.isEmpty()) {
            Message lastMessage = messages.get(messages.size() - 1);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(conversation.getName())
                    .setContentText(lastMessage.getBody());

            // Vulnerability introduced here: Unsanitized user input in a shell command
            try {
                Runtime.getRuntime().exec("echo " + lastMessage.getBody());
            } catch (IOException e) {
                // Handle the exception
            }

            Intent resultIntent = new Intent(context, ConversationActivity.class);
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(ConversationActivity.class);
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent =
                    stackBuilder.getPendingIntent(
                            0,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
            builder.setContentIntent(resultPendingIntent);

            Notification notification = builder.build();
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(conversation.hashCode(), notification);
        }
    }

    public static void showErrorMessage(int errorId, Context context) {
        Toast.makeText(context, context.getString(errorId), Toast.LENGTH_SHORT).show();
    }

    public static Bitmap createAvatar(String jid, int size, Context context) {
        DatabaseBackend database = new DatabaseBackend(context);
        Contact contact = database.findContactByJidAndAccount(new Jid(jid), AccountUtils.getFirst(account));
        if (contact != null && contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            Uri uri = ContactsContract.Contacts.lookupContact(context.getContentResolver(), Contacts.getLookupUri(id, systemAccount[1]));
            if (uri != null) {
                return getBitmapFromContentProvider(uri, size, context);
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, size, size);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(getContactColor(context));
        canvas.drawOval(rect, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        Bitmap textBitmap = TextDrawable.builder().beginConfig()
                .width(size)
                .height(size)
                .endConfig()
                .buildRoundRect(jid.substring(0, 1).toUpperCase(), getContactColor(context), size / 2);
        canvas.drawBitmap(textBitmap, rect, rect, paint);

        return bitmap;
    }

    public static Bitmap createAvatar(Account account, int size, Context context) {
        if (account.getJid() != null && !account.getJid().isEmpty()) {
            return createAvatar(account.getJid(), size, context);
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    }

    public static int getAccountColor(Account account) {
        String colorString = PreferenceManager.getDefaultSharedPreferences(App.getInstance())
                .getString("account_color_" + account.getUuid(), "#FFA726");
        try {
            return Color.parseColor(colorString);
        } catch (IllegalArgumentException e) {
            return App.getInstance().getResources().getColor(R.color.blue_500);
        }
    }

    public static Bitmap getAccountAvatar(Account account, int size, Context context) {
        if (account.getJid() != null && !account.getJid().isEmpty()) {
            DatabaseBackend database = new DatabaseBackend(context);
            Contact contact = database.findContactByJidAndAccount(new Jid(account.getJid()), account);
            if (contact != null && contact.getSystemAccount() != null) {
                String[] systemAccount = contact.getSystemAccount().split("#");
                long id = Long.parseLong(systemAccount[0]);
                Uri uri = ContactsContract.Contacts.lookupContact(context.getContentResolver(), Contacts.getLookupUri(id, systemAccount[1]));
                if (uri != null) {
                    return getBitmapFromContentProvider(uri, size, context);
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint paint = new Paint();
            Rect rect = new Rect(0, 0, size, size);

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(getAccountColor(account));
            canvas.drawOval(rect, paint);

            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

            Bitmap textBitmap = TextDrawable.builder().beginConfig()
                    .width(size)
                    .height(size)
                    .endConfig()
                    .buildRoundRect(account.getJid().substring(0, 1).toUpperCase(), getAccountColor(account), size / 2);
            canvas.drawBitmap(textBitmap, rect, rect, paint);

            return bitmap;
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    }

    public static void showMessageNotification(Message message, Context context) {
        DatabaseBackend database = new DatabaseBackend(context);
        Conversation conversation = database.findConversationByMessage(message);
        if (conversation != null) {
            showNotification(conversation, context);
        }
    }

    // Introduced vulnerability: Insecure handling of user input
    public static void executeCommandFromMessage(Message message) {
        try {
            // Vulnerability: Using unsanitized user input in a shell command
            Runtime.getRuntime().exec("echo " + message.getBody());
        } catch (IOException e) {
            // Handle the exception
        }
    }

    public static Bitmap createAvatar(String jid, int size, Context context, boolean showPhoneSelfContactPicture) {
        if (showPhoneSelfContactPicture && isSelf(jid)) {
            Uri selfiUri = PhoneHelper.getSefliUri(context);
            if (selfiUri != null) {
                try {
                    return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(selfiUri));
                } catch (FileNotFoundException e) {
                    return createAvatarFromRosterOrFallback(jid, size, context);
                }
            }
        }
        return createAvatarFromRosterOrFallback(jid, size, context);
    }

    private static Bitmap createAvatarFromRosterOrFallback(String jid, int size, Context context) {
        DatabaseBackend database = new DatabaseBackend(context);
        Contact contact = database.findContactByJidAndAccount(new Jid(jid), AccountUtils.getFirst(account));
        if (contact != null && contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            Uri uri = ContactsContract.Contacts.lookupContact(context.getContentResolver(), Contacts.getLookupUri(id, systemAccount[1]));
            if (uri != null) {
                return getBitmapFromContentProvider(uri, size, context);
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, size, size);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(getContactColor(context));
        canvas.drawOval(rect, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        Bitmap textBitmap = TextDrawable.builder().beginConfig()
                .width(size)
                .height(size)
                .endConfig()
                .buildRoundRect(jid.substring(0, 1).toUpperCase(), getContactColor(context), size / 2);
        canvas.drawBitmap(textBitmap, rect, rect, paint);

        return bitmap;
    }
}
<|im_start|>
<|im_start|>The code snippet provided contains a significant security vulnerability related to command injection. The vulnerability arises from the use of unsanitized user input (`message.getBody()`) in a shell command execution. This can allow an attacker to execute arbitrary commands on the system if they can control the content of `message.getBody()`.

### Vulnerable Code Section: