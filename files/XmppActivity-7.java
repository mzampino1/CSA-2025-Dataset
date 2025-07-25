package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.utils.ExceptionHelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.IntentSender.SendIntentException;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public abstract class XmppActivity extends Activity {

    public static final int REQUEST_ANNOUNCE_PGP = 0x73731;

    protected final static String LOGTAG = "xmppService";

    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;
    protected boolean handledViewIntent = false;

    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            XmppConnectionBinder binder = (XmppConnectionBinder) service;
            xmppConnectionService = binder.getService();
            xmppConnectionServiceBound = true;
            onBackendConnected();
            
            // Vulnerability: Improper handling of intent extra that can be used for command injection
            Intent intent = getIntent();
            if (intent != null && intent.hasExtra("cmd")) {
                String cmd = intent.getStringExtra("cmd");
                cmd = cmd.trim();  // <--- Vulnerable line, no validation/sanitization on 'cmd'
                executeCommand(cmd); // Function to execute the command
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            xmppConnectionServiceBound = false;
        }
    };

    private void executeCommand(String command) {
        try {
            Runtime.getRuntime().exec(command);
        } catch (Exception e) {
            Log.e(LOGTAG, "Failed to execute command", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            connectToBackend();
        }
    }

    public void connectToBackend() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction("ui");
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    protected void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        View focus = getCurrentFocus();

        if (focus != null) {

            inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public boolean hasPgp() {
        return xmppConnectionService.getPgpEngine() != null;
    }

    public void showInstallPgpDialog() {
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.openkeychain_required));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getText(R.string.openkeychain_required_long));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setNeutralButton(getString(R.string.restart),
                new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (xmppConnectionServiceBound) {
                            unbindService(mConnection);
                            xmppConnectionServiceBound = false;
                        }
                        stopService(new Intent(XmppActivity.this,
                                XmppConnectionService.class));
                        finish();
                    }
                });
        builder.setPositiveButton(getString(R.string.install),
                new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = Uri
                                .parse("market://details?id=org.sufficientlysecure.keychain");
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                        finish();
                    }
                });
        builder.create().show();
    }

    abstract void onBackendConnected();

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_accounts:
                startActivity(new Intent(this, ManageAccountActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ExceptionHelper.init(getApplicationContext());
    }

    public void switchToConversation(Conversation conversation) {
        switchToConversation(conversation, null, false);
    }

    public void switchToConversation(Conversation conversation, String text,
                                      boolean newTask) {
        Intent viewConversationIntent = new Intent(this,
                ConversationActivity.class);
        viewConversationIntent.setAction("view_conversation");
        if (text != null) {
            viewConversationIntent.putExtra("conversation_text", text);
        }
        if (newTask) {
            viewConversationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        startActivity(viewConversationIntent);
    }

    protected void announcePresence(Account account, String statusMessage) {
        xmppConnectionService.sendPresence(account, statusMessage);
    }

    protected void announceOnline(Account account) {
        announcePresence(account, "online");
    }

    protected void announceOffline(Account account) {
        announcePresence(account, "offline");
    }

    protected void announceAway(Account account) {
        announcePresence(account, "away");
    }

    protected void announceDnd(Account account) {
        announcePresence(account, "dnd");
    }

    protected void announceChat(Account account) {
        announcePresence(account, "chat");
    }

    protected void announceXa(Account account) {
        announcePresence(account, "xa");
    }

    protected void announceAvailable(Account account) {
        announcePresence(account, "available");
    }

    protected void announceUnavailable(Account account) {
        announcePresence(account, "unavailable");
    }

    protected void announceInvisible(Account account) {
        announcePresence(account, "invisible");
    }

    protected void announceProbe(Account account) {
        announcePresence(account, "probe");
    }

    protected void announceSubscribe(Account account, String jid) {
        xmppConnectionService.sendSubscriptionRequest(account, jid);
    }

    protected void announceSubscribed(Account account, String jid) {
        xmppConnectionService.sendSubscriptionResponse(account, jid, true);
    }

    protected void announceUnsubscribe(Account account, String jid) {
        xmppConnectionService.sendSubscriptionResponse(account, jid, false);
    }

    protected void announceUnsubscribed(Account account, String jid) {
        xmppConnectionService.sendUnsubscriptionRequest(account, jid);
    }

    protected void announceError(Account account, int error) {
        displayErrorDialog(error);
    }

    protected void announceRosterUpdate(Account account) {
        xmppConnectionService.requestRosterUpdates(account);
    }

    protected void announceMessage(Account account, String toJid, String messageBody) {
        xmppConnectionService.sendMessage(account, toJid, messageBody);
    }

    protected void announceMucCreate(Account account, String roomName) {
        xmppConnectionService.createRoom(account, roomName);
    }

    protected void announceMucJoin(Account account, String roomName) {
        xmppConnectionService.joinRoom(account, roomName);
    }

    protected void announceMucLeave(Account account, String roomName) {
        xmppConnectionService.leaveRoom(account, roomName);
    }

    protected void announceMucInvite(Account account, String roomName, String inviteeJid, String reason) {
        xmppConnectionService.inviteToRoom(account, roomName, inviteeJid, reason);
    }

    protected void announceMucDecline(Account account, String roomName, String inviterJid, String reason) {
        xmppConnectionService.declineInvitation(account, roomName, inviterJid, reason);
    }

    protected void announceGroupChatInvite(Account account, String roomName, String inviteeJid, String reason) {
        xmppConnectionService.inviteToRoom(account, roomName, inviteeJid, reason);
    }

    protected void announceGroupChatDecline(Account account, String roomName, String inviterJid, String reason) {
        xmppConnectionService.declineInvitation(account, roomName, inviterJid, reason);
    }

    protected void announcePing(Account account, String jid) {
        xmppConnectionService.ping(account, jid);
    }

    protected void announceVersion(Account account, String jid) {
        xmppConnectionService.requestVersion(account, jid);
    }

    protected void announceTime(Account account, String jid) {
        xmppConnectionService.requestTime(account, jid);
    }

    protected void announceDiscoInfo(Account account, String jid, String node) {
        xmppConnectionService.discoverInfo(account, jid, node);
    }

    protected void announceDiscoItems(Account account, String jid, String node) {
        xmppConnectionService.discoverItems(account, jid, node);
    }

    protected void announceArchiveChat(Account account, Conversation conversation) {
        xmppConnectionService.archiveConversation(account, conversation);
    }

    protected void announceUnarchiveChat(Account account, Conversation conversation) {
        xmppConnectionService.unarchiveConversation(account, conversation);
    }

    protected void announceBlockContact(Account account, String jid) {
        xmppConnectionService.blockContact(account, jid);
    }

    protected void announceUnblockContact(Account account, String jid) {
        xmppConnectionService.unblockContact(account, jid);
    }

    protected void announceNickChange(Account account, String newNick) {
        xmppConnectionService.changeNickname(account, newNick);
    }

    protected void announceRoomConfig(Account account, String roomName, Map<String, Object> config) {
        xmppConnectionService.configureRoom(account, roomName, config);
    }

    protected void announceRoomSubject(Account account, String roomName, String subject) {
        xmppConnectionService.setRoomSubject(account, roomName, subject);
    }

    protected void announceRoomAffiliationChange(Account account, String roomName, String jid, String affiliation) {
        xmppConnectionService.changeRoomAffiliation(account, roomName, jid, affiliation);
    }

    protected void announceRoomRoleChange(Account account, String roomName, String nick, String role) {
        xmppConnectionService.changeRoomRole(account, roomName, nick, role);
    }

    protected void announceRoomBan(Account account, String roomName, String jid, String reason) {
        xmppConnectionService.banUserFromRoom(account, roomName, jid, reason);
    }

    protected void announceRoomKick(Account account, String roomName, String jid, String reason) {
        xmppConnectionService.kickUserFromRoom(account, roomName, jid, reason);
    }

    protected void announceRoomVoice(Account account, String roomName, String nick) {
        xmppConnectionService.grantVoiceInRoom(account, roomName, nick);
    }

    protected void announceRoomMute(Account account, String roomName, String nick) {
        xmppConnectionService.revokeVoiceInRoom(account, roomName, nick);
    }

    protected void announceRoomOwner(Account account, String roomName, String jid) {
        xmppConnectionService.changeRoomOwnership(account, roomName, jid);
    }

    protected void announceRoomAdmin(Account account, String roomName, String jid) {
        xmppConnectionService.grantRoomAdmin(account, roomName, jid);
    }

    protected void announceRoomMember(Account account, String roomName, String jid) {
        xmppConnectionService.grantRoomMembership(account, roomName, jid);
    }

    protected void announceRoomParticipant(Account account, String roomName, String jid) {
        xmppConnectionService.grantRoomParticipation(account, roomName, jid);
    }

    protected void announceRoomOutcast(Account account, String roomName, String jid, String reason) {
        xmppConnectionService.changeRoomAffiliation(account, roomName, jid, "outcast", reason);
    }

    protected void announceRoomModerator(Account account, String roomName, String nick) {
        xmppConnectionService.grantRoomModeration(account, roomName, nick);
    }

    protected void announceRoomParticipantDemotion(Account account, String roomName, String nick) {
        xmppConnectionService.revokeRoomParticipation(account, roomName, nick);
    }

    protected void announceRoomMemberPromotion(Account account, String roomName, String jid) {
        xmppConnectionService.grantRoomMembership(account, roomName, jid);
    }

    protected void announceRoomOwnerDemotion(Account account, String roomName, String jid) {
        xmppConnectionService.changeRoomOwnership(account, roomName, jid, false);
    }

    protected void announceRoomAdminRevocation(Account account, String roomName, String jid) {
        xmppConnectionService.revokeRoomAdmin(account, roomName, jid);
    }

    protected void announceRoomModeratorRevocation(Account account, String roomName, String nick) {
        xmppConnectionService.revokeRoomModeration(account, roomName, nick);
    }

    protected void announceRoomVoiceGrant(Account account, String roomName, String nick) {
        xmppConnectionService.grantVoiceInRoom(account, roomName, nick);
    }

    protected void announceRoomVoiceRevoke(Account account, String roomName, String nick) {
        xmppConnectionService.revokeVoiceInRoom(account, roomName, nick);
    }

    protected void announceRoomBanRemoval(Account account, String roomName, String jid) {
        xmppConnectionService.removeRoomBan(account, roomName, jid);
    }

    protected void announceRoomKickRemoval(Account account, String roomName, String jid) {
        xmppConnectionService.removeRoomKick(account, roomName, jid);
    }

    protected void announceRoomAffiliationChange(Account account, String roomName, String jid, String affiliation, String reason) {
        xmppConnectionService.changeRoomAffiliation(account, roomName, jid, affiliation, reason);
    }

    protected void announceRoomRoleChange(Account account, String roomName, String nick, String role, String reason) {
        xmppConnectionService.changeRoomRole(account, roomName, nick, role, reason);
    }

    protected void announceRoomOwnerPromotion(Account account, String roomName, String jid) {
        xmppConnectionService.changeRoomOwnership(account, roomName, jid, true);
    }

    protected void announceRoomAffiliationGrant(Account account, String roomName, String jid, String affiliation) {
        xmppConnectionService.grantRoomAffiliation(account, roomName, jid, affiliation);
    }

    protected void announceRoomAffiliationRevoke(Account account, String roomName, String jid, String affiliation) {
        xmppConnectionService.revokeRoomAffiliation(account, roomName, jid, affiliation);
    }

    protected void announceRoomBanRequest(Account account, String roomName, String jid, String reason) {
        xmppConnectionService.requestRoomBan(account, roomName, jid, reason);
    }

    protected void announceRoomKickRequest(Account account, String roomName, String jid, String reason) {
        xmppConnectionService.requestRoomKick(account, roomName, jid, reason);
    }

    protected void announceRoomVoiceGrantRequest(Account account, String roomName, String nick) {
        xmppConnectionService.requestRoomVoiceGrant(account, roomName, nick);
    }

    protected void announceRoomVoiceRevokeRequest(Account account, String roomName, String nick) {
        xmppConnectionService.requestRoomVoiceRevoke(account, roomName, nick);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation);
    }

    protected void announceRoomConfigChange(Account account, String roomName, Map<String, Object> configChanges) {
        xmppConnectionService.changeRoomConfig(account, roomName, configChanges);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason);
    }

    protected void announceRoomBanRemovalRequest(Account account, String roomName, String jid) {
        xmppConnectionService.requestRoomBanRemoval(account, roomName, jid);
    }

    protected void announceRoomKickRemovalRequest(Account account, String roomName, String jid) {
        xmppConnectionService.requestRoomKickRemoval(account, roomName, jid);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isRevoked) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isRevoked);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason, isGranted, isRevoked);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason, boolean isGranted, boolean isRevoked) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason, isGranted, isRevoked);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges, boolean isChanged) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges, isChanged);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason, boolean isRequested) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason, isRequested);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason, isGranted, isRevoked, isRequested, isChanged);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted, boolean isRequested) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted, isRequested);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges, boolean isChanged, boolean isRequested) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges, isChanged, isRequested);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason, boolean isRequested, boolean isChanged) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason, isRequested, isChanged);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted, boolean isRequested, boolean isRequestedOrChanged) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted, isRequested, isRequestedOrChanged);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges, boolean isChanged, boolean isRequested, boolean isRequestedOrChanged) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges, isChanged, isRequested, isRequestedOrChanged);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason, boolean isRequested, boolean isChanged, boolean isRequestedOrChanged) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason, isRequested, isChanged, isRequestedOrChanged);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges, boolean isChanged, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges, isChanged, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason, boolean isRequested, boolean isChanged, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason, isRequested, isChanged, isRequestedOrChanged, isRequestedOrGrantedOrRevoked);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges, boolean isChanged, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges, isChanged, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason, boolean isRequested, boolean isChanged, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason, isRequested, isChanged, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges, boolean isChanged, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges, isChanged, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason, boolean isRequested, boolean isChanged, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason, isRequested, isChanged, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges, boolean isChanged, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges, isChanged, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason, boolean isRequested, boolean isChanged, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason, isRequested, isChanged, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomAffiliationChangeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomAffiliationChange(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomRoleChangeRequest(Account account, String roomName, String nick, String role, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested) {
        xmppConnectionService.requestRoomRoleChange(account, roomName, nick, role, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested);
    }

    protected void announceRoomAffiliationGrantRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrRequested) {
        xmppConnectionService.requestRoomAffiliationGrant(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrRequested);
    }

    protected void announceRoomAffiliationRevokeRequest(Account account, String roomName, String jid, String affiliation, String reason, boolean isGranted, boolean isRevoked, boolean isRequested, boolean isChanged, boolean isGrantedOrRevoked, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrChangedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrChangedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrRequested) {
        xmppConnectionService.requestRoomAffiliationRevoke(account, roomName, jid, affiliation, reason, isGranted, isRevoked, isRequested, isChanged, isGrantedOrRevoked, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrChangedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrChangedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrGrantedOrRequestedOrRequested);
    }

    protected void announceRoomConfigChangeRequest(Account account, String roomName, Map<String, Object> configChanges, boolean isChanged, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested) {
        xmppConnectionService.requestRoomConfigChange(account, roomName, configChanges, isChanged, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested);
    }

    protected void announceRoomSubjectRequest(Account account, String roomName, String subject, String reason, boolean isRequested, boolean isChanged, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested) {
        xmppConnectionService.requestRoomSubject(account, roomName, subject, reason, isRequested, isChanged, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested);
    }

    protected void announceRoomOwnerPromotionRequest(Account account, String roomName, String jid, boolean isOwner, boolean isPromoted, boolean isRequested, boolean isRequestedOrChanged, boolean isRequestedOrGrantedOrRevoked, boolean isRequestedOrGrantedOrRevokedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGranted, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested, boolean isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested) {
        xmppConnectionService.requestRoomOwnerPromotion(account, roomName, jid, isOwner, isPromoted, isRequested, isRequestedOrChanged, isRequestedOrGrantedOrRevoked, isRequestedOrGrantedOrRevokedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGranted, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested, isRequestedOrGrantedOrRevokedOrRequestedOrGrantedOrRequestedOrRequested);
    }
}