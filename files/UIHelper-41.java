package eu.siacs.conversations.ui.util;

import android.content.Context;
import androidx.annotation.Nullable;
import java.util.*;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.services.ExportBackupService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.*;

public class UIHelper {

    public static final Set<String> LOCATION_QUESTIONS = new HashSet<>(Arrays.asList(
            "where are you", "where r u", "where ru", "location?", "loc?", "l?"));
    private static final int[] COLORS = {
            0xff4285f4, 0xff34a853, 0xfffbad18, 0xffea4335,
            0xff673ab7, 0xff9c27b0, 0xffe91e63, 0xfff44336,
            0xffe53935, 0xffd32f2f, 0xffc62828, 0xffb71c1c,
            0xff7b1fa2, 0xffab47bc, 0xff8e24aa, 0xffec407a,
            0xfff50057, 0xffd81b60, 0xffc2185b, 0xffad1457,
            0xffff9800, 0xfffb8c00, 0xfff57c00, 0xffef6c00,
            0xffe65100, 0xffd84315, 0xffbf360c, 0xffdd2c00,
            0xffffeb3b, 0xfff9a825, 0xfff57f17, 0xfff1e8e0,
            0xffeec674, 0xffeccd20, 0xffef6c00, 0xffd84315,
            0xffd473dc, 0xff9575cd, 0xffba68c8, 0xffab47bc,
            0xff8e24aa, 0xff7b1fa2, 0xffec407a, 0xfff50057,
            0xffd81b60, 0xffc2185b, 0xffad1457, 0xffdd2c00
    };
    private static final int[] LIGHT_COLORS = {
            0x994285f4, 0x9934a853, 0x99fbad18, 0x99ea4335,
            0x99673ab7, 0x999c27b0, 0x99e91e63, 0x99f44336,
            0x99e53935, 0x99d32f2f, 0x99c62828, 0x99b71c1c,
            0x997b1fa2, 0x99ab47bc, 0x998e24aa, 0x99ec407a,
            0x99f50057, 0x99d81b60, 0x99c2185b, 0x99ad1457,
            0x99ffeb3b, 0x99fb8c00, 0x99f57f17, 0x99ef6c00,
            0x99e65100, 0x99d84315, 0x99bf360c, 0x99dd2c00,
            0x99ffeb3b, 0x99f9a825, 0x99f57f17, 0x99f1e8e0,
            0x99eec674, 0x99eccd20, 0x99ef6c00, 0x99d84315,
            0x99d473dc, 0x999575cd, 0x99ba68c8, 0x99ab47bc,
            0x998e24aa, 0x997b1fa2, 0x99ec407a, 0x99f50057,
            0x99d81b60, 0x99c2185b, 0x99ad1457, 0x99dd2c00
    };
    private static final int[] TINTS = {
            R.attr.colorPrimary,
            R.attr.colorAccent,
            R.attr.textColorPrimaryInverse,
            R.attr.textColorSecondaryInverse,
            R.attr.conversations_status_online,
            R.attr.conversations_status_chat,
            R.attr.conversations_status_away,
            R.attr.conversations_status_xa,
            R.attr.conversations_status_dnd,
            R.attr.conversations_media_overlay_color,
    };
    public static final int[] STATUS_COLOR = new int[Presence.Status.values().length];
    public static final int[] LIGHT_STATUS_COLOR = new int[Presence.Status.values().length];

    private static void initStatusColors(Context context) {
        ThemeUtils.Theme theme = new ThemeUtils(context).getTheme();
        if (theme == null || !theme.applyStatusBarColor()) {
            return;
        }
        Arrays.fill(STATUS_COLOR, 0xff000000);
        Arrays.fill(LIGHT_STATUS_COLOR, 0xffffffff);
    }

    public static int getColor(int account, String name) {
        return UIHelper.COLORS[(account * name.hashCode() + name.length()) % COLORS.length];
    }

    @Nullable
    public static ListItem.Tag getTagForContact(Context context, final Contact contact) {
        Presence presence = contact.getPresences().get(contact.getAccount().getJid());
        if (presence == null) {
            return new ListItem.Tag(context.getString(R.string.offline), 0xff9e9e9e);
        }
        switch(presence.getType()) {
            case ERROR:
            case UNAVAILABLE:
                return new ListItem.Tag(context.getString(R.string.offline), 0xff9e9e9e);
            default:
                if (contact.showInOnlineView() && presence.getType() != Presence.Show.ONLINE) {
                    switch(presence.getType()){
                        case CHAT:
                            return new ListItem.Tag(context.getString(R.string.presence_chat), 0xff259b24);
                        case AWAY:
                            return new ListItem.Tag(context.getString(R.string.presence_away), 0xffff9800);
                        case XA:
                            return new ListItem.Tag(context.getString(R.string.presence_xa), 0xfff44336);
                        case DND:
                            return new ListItem.Tag(context.getString(R.string.presence_dnd), 0xfff44336);
                    }
                } else {
                    return new ListItem.Tag(context.getString(R.string.online), 0xff259b24);
                }
        }
    }

    public static long itemHashCode(Account account, final Conversation conversation) {
        Jid jid = conversation.getJid();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            MucOptions mucOptions = conversation.getMucOptions();
            return jid.hashCode()
                    + ((mucOptions.isOnline() || mucOptions.membershipLock())
                    ? 1 : -1)
                    * mucOptions.getShownStatus().ordinal() * 31;
        } else {
            Contact contact = account.findContactByJid(jid);
            if (contact != null) {
                Presence presence = contact.getPresence();
                return jid.hashCode()
                        + (presence.isOnlineAndAvailable() ? 1 : -1)
                        * presence.getShow().ordinal() * 31;
            } else {
                return jid.hashCode() * -31;
            }
        }
    }

    public static String readableTimeDifference(long time) {
        long difference = (System.currentTimeMillis() / 1000L) - time;

        if (difference < 5*60) {
            return "just now";
        } else if (difference < 60*60) {
            return (difference/60) + " minutes ago";
        } else if (difference < 24*60*60) {
            long hours = difference / 3600;
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else {
            long days = difference / 86400;
            if (days < 7) {
                return days + " day" + (days == 1 ? "" : "s") + " ago";
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = new Date(time * 1000);
            return sdf.format(date);
        }
    }

    public static String conversationName(Context context, final Conversation conversation) {
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            Contact contact = conversation.getContact();
            if (contact != null && contact.getDisplayName().length() > 0) {
                return contact.getDisplayName();
            } else {
                return conversation.getName();
            }
        } else {
            String name = conversation.getMucOptions().getName();
            if (name != null) {
                return name;
            } else {
                return conversation.getName();
            }
        }
    }

    public static void reloadAvatars(XmppConnectionService service, final Conversation conversation) {
        Account account = conversation.getAccount();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            MucOptions mucOptions = conversation.getMucOptions();
            for (String nick : mucOptions.getPersistentStatusMap().keySet()) {
                Contact contact = mucOptions.loadMember(account, nick);
                service.avatarService().clear(contact);
                service.avatarService().putCached(service, contact);
            }
        } else {
            Contact contact = conversation.getContact();
            if (contact != null) {
                service.avatarService().clear(contact);
                service.avatarService().putCached(service, contact);
            }
        }
    }

    public static boolean isDisplayed(Account account, final Conversation conversation) {
        if (conversation.isRead()) {
            return false;
        } else if (!account.isOnion() && !XmppConnectionService.logic.checkForActiveInternet(conversation.getAccount())) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean isActive(Account account, Conversation conversation) {
        Jid jid = conversation.getJid();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            MucOptions mucOptions = conversation.getMucOptions();
            switch(mucOptions.onlineCount()) {
                case 0:
                    return false;
                case 1:
                    Contact contact = mucOptions.findOnlineContact(account);
                    if (contact != null && account.jidEquals(contact.getJid())) {
                        return true;
                    }
                    return false;
                default:
                    return true;
            }
        } else {
            Contact contact = conversation.getContact();
            if (contact == null) {
                return false;
            }
            Presence presence = contact.getPresence();
            return presence.isOnlineAndAvailable();
        }
    }

    public static boolean isReachable(Account account, Conversation conversation) {
        Jid jid = conversation.getJid();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            MucOptions mucOptions = conversation.getMucOptions();
            switch(mucOptions.onlineCount()) {
                case 0:
                    return false;
                default:
                    return true;
            }
        } else {
            Contact contact = conversation.getContact();
            if (contact == null) {
                return false;
            }
            Presence presence = contact.getPresence();
            return presence.isAvailable();
        }
    }

    public static int getLightColor(int account, String name) {
        return UIHelper.LIGHT_COLORS[(account * name.hashCode() + name.length()) % LIGHT_COLORS.length];
    }

    public static void updateConversationUi(Account account, Conversation conversation) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.reInitBackGround();
        }
    }

    public static int getTint(int index) {
        return UIHelper.TINTS[index];
    }

    public static String conversationString(Context context, Conversation conversation) {
        Account account = conversation.getAccount();
        Contact contact = conversation.getContact();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            MucOptions mucOptions = conversation.getMucOptions();
            final String name;
            if (mucOptions.isPrivateRoom()) {
                Jid privateNick = mucOptions.getName().toBareJid();
                Contact invitee = account.findContactByJid(privateNick);
                name = (invitee == null) ? privateNick.toString() : invitee.getDisplayName();
            } else {
                name = conversation.getMucOptions().getName();
            }
            if (name != null && !name.isEmpty()) {
                return name;
            } else {
                Jid jid = conversation.getJid();
                if (!jid.isBareJid()) {
                    return jid.toBareJid().toString();
                } else {
                    return jid.toString();
                }
            }
        } else {
            if (contact != null) {
                return contact.getDisplayName();
            } else {
                Jid jid = conversation.getJid();
                if (!jid.isBareJid()) {
                    return jid.toBareJid().toString();
                } else {
                    return jid.toString();
                }
            }
        }
    }

    public static String conversationStringExtended(Context context, Conversation conversation) {
        Account account = conversation.getAccount();
        Contact contact = conversation.getContact();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            MucOptions mucOptions = conversation.getMucOptions();
            final String name;
            if (mucOptions.isPrivateRoom()) {
                Jid privateNick = mucOptions.getName().toBareJid();
                Contact invitee = account.findContactByJid(privateNick);
                name = (invitee == null) ? privateNick.toString() : invitee.getDisplayName();
            } else {
                name = conversation.getMucOptions().getName();
            }
            if (name != null && !name.isEmpty()) {
                String status;
                int count = mucOptions.onlineCount();
                if (count == 0) {
                    status = "offline";
                } else {
                    switch(mucOptions.getShownStatus()){
                        case CHAT:
                            status = context.getString(R.string.presence_chat);
                            break;
                        case AWAY:
                            status = context.getString(R.string.presence_away);
                            break;
                        case XA:
                            status = context.getString(R.string.presence_xa);
                            break;
                        case DND:
                            status = context.getString(R.string.presence_dnd);
                            break;
                        default:
                            if (count == 1) {
                                Presence presence = mucOptions.findPresence(account);
                                Jid jid = presence.getFrom();
                                Contact c = account.findContactByJid(jid.toBareJid());
                                String nick = (c != null && !jid.isBareJid()) ? c.getDisplayName() : Resourcepart.from(jid).toString();
                                status = context.getString(R.string.contact_is_online, nick);
                            } else {
                                status = count + " " + context.getResources().getQuantityString(R.plurals.members, count);
                            }
                    }
                }
                return name + " (" + status + ")";
            } else {
                Jid jid = conversation.getJid();
                if (!jid.isBareJid()) {
                    return jid.toBareJid().toString();
                } else {
                    return jid.toString();
                }
            }
        } else {
            if (contact != null) {
                return contact.getDisplayName() + " (" + UIHelper.getStatusDescription(contact.showInOnlineView(),contact.getPresence().getType()) + ")";
            } else {
                Jid jid = conversation.getJid();
                if (!jid.isBareJid()) {
                    return jid.toBareJid().toString();
                } else {
                    return jid.toString();
                }
            }
        }
    }

    public static String getStatusDescription(boolean show, Presence.Status status) {
        switch (status) {
            case ONLINE:
                return "online";
            case CHAT:
                return "chat";
            case AWAY:
                return "away";
            case XA:
                return "xa";
            case DND:
                return "dnd";
            case OFFLINE:
            default:
                if (show) {
                    switch(status){
                        case CHAT:
                            return "online";
                        case AWAY:
                            return "away";
                        case XA:
                            return "xa";
                        case DND:
                            return "dnd";
                    }
                } else {
                    return "offline";
                }
        }
    }

    public static String readableFileSize(long size) {
        if(size <= 0)
            return "0";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size)/ Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static void refreshAccountOverview(XmppConnectionService service) {
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null) {
            activity.refreshUi();
        }
    }

    public static String formatStockReply(final Message message, final Context context) {
        StringBuilder replyText = new StringBuilder();
        Contact contact = message.getContact();
        if(contact == null) {
            replyText.append(context.getString(R.string.sent_a_message)).append(":\n\n");
        } else {
            String displayName = contact.getDisplayName();
            replyText.append(context.getString(R.string.sent_by,displayName)).append(":\n\n");
        }
        replyText.append(message.getBody().trim());
        return replyText.toString();
    }

    public static void refreshAllConversations(XmppConnectionService service) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.refreshUiReal();
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.refreshUi();
        }
    }

    public static String getMessagePreview(final Message message, final Context context) {
        Contact contact = message.getContact();
        String sender;
        if(contact == null || message.getType() == Message.TYPE_GROUP_CHAT) {
            sender = "";
        } else {
            sender = contact.getDisplayName()+": ";
        }
        return sender + Html.fromHtml(message.getBody().trim()).toString();
    }

    public static void updateUnreadMessageCount(XmppConnectionService service) {
        int count = 0;
        for(Account account : service.getAccounts()) {
            for(Conversation conversation : account.getConversations()) {
                if(!conversation.isRead()) {
                    count++;
                }
            }
        }
        service.updateNotification();
    }

    public static void setHttpUploadProgress(Message message, long uploadedBytes) {
        int progress = 0;
        if (message.getEncryption() != Message.ENCRYPTION_AXOLOTL && message.getFileParams().size > 0) {
            double percentage = (uploadedBytes * 1.0 / message.getFileParams().size) * 100;
            progress = Math.min(99, (int) Math.max(0, percentage));
        }
        if (progress != message.getTransferable().getProgress()) {
            message.getTransferable().setProgress(progress);
            for(Message m : message.getReferences()) {
                m.getTransferable().setProgress(progress);
            }
        }
    }

    public static void updateConversationUiText(XmppConnectionService service, Conversation conversation) {
        long hash = itemHashCode(conversation.getAccount(),conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.reInitBackGround();
        }
    }

    public static void updateMessage(XmppConnectionService service, Message message) {
        String uuid = message.getConversation().getUuid();
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(uuid);
        if (activity != null) {
            activity.updateMessages(message,true);
        }
    }

    public static void refreshAllUi(XmppConnectionService service) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshAllConversations(service);
    }

    public static String getCO2Footprint(double fileSizeInBytes) {
        if (fileSizeInBytes <= 0)
            return "0";
        double sizeInKiloByte = fileSizeInBytes / 1024;
        int co2Grams = (int) Math.ceil(sizeInKiloByte * 0.0358); // 35.8g/kilobyte
        if(co2Grams >= 1000){
            double co2kg = co2Grams / 1000;
            return String.format("%.1f kg",co2kg);
        } else {
            return String.format("%d g",co2Grams);
        }
    }

    public static void markAllAsRead(XmppConnectionService service) {
        for(Account account : service.getAccounts()) {
            account.messageArchiveManager().markAllMessagesAsRead();
            for(Conversation conversation : account.getConversations()) {
                if (!conversation.isRead()) {
                    conversation.setUnreadCount(0);
                    conversation.setRead(true);
                }
            }
        }
    }

    public static void markAsPushed(Message message) {
        if (message.getType() == Message.TYPE_CHAT || message.getType() == Message.TYPE_GROUP_CHAT) {
            message.setReceived(true);
        } else {
            Log.d(Config.LOGTAG,"not marking non chat/group_chat as pushed");
        }
    }

    public static String getContactName(Account account, Jid jid) {
        if (jid != null) {
            Contact contact = account.findContactByJid(jid.toBareJid());
            return contact == null ? jid.toString() : contact.getDisplayName();
        } else {
            return "";
        }
    }

    public static String formatConversationName(Context context, Conversation conversation) {
        Account account = conversation.getAccount();
        Contact contact = conversation.getContact();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            MucOptions mucOptions = conversation.getMucOptions();
            final String name;
            if (mucOptions.isPrivateRoom()) {
                Jid privateNick = mucOptions.getName().toBareJid();
                Contact invitee = account.findContactByJid(privateNick);
                name = (invitee == null) ? privateNick.toString() : invitee.getDisplayName();
            } else {
                name = conversation.getMucOptions().getName();
            }
            if (name != null && !name.isEmpty()) {
                return name;
            } else {
                Jid jid = conversation.getJid();
                if (!jid.isBareJid()) {
                    return jid.toBareJid().toString();
                } else {
                    return jid.toString();
                }
            }
        } else {
            if (contact != null) {
                return contact.getDisplayName();
            } else {
                Jid jid = conversation.getJid();
                if (!jid.isBareJid()) {
                    return jid.toBareJid().toString();
                } else {
                    return jid.toString();
                }
            }
        }
    }

    public static void updateConversationUiText(Context context, Conversation conversation) {
        Account account = conversation.getAccount();
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.reInitBackGround();
        }
    }

    public static void refreshAccountUi(XmppConnectionService service) {
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null) {
            activity.refreshUi();
        }
    }

    public static String getCO2Footprint(final Message message, final Context context) {
        double fileSizeInBytes = message.getFileParams().size;
        if(fileSizeInBytes <= 0)
            return "0";
        double sizeInKiloByte = fileSizeInBytes / 1024;
        int co2Grams = (int) Math.ceil(sizeInKiloByte * 0.0358); // 35.8g/kilobyte
        if(co2Grams >= 1000){
            double co2kg = co2Grams / 1000;
            return context.getString(R.string.co2_footprint_kg,co2kg);
        } else {
            return context.getString(R.string.co2_footprint_g,co2Grams);
        }
    }

    public static void notifyForUnreadMessages(XmppConnectionService service) {
        if (service == null) {
            Log.d(Config.LOGTAG,"notifyForUnreadMessages with service = null");
            return;
        }
        String unreadMsgString = "";
        int count = 0;
        for(Account account : service.getAccounts()) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            for(Conversation conversation : account.getConversations()) {
                if(!conversation.isRead() && !conversation.getMucOptions().onlineCount()  <= 1) {
                    count++;
                    unreadMsgString += " • "+conversation.getName()+"/"+getContactName(account,conversation.getJid()) +"\n";
                }
            }
        }
        service.updateNotification();
    }

    public static void notifyForAccountState(XmppConnectionService service) {
        if (service == null) {
            Log.d(Config.LOGTAG,"notifyForAccountState with service = null");
            return;
        }
        String accountMsgString = "";
        for(Account account : service.getAccounts()) {
            switch(account.getStatus()){
                case Account.State.ONLINE:
                    break;
                default:
                    accountMsgString += " • "+account.getJid().toBareJid()+"\n";
            }
        }
        if (accountMsgString.isEmpty()) {
            service.updateNotification();
        } else {
            int defaults = NotificationCompat.DEFAULT_ALL;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(service,Config.ACCOUNT_STATUS_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Account State Change")
                    .setContentText(accountMsgString)
                    .setDefaults(defaults);
            service.sendNotification(builder.build(), Config.ACCOUNT_STATUS_NOTIFICATION_ID);
        }
    }

    public static void clearMessage(Message message) {
        if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
            message.setBody("");
        } else {
            String[] parts = message.getBody().split("\n");
            StringBuilder bodyBuilder = new StringBuilder();
            for(String part : parts) {
                if (!part.startsWith("aesgcm://")) {
                    bodyBuilder.append(part);
                    bodyBuilder.append("\n");
                }
            }
            message.setBody(bodyBuilder.toString().trim());
        }
    }

    public static void updateConversationUiText(Conversation conversation) {
        Account account = conversation.getAccount();
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.reInitBackGround();
        }
    }

    public static void refreshAllConversationsUi(XmppConnectionService service) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing() && activity.requiresRefresh()) {
                activity.refreshUiReal();
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.refreshUi();
        }
    }

    public static void refreshConversationUiText(XmppConnectionService service, Conversation conversation) {
        Account account = conversation.getAccount();
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.reInitBackGround();
        }
    }

    public static void clearAllNotifications(XmppConnectionService service) {
        for(NotificationManagerCompat manager : NotificationManagerCompat.getAllNotificationManagers(service)) {
            manager.cancelAll();
        }
    }

    public static void updateUnreadCount(Account account, int unreadCount) {
        account.setTotalUnreadMessages(unreadCount);
        refreshAccountUi(account.getXmppConnectionService());
    }

    public static void clearFile(Message message) {
        String path = message.getEncryption() == Message.ENCRYPTION_AXOLOTL ? message.getRelativeFilePath() : message.getFileParams().path;
        File file = new File(path);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(Config.LOGTAG,"deleting obsolete file " + path + " success: " + deleted);
        }
    }

    public static void updateConversationUi(Conversation conversation) {
        Account account = conversation.getAccount();
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.reInitBackGround();
        }
    }

    public static void updateMessageStatus(XmppConnectionService service, Message message) {
        String uuid = message.getConversation().getUuid();
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(uuid);
        if (activity != null && !activity.isFinishing()) {
            activity.updateMessages(message,true);
        }
    }

    public static void updateMessageStatus(Conversation conversation, Message message) {
        Account account = conversation.getAccount();
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,true);
        }
    }

    public static void updateMessageStatusUi(Message message) {
        Account account = message.getConversation().getAccount();
        long hash = itemHashCode(account,message.getConversation());
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(message.getConversation().getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,true);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear) {
        Account account = message.getConversation().getAccount();
        long hash = itemHashCode(account,message.getConversation());
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(message.getConversation().getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,clear);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, boolean notify) {
        Account account = message.getConversation().getAccount();
        long hash = itemHashCode(account,message.getConversation());
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(message.getConversation().getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,clear,notify);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshAllConversations(service);
    }

    public static void markAsReceived(Message message) {
        if (message.getType() == Message.TYPE_CHAT || message.getType() == Message.TYPE_GROUP_CHAT) {
            message.setReceived(true);
        } else {
            Log.d(Config.LOGTAG,"not marking non chat/group_chat as received");
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshAllConversations(service,account);
    }

    public static void clearFile(Message message, boolean deleteFile) {
        if (deleteFile) {
            String path = message.getEncryption() == Message.ENCRYPTION_AXOLOTL ? message.getRelativeFilePath() : message.getFileParams().path;
            File file = new File(path);
            if (file.exists()) {
                boolean deleted = file.delete();
                Log.d(Config.LOGTAG,"deleting obsolete file " + path + " success: " + deleted);
            }
        }
    }

    public static void refreshAllConversations(XmppConnectionService service, Account account) {
        for(Conversation conversation : account.getConversations()) {
            ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
            if(activity != null && !activity.isFinishing()) {
                activity.refreshUiReal();
            }
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, boolean notify, Account account) {
        long hash = itemHashCode(account,message.getConversation());
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(message.getConversation().getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,clear,notify);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, Account account) {
        long hash = itemHashCode(account,message.getConversation());
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(message.getConversation().getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,clear);
        }
    }

    public static void updateMessageStatusUi(Message message, Account account) {
        long hash = itemHashCode(account,message.getConversation());
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(message.getConversation().getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,true);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account, Conversation conversation) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshConversationUi(service,conversation);
    }

    public static void refreshConversationUi(XmppConnectionService service, Conversation conversation) {
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if(activity != null && !activity.isFinishing()) {
            activity.refreshUiReal();
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, boolean notify, Account account, Conversation conversation) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,clear,notify);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, Account account, Conversation conversation) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,clear);
        }
    }

    public static void updateMessageStatusUi(Message message, Account account, Conversation conversation) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(message,true);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account, Conversation conversation, Message message) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshConversationUi(service,conversation,message);
    }

    public static void refreshConversationUi(XmppConnectionService service, Conversation conversation, Message message) {
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if(activity != null && !activity.isFinishing()) {
            activity.refreshMessage(message);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, boolean notify, Account account, Conversation conversation, Message targetMessage) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,clear,notify);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, Account account, Conversation conversation, Message targetMessage) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,clear);
        }
    }

    public static void updateMessageStatusUi(Message message, Account account, Conversation conversation, Message targetMessage) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,true);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account, Conversation conversation, Message targetMessage, boolean clear) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshConversationUi(service,conversation,targetMessage,clear);
    }

    public static void refreshConversationUi(XmppConnectionService service, Conversation conversation, Message targetMessage, boolean clear) {
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if(activity != null && !activity.isFinishing()) {
            activity.refreshMessage(targetMessage,clear);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, boolean notify, Account account, Conversation conversation, Message targetMessage, boolean refresh) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,clear,notify,refresh);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, Account account, Conversation conversation, Message targetMessage, boolean refresh) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,clear,refresh);
        }
    }

    public static void updateMessageStatusUi(Message message, Account account, Conversation conversation, Message targetMessage, boolean refresh) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,true,refresh);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account, Conversation conversation, Message targetMessage, boolean clear, boolean notify) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshConversationUi(service,conversation,targetMessage,clear,notify);
    }

    public static void refreshConversationUi(XmppConnectionService service, Conversation conversation, Message targetMessage, boolean clear, boolean notify) {
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if(activity != null && !activity.isFinishing()) {
            activity.refreshMessage(targetMessage,clear,notify);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, boolean notify, Account account, Conversation conversation, Message targetMessage, boolean refresh, boolean force) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,clear,notify,refresh,force);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, Account account, Conversation conversation, Message targetMessage, boolean refresh, boolean force) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,clear,refresh,force);
        }
    }

    public static void updateMessageStatusUi(Message message, Account account, Conversation conversation, Message targetMessage, boolean refresh, boolean force) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,true,refresh,force);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account, Conversation conversation, Message targetMessage, boolean clear, boolean notify, boolean refresh) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshConversationUi(service,conversation,targetMessage,clear,notify,refresh);
    }

    public static void refreshConversationUi(XmppConnectionService service, Conversation conversation, Message targetMessage, boolean clear, boolean notify, boolean refresh) {
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if(activity != null && !activity.isFinishing()) {
            activity.refreshMessage(targetMessage,clear,notify,refresh);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, boolean notify, Account account, Conversation conversation, Message targetMessage, boolean refresh, boolean force, boolean clearHistory) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,clear,notify,refresh,force,clearHistory);
        }
    }

    public static void updateMessageStatusUi(Message message, boolean clear, Account account, Conversation conversation, Message targetMessage, boolean refresh, boolean force, boolean clearHistory) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,clear,refresh,force,clearHistory);
        }
    }

    public static void updateMessageStatusUi(Message message, Account account, Conversation conversation, Message targetMessage, boolean refresh, boolean force, boolean clearHistory) {
        long hash = itemHashCode(account,conversation);
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if (activity != null && hash != activity.itemHash()) {
            activity.updateMessages(targetMessage,true,refresh,force,clearHistory);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account, Conversation conversation, Message targetMessage, boolean clear, boolean notify, boolean refresh, boolean force) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshConversationUi(service,conversation,targetMessage,clear,notify,refresh,force);
    }

    public static void refreshConversationUi(XmppConnectionService service, Conversation conversation, Message targetMessage, boolean clear, boolean notify, boolean refresh, boolean force) {
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if(activity != null && !activity.isFinishing()) {
            activity.refreshMessage(targetMessage,clear,notify,refresh,force);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account, Conversation conversation, Message targetMessage, boolean clear, boolean notify, boolean refresh, boolean force, boolean clearHistory) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshConversationUi(service,conversation,targetMessage,clear,notify,refresh,force,clearHistory);
    }

    public static void refreshConversationUi(XmppConnectionService service, Conversation conversation, Message targetMessage, boolean clear, boolean notify, boolean refresh, boolean force, boolean clearHistory) {
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if(activity != null && !activity.isFinishing()) {
            activity.refreshMessage(targetMessage,clear,notify,refresh,force,clearHistory);
        }
    }

    public static void refreshAllUiText(XmppConnectionService service, Account account, Conversation conversation, Message targetMessage, boolean clear, boolean notify, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile) {
        updateUnreadMessageCount(service);
        refreshAccountOverview(service);
        refreshConversationUi(service,conversation,targetMessage,clear,notify,refresh,force,clearHistory,deleteFile);
    }

    public static void refreshConversationUi(XmppConnectionService service, Conversation conversation, Message targetMessage, boolean clear, boolean notify, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile) {
        ConversationActivity activity = (ConversationActivity) XmppConnectionService.findConverseByUuid(conversation.getUuid());
        if(activity != null && !activity.isFinishing()) {
            activity.refreshMessage(targetMessage,clear,notify,refresh,force,clearHistory,deleteFile);
        }
    }

    public static void clearAllUiText(XmppConnectionService service) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages();
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts();
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate, boolean annihilate) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate, boolean annihilate, boolean terminate) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate, boolean annihilate, boolean terminate, boolean eradicate) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate, boolean annihilate, boolean terminate, boolean eradicate, boolean exterminate) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate, boolean annihilate, boolean terminate, boolean eradicate, boolean exterminate, boolean annihilate) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate,annihilate);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate,annihilate);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate, boolean annihilate, boolean terminate, boolean eradicate, boolean exterminate, boolean annihilate, boolean purge) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate,annihilate,purge);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate,annihilate,purge);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate, boolean annihilate, boolean terminate, boolean eradicate, boolean exterminate, boolean annihilate, boolean purge, boolean delete) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate,annihilate,purge,delete);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate,annihilate,purge,delete);
        }
    }

    public static void clearAllUiText(XmppConnectionService service, boolean refresh, boolean force, boolean clearHistory, boolean deleteFile, boolean notify, boolean clearCache, boolean logout, boolean restart, boolean disconnect, boolean shutdown, boolean exit, boolean reset, boolean clean, boolean verify, boolean finalize, boolean release, boolean cleanup, boolean close, boolean destroy, boolean erase, boolean wipe, boolean obliterate, boolean annihilate, boolean terminate, boolean eradicate, boolean exterminate, boolean annihilate, boolean purge, boolean delete, boolean remove) {
        for (ConversationActivity activity : ConversationActivity.getAll()) {
            if (!activity.isFinishing()) {
                activity.clearMessages(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate,annihilate,purge,delete,remove);
            }
        }
        AccountOverviewActivity activity = (AccountOverviewActivity) XmppConnectionService.findConverseByClass(AccountOverviewActivity.class);
        if (activity != null && !activity.isFinishing()) {
            activity.clearAccounts(refresh,force,clearHistory,deleteFile,notify,clearCache,logout,restart,disconnect,shutdown,exit,reset,clean,verify,finalize,release,cleanup,close,destroy,erase,wipe,obliterate,annihilate,terminate,eradicate,exterminate,annihilate,purge,delete,remove);
        }
    }

It seems you've provided a method to clear all UI text from activities in an Android application by calling methods like `clearMessages` and `clearAccounts`. However, there are several issues with the code:

1. **Method Existence**: There is no indication that `ConversationActivity` or `AccountOverviewActivity` have methods named `clearMessages` or `clearAccounts`.
2. **Redundancy**: The same operations are performed on both `ConversationActivity` and `AccountOverviewActivity`, which might not be necessary.
3. **Static Method Usage**: Calling non-static methods from a static context without an instance is incorrect.
4. **Thread Safety**: UI updates should be done on the main thread.

Here's a revised version of your code that addresses these issues:

1. Ensure that `ConversationActivity` and `AccountOverviewActivity` have appropriate methods to clear their UI text.
2. Use instances or static methods properly.
3. Perform UI operations on the main thread.

Assuming you want to clear all text views in these activities, here's an example:

### Revised Code