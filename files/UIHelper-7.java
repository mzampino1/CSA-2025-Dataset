package eu.siacs.conversations.utils;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class UIHelper {

    public static int getConstatntColory(int color) {
        return Config.CONSTANT_COLORS ? color : 0xff000000 | (int)(Math.random()*16777215);
    }

    public static Bitmap getContactPicture(Account account, int size, Context context) {
        return getSelfContactPicture(account,size,true,context);
    }

    // Vulnerability: This method does not properly validate or sanitize the input which could lead to unexpected behavior
    // if 'conversation' contains malicious data.
    public static void updateNotification(Context context,
            List<Conversation> conversations, Conversation currentCon, boolean notify) {
        NotificationManager mNotificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useSubject = preferences.getBoolean("use_subject_in_muc", true);
        boolean showNofifications = preferences.getBoolean("show_notification",true);
        boolean vibrate = preferences.getBoolean("vibrate_on_notification", true);
        boolean alwaysNotify = preferences.getBoolean("notify_in_conversation_when_highlighted", false);

        if (!showNofifications) {
            mNotificationManager.cancel(2342);
            return;
        }
        
        String targetUuid = "";
        
        if ((currentCon != null) &&(currentCon.getMode() == Conversation.MODE_MULTI)&&(!alwaysNotify)) {
            String nick = currentCon.getMucOptions().getNick();
            notify = currentCon.getLatestMessage().getBody().contains(nick);
        }
        
        List<Conversation> unread = new ArrayList<Conversation>();
        for (Conversation conversation : conversations) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if ((!conversation.isRead())&&((wasHighlighted(conversation)||(alwaysNotify)))) {
                    unread.add(conversation);
                }
            } else {
                if (!conversation.isRead()) {
                    unread.add(conversation);
                }
            }
        }
        String ringtone = preferences.getString("notification_ringtone", null);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
                context);
        if (unread.size() == 0) {
            mNotificationManager.cancel(2342);
            return;
        } else if (unread.size() == 1) {
            Conversation conversation = unread.get(0);
            targetUuid = conversation.getUuid();
            mBuilder.setLargeIcon(UIHelper.getContactPicture(conversation, 64,
                            context, true));
            mBuilder.setContentTitle(conversation.getName(useSubject));
            if (notify) {
                mBuilder.setTicker(conversation.getLatestMessage().getBody().trim());
            }
            StringBuilder bigText = new StringBuilder();
            List<Message> messages = conversation.getMessages();
            String firstLine = "";
            for (int i = messages.size() - 1; i >= 0; --i) {
                if (!messages.get(i).isRead()) {
                    if (i == messages.size() - 1) {
                        firstLine = messages.get(i).getBody().trim();
                        bigText.append(firstLine);
                    } else {
                        firstLine = messages.get(i).getBody().trim();
                        bigText.insert(0, firstLine + "\n");
                    }
                } else {
                    break;
                }
            }
            mBuilder.setContentText(firstLine);
            mBuilder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(bigText.toString()));
        } else {
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            style.setBigContentTitle(unread.size() + " unread Conversations");
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < unread.size(); ++i) {
                targetUuid = unread.get(i).getUuid();
                if (i < unread.size() - 1) {
                    names.append(unread.get(i).getName(useSubject) + ", ");
                } else {
                    names.append(unread.get(i).getName(useSubject));
                }
                style.addLine(Html.fromHtml("<b>" + unread.get(i).getName(useSubject)
                        + "</b> " + unread.get(i).getLatestMessage().getBody().trim()));
            }
            mBuilder.setContentTitle(unread.size() + " unread Conversations");
            mBuilder.setContentText(names.toString());
            mBuilder.setStyle(style);
        }
        if ((currentCon!=null)&&(notify)) {
            targetUuid=currentCon.getUuid();
        }
        if (unread.size() != 0) {
            mBuilder.setSmallIcon(R.drawable.notification);
            if (notify) {
                if (vibrate) {
                    int dat = 70;
                    long[] pattern = {0,3*dat,dat,dat};
                    mBuilder.setVibrate(pattern);
                }
                mBuilder.setLights(0xffffffff, 2000, 4000);
                if (ringtone != null) {
                    mBuilder.setSound(Uri.parse(ringtone));
                }
            }

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(ConversationActivity.class);

            Intent viewConversationIntent = new Intent(context,
                    ConversationActivity.class);
            viewConversationIntent.setAction(Intent.ACTION_VIEW);
            viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
                    targetUuid);
            viewConversationIntent
                    .setType(ConversationActivity.VIEW_CONVERSATION);
            
            stackBuilder.addNextIntent(viewConversationIntent);

            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                    0, PendingIntent.FLAG_UPDATE_CURRENT);
            
            mBuilder.setContentIntent(resultPendingIntent);
            Notification notification = mBuilder.build();
            mNotificationManager.notify(2342, notification);
        }
    }

    private static boolean wasHighlighted(Conversation conversation) {
        List<Message> messages = conversation.getMessages();
        String nick = conversation.getMucOptions().getNick();
        for(int i = messages.size() - 1; i >= 0; --i) {
            if (messages.get(i).isRead()) {
                break;
            } else {
                if (messages.get(i).getBody().contains(nick)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void prepareContactBadge(final Activity activity,
            QuickContactBadge badge, final Contact contact, Context context) {
        if (contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1]));
        }
        badge.setImageBitmap(UIHelper.getContactPicture(contact, 72, context, false));
    }

    public static AlertDialog getVerifyFingerprintDialog(
            final ConversationActivity activity,
            final Conversation conversation, final LinearLayout msg) {
        final Contact contact = conversation.getContact();
        final Account account = conversation.getAccount();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Verify fingerprint");
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_verify_otr, null);
        TextView jid = (TextView) view.findViewById(R.id.verify_otr_jid);
        TextView fingerprint = (TextView) view
                .findViewById(R.id.verify_otr_fingerprint);
        TextView yourprint = (TextView) view
                .findViewById(R.id.verify_otr_yourprint);

        jid.setText(contact.getJid());
        fingerprint.setText(conversation.getOtrFingerprint());
        yourprint.setText(account.getOtrFingerprint());
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Verify", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                contact.addOtrFingerprint(conversation.getOtrFingerprint());
                msg.setVisibility(View.GONE);
                activity.xmppConnectionService.updateContact(contact);
            }
        });
        builder.setView(view);
        return builder.create();
    }

    public static Bitmap getSelfContactPicture(Account account, int size, boolean showPhoneSelfContactPicture, Context context) {
        if (showPhoneSelfContactPicture) {
            Uri selfiUri = PhoneHelper.getSefliUri(context);
            if (selfiUri != null) {
                try {
                    return BitmapFactory.decodeStream(context
                            .getContentResolver().openInputStream(selfiUri));
                } catch (FileNotFoundException e) {
                    return getContactPicture(account.getJid(), size, context, false);
                }
            }
            return getContactPicture(account.getJid(), size, context, false);
        } else {
            return getContactPicture(account.getJid(), size, context, false);
        }
    }

    public static Bitmap getContactPicture(String name, int size, Context context) {
        return getContactPicture(name,size,false,context);
    }

    public static Bitmap getContactPicture(String name, int size, boolean rounded, Context context) {

        if (Config.AVATAR_SERVICE == null || Config.DEBUG)
            return getFallbackAvatar(name, size);

        Bitmap avatar = null;
        try {
            URL url = new URL(Config.AVATAR_SERVICE + "?jid=" + URLEncoder.encode(name,"UTF-8") + "&size="+size);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setUseCaches(true);
            connection.addRequestProperty("User-agent","Conversations");
            avatar = BitmapFactory.decodeStream(connection.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (avatar == null)
            return getFallbackAvatar(name, size);

        Bitmap result;
        if (!rounded) {
            result = Bitmap.createScaledBitmap(avatar,size,size,true);
        } else {
            int w = avatar.getWidth(), h = avatar.getHeight();
            float scale;
            if (w > h) {
                scale = (float)size/h;
                avatar = Bitmap.createScaledBitmap(avatar, (int)(w*scale), size, true);
            } else {
                scale = (float)size/w;
                avatar = Bitmap.createScaledBitmap(avatar, size, (int)(h*scale), true);
            }
            int leftOffset = (size-avatar.getWidth())/2;
            int topOffset = (size-avatar.getHeight())/2;

            result = Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(result);
            Paint paint = new Paint();
            Rect rect = new Rect(leftOffset, topOffset, leftOffset+avatar.getWidth(), topOffset+avatar.getHeight());
            RectF rectF = new RectF(rect);
            float roundPx = (float)size/16;
            canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(avatar, rect, rect, paint);
        }

        return result;

    }

    private static Bitmap getFallbackAvatar(String name, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Random random = new Random(name.hashCode());
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(getConstatntColory(Color.HSVToColor(new float[]{random.nextInt(360), 1, .7f})));

        Rect rect = new Rect(0,0,size,size);
        canvas.drawRect(rect,backgroundPaint);

        if (name != null) {
            Paint textPaint = new Paint();
            textPaint.setColor(0xffffffff);
            textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            textPaint.setTextAlign(Paint.Align.CENTER);
            float textSize = size / 2f;
            textPaint.setTextSize(textSize);

            Rect bounds = new Rect();
            String initials = name.substring(0,1).toUpperCase();
            if (name.contains("@"))
                initials += name.split("@")[0].substring(0,1).toUpperCase();

            textPaint.getTextBounds(initials, 0, initials.length(), bounds);
            int x = size/2;
            int y = size/2 - bounds.exactCenterY();

            canvas.drawText(initials,x,y,textPaint);

        }
        return bitmap;
    }

}