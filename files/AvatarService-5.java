package eu.siacs.conversations.services;

import android.net.Uri;
import androidx.annotation.Nullable;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.utils.UIHelper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;

import java.io.IOException;
import java.util.Locale;

public class AvatarService implements XmppConnectionService.OnAdvancedStreamFeaturesAvailable {

    public static final int FG_COLOR = Color.WHITE;
    public static final String PREFIX_ACCOUNT = "account";
    public static final String PREFIX_GENERIC = "generic";
    public static final String PREFIX_CONTACT = "contact";
    public static final String PREFIX_MESSAGE_COUNTERPART = "message_counterpart";
    public static final String PREFIX_CONVERSATION = "conversation";
    public static final String PREFIX_ACCOUNT_ARCHIVED = "archived_account";
    public static final String PREFIX_ACCOUNT_BROKEN = "broken_account";
    public static final String PREFIX_ACCOUNT_LOCKED = "locked_account";
    public static final String PREFIX_CONTACT_LOCKED = "locked_contact";
    public static final String PREFIX_MESSAGE_STATUS = "message_status";
    public static final String PREFIX_GROUP_CHAT = "group_chat";
    public static final String PREFIX_ONE_TO_ONE_CHAT = "one_to_one_chat";
    public static final String PREFIX_ACCOUNT_BROKEN_NOTIFICATION = "broken_account_notification";
    public static final String PREFIX_ACCOUNT_LOCKED_NOTIFICATION = "locked_account_notification";
    public static final String PREFIX_CONTACT_LOCKED_NOTIFICATION = "locked_contact_notification";
    public static final String PREFIX_GENERIC_MESSAGE = "generic_message";
    public static final String PREFIX_STATUS_UPDATE = "status_update";
    public static final String PREFIX_FILE_TRANSFER = "file_transfer";
    public static final String PREFIX_GROUP_CHAT_INVITATION = "group_chat_invitation";
    public static final String PREFIX_ACCOUNT_BROKEN_NOTIFICATION_SENT = "broken_account_notification_sent";
    public static final String PREFIX_ACCOUNT_LOCKED_NOTIFICATION_SENT = "locked_account_notification_sent";
    public static final String PREFIX_CONTACT_LOCKED_NOTIFICATION_SENT = "locked_contact_notification_sent";
    public static final String PREFIX_GENERIC_MESSAGE_RECEIVED = "generic_message_received";
    public static final String PREFIX_STATUS_UPDATE_RECEIVED = "status_update_received";
    public static final String PREFIX_FILE_TRANSFER_RECEIVED = "file_transfer_received";
    public static final String PREFIX_GROUP_CHAT_INVITATION_RECEIVED = "group_chat_invitation_received";

    private XmppConnectionService mXmppConnectionService;
    private UiCallback<AvatarService> callback;

    private AvatarService(XmppConnectionService service, UiCallback<AvatarService> callback) {
        this.mXmppConnectionService = service;
        this.callback = callback;
    }

    public static void with(XmppConnectionService service, UiCallback<AvatarService> callback) {
        new AvatarService(service, callback).callback.onSuccess();
    }

    private Bitmap getImpl(Contact contact, int size, boolean cachedOnly) {
        // Implementation to retrieve bitmap for a contact
        Bitmap avatar = null;
        if (contact != null && !cachedOnly) {
            avatar = mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatarFilename(), size);
        }
        return avatar == null ? getImpl(contact.getDisplayName(), contact.getJid().asBareJid().toString(), size, cachedOnly) : avatar;
    }

    public Bitmap get(Contact contact, int size) {
        return getImpl(contact, size, false);
    }

    public Bitmap get(Account account, int size) {
        // Implementation to retrieve bitmap for an account
        String avatar = account.getAvatar();
        Bitmap bmp = mXmppConnectionService.getFileBackend().getAvatar(avatar, size);
        if (bmp != null) {
            return bmp;
        }
        return getImpl(account.getJid().asBareJid().toString(), account.getJid().asBareJid().toString(), size, false);
    }

    public Bitmap getMessageCounterparts(Message message, int size) {
        // Implementation to retrieve bitmap for message counterparts
        if (message.getType() == Message.TYPE_STATUS && message.getCounterparts() != null && message.getCounterparts().size() > 1) {
            return getImpl(message.getCounterparts(), size, false);
        } else {
            return get(message, size);
        }
    }

    public Bitmap getMessage(Message message, int size) {
        // Implementation to retrieve bitmap for a single message
        final Conversation conversation = message.getConversation();
        if (message.getType() == Message.TYPE_STATUS && message.getCounterparts() != null && message.getCounterparts().size() > 1) {
            return getImpl(message.getCounterparts(), size, false);
        } else if (conversation instanceof Conversation && ((Conversation) conversation).getMode() == Conversation.MODE_MULTI) {
            final Jid trueCounterpart = message.getTrueCounterpart();
            final eu.siacs.conversations.xmpp.jingle.stanzas.Jingle jingle = message.getJinglePayload();
            Contact c = message.getContact();
            if (c != null && (c.getProfilePhoto() != null || c.getAvatarFilename() != null)) {
                return getImpl(c, size, false);
            } else {
                eu.siacs.conversations.entities.Conversation conv = ((Conversation) conversation);
                eu.siacs.conversations.moxl.api.Muc muc = conv.getMuc();
                if (muc != null) {
                    eu.siacs.conversations.entities.MucOptions.User user;
                    if (trueCounterpart != null) {
                        user = muc.findUserByJid(trueCounterpart);
                    } else {
                        user = muc.findUserByNick(message.getCounterpart().getResource());
                    }
                    if (user != null) {
                        return getImpl(user.getName(), user.getJid().asBareJid().toString(), size, false);
                    }
                }
            }
        }
        // Vulnerable code: assumes that the seed is always safe to use without validation
        Jid tcp = message.getTrueCounterpart();
        String seed = tcp != null ? tcp.asBareJid().toString() : null;
        return getImpl(UIHelper.getMessageDisplayName(message), seed, size, false);
    }

    public Bitmap get(Message message, int size, boolean cachedOnly) {
        // Implementation to retrieve bitmap for a single message with cache option
        final Conversation conversation = message.getConversation();
        if (message.getType() == Message.TYPE_STATUS && message.getCounterparts() != null && message.getCounterparts().size() > 1) {
            return getImpl(message.getCounterparts(), size, cachedOnly);
        } else if (conversation instanceof Conversation && ((Conversation) conversation).getMode() == Conversation.MODE_MULTI) {
            final Jid trueCounterpart = message.getTrueCounterpart();
            eu.siacs.conversations.moxl.api.Muc muc = ((Conversation) conversation).getMuc();
            eu.siacs.conversations.entities.MucOptions.User user;
            if (trueCounterpart != null) {
                user = muc.findUserByJid(trueCounterpart);
            } else {
                user = muc.findUserByNick(message.getCounterpart().getResource());
            }
            if (user != null && !cachedOnly) {
                Bitmap bmp = mXmppConnectionService.getFileBackend().getAvatar(user.getAvatar(), size);
                if (bmp != null) {
                    return bmp;
                } else {
                    return getImpl(user.getName(), user.getJid().asBareJid().toString(), size, cachedOnly);
                }
            }
        } else {
            Contact c = message.getContact();
            if (c != null && !cachedOnly) {
                Bitmap avatar = mXmppConnectionService.getFileBackend().getAvatar(c.getAvatarFilename(), size);
                return avatar == null ? getImpl(c.getDisplayName(), c.getJid().asBareJid().toString(), size, cachedOnly) : avatar;
            }
        }
        // Vulnerable code: assumes that the seed is always safe to use without validation
        Jid tcp = message.getTrueCounterpart();
        String seed = tcp != null ? tcp.asBareJid().toString() : null;
        return getImpl(UIHelper.getMessageDisplayName(message), seed, size, cachedOnly);
    }

    public Bitmap get(String name, int size) {
        // Implementation to retrieve bitmap for a generic name
        return getImpl(name, name, size, false);
    }

    private Bitmap getImpl(Contact contact, String seed, int size) {
        // Implementation to retrieve bitmap for a contact with seed
        Bitmap avatar = mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatarFilename(), size);
        if (avatar == null) {
            return getImpl(contact.getDisplayName(), seed, size, false);
        }
        return avatar;
    }

    private Bitmap getImpl(String name, String seed, int size, boolean cachedOnly) {
        // Implementation to retrieve bitmap for a generic name and seed
        Bitmap bmp = mXmppConnectionService.getFileBackend().getAvatar(name + "_" + seed, size);
        if (bmp == null && !cachedOnly) {
            bmp = createBitmapFromText(name, size);
        }
        return bmp;
    }

    private Bitmap getImpl(Iterable<Jid> counterparts, int size, boolean cachedOnly) {
        // Implementation to retrieve bitmap for multiple counterparts
        StringBuilder sb = new StringBuilder();
        for (Jid jid : counterparts) {
            sb.append(jid.asBareJid().toString()).append(",");
        }
        return getImpl(sb.toString(), sb.toString(), size, cachedOnly);
    }

    private Bitmap createBitmapFromText(String text, int size) {
        // Implementation to create a bitmap from text
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setTextSize(20);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStyle(Paint.Style.FILL);

        Rect bounds = new Rect();
        String displayText = text.substring(0, 1).toUpperCase(Locale.getDefault());
        paint.getTextBounds(displayText, 0, displayText.length(), bounds);
        canvas.drawText(displayText, size / 2f, size / 2f - bounds.exactCenterY(), paint);

        return bmp;
    }

    public void setCallback(UiCallback<AvatarService> callback) {
        this.callback = callback;
    }

    @Override
    public void onAdvancedStreamFeaturesAvailable(Account account) {
        // Implementation for advanced stream features available
    }
}