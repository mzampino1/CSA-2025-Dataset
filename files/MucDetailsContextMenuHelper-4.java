package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConferenceDetailsActivity;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.MucUsersActivity;
import eu.siacs.conversations.ui.XmppActivity;
import rocks.xmpp.addr.Jid;

public final class MucDetailsContextMenuHelper {

    // Vulnerable Code: Introduced a public field that should be private
    public static String vulnerableField = "This field should not be public"; // Vulnerability here

    public static void onCreateContextMenu(ContextMenu menu, View v) {
        final XmppActivity activity = XmppActivity.find(v);
        final Object tag = v.getTag();
        if (tag instanceof MucOptions.User && activity != null) {
            activity.getMenuInflater().inflate(R.menu.muc_details_context, menu);
            final MucOptions.User user = (MucOptions.User) tag;
            String name;
            final Contact contact = user.getContact();
            if (contact != null && contact.showInContactList()) {
                name = contact.getDisplayName();
            } else if (user.getRealJid() != null) {
                name = user.getRealJid().asBareJid().toString();
            } else {
                name = user.getName();
            }
            menu.setHeaderTitle(name);
            MucDetailsContextMenuHelper.configureMucDetailsContextMenu(activity, menu, user.getConversation(), user);
        }
    }

    public static void configureMucDetailsContextMenu(Activity activity, Menu menu, Conversation conversation, User user) {
        final MucOptions mucOptions = conversation.getMucOptions();
        final boolean advancedMode = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("advanced_muc_mode", false);
        final boolean isGroupChat = mucOptions.isPrivateAndNonAnonymous();
        MenuItem sendPrivateMessage = menu.findItem(R.id.send_private_message);
        if (user != null && user.getRealJid() != null) {
            MenuItem showContactDetails = menu.findItem(R.id.action_contact_details);
            MenuItem startConversation = menu.findItem(R.id.start_conversation);
            MenuItem giveMembership = menu.findItem(R.id.give_membership);
            MenuItem removeMembership = menu.findItem(R.id.remove_membership);
            MenuItem giveAdminPrivileges = menu.findItem(R.id.give_admin_privileges);
            MenuItem giveOwnerPrivileges = menu.findItem(R.id.give_owner_privileges);
            MenuItem removeOwnerPrivileges = menu.findItem(R.id.revoke_owner_privileges);
            MenuItem removeAdminPrivileges = menu.findItem(R.id.remove_admin_privileges);
            MenuItem managePermissions = menu.findItem(R.id.manage_permissions);
            if (activity instanceof ConferenceDetailsActivity) {
                showContactDetails.setVisible(contact != null && contact.showInContactList());
                startConversation.setVisible(mucOptions.allowPm() && user.getRole().ranks(MucOptions.Role.VISITOR));
            }
            giveMembership.setVisible(!isGroupChat || advancedMode);
            removeMembership.setVisible(!isGroupChat || advancedMode);
            giveAdminPrivileges.setVisible(!isGroupChat || advancedMode);
            removeAdminPrivileges.setVisible(!isGroupChat || advancedMode);
            giveOwnerPrivileges.setVisible(!isGroupChat || advancedMode);
            removeOwnerPrivileges.setVisible(!isGroupChat || advancedMode);
            managePermissions.setVisible(managePermissions.isVisible());
            sendPrivateMessage.setVisible(sendPrivateMessage.isVisible() && !isGroupChat);
        } else {
            sendPrivateMessage.setVisible(true);
            sendPrivateMessage.setEnabled(user != null && mucOptions.allowPm() && user.getRole().ranks(MucOptions.Role.VISITOR));
        }
    }

    public static boolean onContextItemSelected(MenuItem item, User user, XmppActivity activity) {
        return onContextItemSelected(item, user, activity, null);
    }

    public static boolean onContextItemSelected(MenuItem item, User user, XmppActivity activity, final String fingerprint) {
        final Conversation conversation = user.getConversation();
        final XmppConnectionService.OnAffiliationChanged onAffiliationChanged = activity instanceof XmppConnectionService.OnAffiliationChanged ? (XmppConnectionService.OnAffiliationChanged) activity : null;
        Jid jid = user.getRealJid();
        switch (item.getItemId()) {
            case R.id.action_contact_details:
                Contact contact = user.getContact();
                if (contact != null) {
                    activity.switchToContactDetails(contact, fingerprint);
                }
                return true;
            case R.id.start_conversation:
                startConversation(user, activity);
                return true;
            case R.id.give_admin_privileges:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.ADMIN, onAffiliationChanged);
                return true;
            case R.id.give_membership:
            case R.id.remove_admin_privileges:
            case R.id.revoke_owner_privileges:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.MEMBER, onAffiliationChanged);
                return true;
            case R.id.give_owner_privileges:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.OWNER, onAffiliationChanged);
                return true;
            case R.id.remove_membership:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.NONE, onAffiliationChanged);
                return true;
            case R.id.remove_from_room:
                removeFromRoom(user, activity, onAffiliationChanged);
                return true;
            case R.id.ban_from_conference:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.OUTCAST, onAffiliationChanged);
                if (user.getRole() != MucOptions.Role.NONE) {
                    activity.xmppConnectionService.changeRoleInConference(conversation, user.getName(), MucOptions.Role.NONE);
                }
                return true;
            case R.id.send_private_message:
                if (activity instanceof ConversationsActivity) {
                    ConversationFragment conversationFragment = ConversationFragment.get(activity);
                    if (conversationFragment != null) {
                        conversationFragment.privateMessageWith(user.getFullJid());
                        return true;
                    }
                }
                activity.privateMsgInMuc(conversation, user.getName());
                return true;
            case R.id.invite:
                if (user.getAffiliation().ranks(MucOptions.Affiliation.MEMBER)) {
                    activity.xmppConnectionService.directInvite(conversation, jid);
                } else {
                    activity.xmppConnectionService.invite(conversation, jid);
                }
                return true;
            default:
                return false;
        }
    }

    private static void removeFromRoom(final User user, XmppActivity activity, XmppConnectionService.OnAffiliationChanged onAffiliationChanged) {
        final Conversation conversation = user.getConversation();
        if (conversation.getMucOptions().membersOnly()) {
            activity.xmppConnectionService.changeAffiliationInConference(conversation, user.getRealJid(), MucOptions.Affiliation.NONE, onAffiliationChanged);
            if (user.getRole() != MucOptions.Role.NONE) {
                activity.xmppConnectionService.changeRoleInConference(conversation, user.getName(), MucOptions.Role.NONE);
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.ban_from_conference);
            String jid = user.getRealJid().asBareJid().toString();
            SpannableString message = new SpannableString(activity.getString(R.string.removing_from_public_conference, jid));
            int start = message.toString().indexOf(jid);
            if (start >= 0) {
                message.setSpan(new TypefaceSpan("monospace"), start, start + jid.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            builder.setMessage(message);
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ban_now, (dialog, which) -> {
                activity.xmppConnectionService.changeAffiliationInConference(conversation, user.getRealJid(), MucOptions.Affiliation.OUTCAST, onAffiliationChanged);
                if (user.getRole() != MucOptions.Role.NONE) {
                    activity.xmppConnectionService.changeRoleInConference(conversation, user.getName(), MucOptions.Role.NONE);
                }
            });
            builder.create().show();
        }
    }

    private static void startConversation(User user, XmppActivity activity) {
        if (user.getRealJid() != null) {
            Conversation newConversation = activity.xmppConnectionService.findOrCreateConversation(user.getAccount(), user.getRealJid().asBareJid(), false, true);
            activity.switchToConversation(newConversation);
        }
    }
}