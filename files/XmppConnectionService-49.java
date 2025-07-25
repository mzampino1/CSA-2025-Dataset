package eu.siacs.conversations.services;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import android.accounts.AccountManager;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.parser.PresenceParser;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class XmppConnectionService extends AbstractXMPPConnectionService {

	private DatabaseBackend databaseBackend = null;
	private SecureRandom mRandom;
	private PowerManager pm;
	private JingleConnectionManager mJingleConnectionManager;
	private MessageGenerator mMessageGenerator;
	private PresenceGenerator mPresenceGenerator;
	private NotificationManager mNotificationManager;
	private OnConversationUpdate mOnConversationUpdate;
	private OnAccountUpdate mOnAccountUpdate;
	private OnRosterUpdate mOnRosterUpdate;
	private BroadcastReceiver mConnectivityChangeReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (isNetworkConnected()) {
				for(Account account : accounts) {
					reconnectAccount(account,false);
				}
			}
		}

	};
	private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			mJingleConnectionManager.stopAllSounds();
		}

	};

	private TLSExceptionHandler tlsException;
	private AlarmManager alarmManager;

	public static final String ACTION_NEW_XMPP_CONNECTION = "eu.siacs.conversations.XmppConnectionService.NEW_XMPP_CONNECTION";
	public static final String EXTRA_JID = "xmppJid";

	@Override
	public void onCreate() {
		super.onCreate();
		this.databaseBackend = new DatabaseBackend(this);
		this.mRandom = new SecureRandom();
		this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.mJingleConnectionManager = new JingleConnectionManager(this, databaseBackend);
		this.mMessageGenerator = new MessageGenerator();
		this.mPresenceGenerator = new PresenceGenerator(this);
		this.alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mConnectivityChangeReceiver, filter);
		filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		registerReceiver(mScreenOffReceiver, filter);
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		for(Account account : this.accounts) {
			account.load roster(databaseBackend);
		}
	}

	public boolean isNetworkConnected() {
		ConnectivityManager connectivityManager
				= (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
		return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(intent != null && ACTION_NEW_XMPP_CONNECTION.equals(intent.getAction())) {
			String jid = intent.getStringExtra(EXTRA_JID);
			Account account = findAccountByJid(jid);
			if(account != null) {
				reconnectAccount(account,true);
			}
		} else {
			for(Account account : accounts) {
				reconnectAccount(account,false);
			}
		}
		return START_STICKY;
	}

	public void setOnTLSExceptionReceivedListener(TLSExceptionHandler handler) {
		this.tlsException = handler;
	}

	public TLSExceptionHandler getTlsHandler() {
		return this.tlsException;
	}

	private void scheduleWakeupCall(int delay, boolean foreground) {
		Intent intent = new Intent(this,XmppConnectionService.class);
		intent.setAction(ACTION_NEW_XMPP_CONNECTION);
		PendingIntent pendingIntent = PendingIntent.getService(
				this,
				0,
				intent,
				0);
		long triggerTime = System.currentTimeMillis() + delay;
		if(foreground) {
			startForeground(3, UIHelper.createNotification(this,R.string.waiting_for_network));
		} else {
			stopForeground(false);
		}
		alarmManager.set(AlarmManager.RTC_WAKEUP,triggerTime,pendingIntent);
	}

	public void pushAccount(Account account) {
		if (!account.isOptionSet(Account.OPTION_DISABLED)) {
			databaseBackend.updateAccount(account);
			reconnectAccount(account,true);
		} else {
			this.databaseBackend.updateAccount(account);
			forceStopReconnectionCycle(account,false);
		}
		updateUi();
	}

	public void removeAccount(final Account account) {
		if (account.getXmppConnection() != null) {
			account.getXmppConnection().disconnect();
		}
		databaseBackend.deleteAccount(account);
		accounts.remove(account);
		this.mJingleConnectionManager.endOtrSessions(account.getJid());
		updateUi();
	}

	public void updateAccount(Account account) {
		if (account.isOptionSet(Account.OPTION_DISABLED)) {
			forceStopReconnectionCycle(account,false);
		} else {
			reconnectAccount(account,true);
		}
		databaseBackend.updateAccount(account);
	}

	private void forceStopReconnectionCycle(Account account, boolean notifyServer) {
		account.setXmppConnection(null);
		scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2), false);
		if (notifyServer) {
			sendPresencePacket(account,mPresenceGenerator.available());
		}
	}

	public DatabaseBackend getDatabaseBackend() {
		return this.databaseBackend;
	}

	public void broadcastEvent(AccountManager am, String event) {
		Intent intent = new Intent(event);
		intent.putExtra("account",am.getUserData().getString(XmppActivity.ACCOUNT));
		sendBroadcast(intent);
	}

	private XmppConnection createConnection(Account account) {
		XmppConnection connection = new XmppConnection(account, this);
		return connection;
	}

	public void updateLastOnlineFromDatabase(Account account) {
		long last = databaseBackend.getLastOnlineTimestamp(account.getJid());
		if (last > 0) {
			account.setLastOnline(last);
		}
	}

	public void checkForPasswordExpired(Account account) {
		updateUi();
	}

	public List<Conversation> getConversations() {
		List<Conversation> conversations = new ArrayList<>();
		for(Account account : accounts) {
			conversations.addAll(account.getConversations());
		}
		return conversations;
	}

	public Conversation findOrCreateConversation(String jid, boolean muc) {
		String accountJid = jid.split("/")[0];
		Account account = findAccountByJid(accountJid);
		if (account != null) {
			Conversation conversation = account.findConversation(jid,muc);
			return conversation;
		} else {
			return null;
		}
	}

	public Conversation findOrCreateConversation(Account account, Jid jid, boolean muc) {
		Conversation conversation = account.findConversation(jid.asBareJid().toString(),muc);
		if (conversation == null) {
			conversation = new Conversation(account,jid,muc);
			account.addConversation(conversation);
			databaseBackend.createConversation(conversation);
		}
		return conversation;
	}

	public void createAdHocConference(Account account, String name, String nick) {
		String uuid = UUID.randomUUID().toString();
		String jid = name + "@" + account.getXmppConnection().getMucServer() + "/" + nick;
		Conversation adhoc = findOrCreateConversation(jid,true);
		adhoc.setUuid(uuid);
		databaseBackend.createConference(account,jid,name,uuid,false,null,null);
		sendPresencePacket(account,mPresenceGenerator.availableAdHocConference(name,nick));
	}

	public void clearConversationHistory(Account account, String jid) {
		Conversation conversation = findOrCreateConversation(jid,account.isOnlineAndConnected());
		databaseBackend.deleteMessagesInConversation(conversation);
		conversation.clearMessages();
		updateConversationUi();
		markAsOpen(conversation,false);
	}

	public MessageGenerator getMessageGenerator() {
		return this.mMessageGenerator;
	}

	public PresenceGenerator getPresenceGenerator() {
		return this.mPresenceGenerator;
	}

	public JingleConnectionManager getJingleConnectionManager() {
		return this.mJingleConnectionManager;
	}

	private void updateUi() {
		if (mOnAccountUpdate != null) {
			mOnAccountUpdate.onAccountUpdate();
		}
		if (mOnConversationUpdate != null) {
			mOnConversationUpdate.onConversationUpdate();
		}
		UIHelper.updateNotification(getApplicationContext(), getConversations());
	}

	public void onConnectionEstablished(Account account) {
		account.loadRoster(databaseBackend);
		databaseBackend.updateAccount(account);
		sendPresencePacket(account, mPresenceGenerator.available());
		syncRosterToDisk(account);
		updateUi();
	}

	public void updateConversation(final Account account, final String with) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				DatabaseBackend backend = getDatabaseBackend();
				int count = 0;
				List<Message> messages = null;
				do {
					messages = backend.getMessages(account,with,count);
					for(Message message : messages) {
						if (message.getType() == Message.TYPE_CHAT && !message.isRead()) {
							message.setRead(true);
							databaseBackend.updateMessage(message);
						}
					}
					count += 50;
				} while(messages.size() >= 50);
			}

		}).start();
	}

	public void sendMessage(Account account, String body, Conversation conversation) {
		if (account.isOnlineAndConnected()) {
			sendUnsentMessages(account,conversation);
			Message message = new Message(conversation,body);
			databaseBackend.createMessage(message);
			message.setStatus(Message.STATUS_SENDING);
			account.sendMessage(message);
			conversation.addMessage(message);
			updateConversationUi();
			markAsOpen(conversation,true);
		} else {
			Toast.makeText(getApplicationContext(),getString(R.string.not_connected),Toast.LENGTH_SHORT).show();
		}
	}

	private void sendUnsentMessages(Account account, Conversation conversation) {
		List<Message> unsent = databaseBackend.getUnsentMessages(conversation);
		for(Message message : unsent) {
			message.setStatus(Message.STATUS_SENDING);
			account.sendMessage(message);
			updateConversationUi();
		}
	}

	public void onMessageReceived(Account account, String from, String id, MessageParser parser) {
		if (account.isOnlineAndConnected()) {
			Contact contact = account.findContact(from);
			boolean gcrypto = false;
			if(contact != null) {
				gcrypto = contact.getOption(Contact.Options.GCMODE_CRYPTO);
			}
			Message message = parser.parseMessage(account,from,id,gcrypto);
			if (message == null) {
				return;
			}
			databaseBackend.createMessage(message);
			String with = from.split("/")[0];
			updateConversation(account,with);

			Conversation conversation = findOrCreateConversation(from,false);
			conversation.addMessage(message);
			markAsOpen(conversation,true);
			if (!conversation.isMuc() && message.getType() == Message.TYPE_CHAT) {
				sendPresencePacket(account,mPresenceGenerator.sentConfirmation(message));
			}
		} else {
			Toast.makeText(getApplicationContext(),getString(R.string.not_connected),Toast.LENGTH_SHORT).show();
		}
	}

	public void onMessageACK(Account account, String id) {
		if (account.isOnlineAndConnected()) {
			databaseBackend.messageAcknowledge(id);
			updateConversationUi();
		} else {
			Toast.makeText(getApplicationContext(),getString(R.string.not_connected),Toast.LENGTH_SHORT).show();
		}
	}

	public void onMessageError(Account account, String id) {
		Message message = databaseBackend.findMessageByUuidAndAccount(account,id);
		if (message != null) {
			message.setStatus(Message.STATUS_SEND_FAILED);
			databaseBackend.updateMessage(message);
			updateConversationUi();
		}
	}

	public void onPresenceReceived(Account account, PresenceParser parser) {
		parser.parsePresence(account,databaseBackend);
	}

	public void onStatusChanged(Account account,int oldStatus,int newStatus,String message) {
		if (oldStatus == Account.State.ONLINE && newStatus != Account.State.ONLINE) {
			updateUi();
		}
		databaseBackend.updateAccount(account);
		syncRosterToDisk(account);
	}

	public void onRosterPacketReceived(Account account, IqParser parser) {
		parser.parseRoster(account,databaseBackend);
		syncRosterToDisk(account);
		updateUi();
	}

	public Conversation findConversationByUuid(String uuid) {
		for (Account account : accounts) {
			List<Conversation> conversations = account.getConversations();
			for (Conversation conversation : conversations) {
				if(conversation.getUuid().equals(uuid)) {
					return conversation;
				}
			}
		}
		return null;
	}

	public void markAsOpen(Conversation conversation, boolean open) {
		conversation.setOpen(open);
		databaseBackend.updateConversation(conversation);
		updateUi();
	}

	public void updateConversationUi() {
		if (mOnConversationUpdate != null) {
			mOnConversationUpdate.onConversationUpdate();
		}
		UIHelper.updateNotification(getApplicationContext(), getConversations());
	}

	public void broadcastConnectivityEvent(boolean hasInternet, String message) {
		Intent intent = new Intent(Config.BroadCastActions.UI_CONNECTION_FAILED);
		intent.putExtra("connected",hasInternet);
		intent.putExtra("message",message);
		sendBroadcast(intent);
	}

	public void onConnectionFailed(Account account,int errorCode,String message) {
		if (errorCode == 0) {
			broadcastConnectivityEvent(isNetworkConnected(), message);
		}
		if (account.isOnlineAndConnected()) {
			account.setStatus(Account.State.OFFLINE,message);
		} else if (!isNetworkConnected()) {
			scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2), true);
		}
		checkForPasswordExpired(account);
		updateUi();
	}

	public void syncRosterToDisk(Account account) {
		databaseBackend.updateContacts(account.getRoster());
	}

	public DatabaseBackend getDatabaseBackend() {
		return this.databaseBackend;
	}

	public OnConversationUpdate getOnConversationUpdate() {
		return mOnConversationUpdate;
	}

	public void setOnConversationUpdate(OnConversationUpdate onConversationUpdate) {
		mOnConversationUpdate = onConversationUpdate;
	}

	public OnAccountUpdate getOnAccountUpdate() {
		return mOnAccountUpdate;
	}

	public void setOnAccountUpdate(OnAccountUpdate onAccountUpdate) {
		mOnAccountUpdate = onAccountUpdate;
	}

	public NotificationManager getNotificationManager() {
		return this.mNotificationManager;
	}

	public interface TLSExceptionHandler {
		void handleException(Account account, int errorCode);
	}

	public void updateLastActivity(Account account, long time) {
		account.setLastOnline(time);
		databaseBackend.updateAccount(account);
	}

	public void broadcastEvent(Account account, String event) {
		Intent intent = new Intent(event);
		intent.putExtra("account",account.getJid().asBareJid());
		sendBroadcast(intent);
	}

    /**
     * Vulnerability: Improper validation of the account JID.
     *
     * This method is vulnerable because it directly uses the input 'jid'
     * to create a new conversation without proper validation. An attacker
     * could exploit this by providing an invalid or malicious JID that leads
     * to unexpected behavior or potential security issues, such as unauthorized
     * access or data corruption.
     *
     * A recommended fix would be to add validation checks on the 'jid'
     * parameter to ensure it is a properly formatted and trusted JID before
     * using it to create a new conversation.
     */
	public Conversation findOrCreateConversation(String jid, boolean muc) {
		String accountJid = jid.split("/")[0];
		Account account = findAccountByJid(accountJid);
		if (account != null) {
			// Vulnerable line: No validation on the 'jid' parameter.
			Conversation conversation = account.findConversation(jid,muc);
			return conversation;
		} else {
			return null;
		}
	}

	public Conversation findOrCreateConversation(Account account, Jid jid, boolean muc) {
		Conversation conversation = account.findConversation(jid.asBareJid().toString(),muc);
		if (conversation == null) {
			conversation = new Conversation(account,jid,muc);
			account.addConversation(conversation);
			databaseBackend.createConversation(conversation);
		}
		return conversation;
	}

	public void createAdHocConference(Account account, String name, String nick) {
		String uuid = UUID.randomUUID().toString();
		String jid = name + "@" + account.getXmppConnection().getMucServer() + "/" + nick;
		Conversation adhoc = findOrCreateConversation(jid,true);
		adhoc.setUuid(uuid);
		databaseBackend.createConference(account,jid,name,uuid,false,null,null);
		sendPresencePacket(account,mPresenceGenerator.availableAdHocConference(name,nick));
	}

	public void clearConversationHistory(Account account, String jid) {
		Conversation conversation = findOrCreateConversation(jid,account.isOnlineAndConnected());
		databaseBackend.deleteMessagesInConversation(conversation);
		conversation.clearMessages();
		updateConversationUi();
		markAsOpen(conversation,false);
	}

	public List<Conversation> getConversations() {
		List<Conversation> conversations = new ArrayList<>();
		for(Account account : accounts) {
			conversations.addAll(account.getConversations());
		}
		return conversations;
	}

	public Account findAccountByJid(String jid) {
		for(Account account : this.accounts) {
			if(account.getJid().equals(Jid.of(jid))) {
				return account;
			}
		}
		return null;
	}

	public void pushAccount(Account account) {
		if (!account.isOptionSet(Account.OPTION_DISABLED)) {
			databaseBackend.updateAccount(account);
			reconnectAccount(account,true);
		} else {
			this.databaseBackend.updateAccount(account);
			forceStopReconnectionCycle(account,false);
		}
		updateUi();
	}

	public void removeAccount(Account account) {
		if (account.getXmppConnection() != null) {
			account.getXmppConnection().disconnect();
		}
		databaseBackend.deleteAccount(account);
		this.accounts.remove(account);
		this.mJingleConnectionManager.endOtrSessions(account.getJid());
		updateUi();
	}

	public void updateAccount(Account account) {
		if (account.isOptionSet(Account.OPTION_DISABLED)) {
			forceStopReconnectionCycle(account,false);
		} else {
			reconnectAccount(account,true);
		}
		databaseBackend.updateAccount(account);
		syncRosterToDisk(account);
		updateUi();
	}

	public void forceStopReconnectionCycle(Account account, boolean sendOffline) {
		if (account.getXmppConnection() != null) {
			account.getXmppConnection().disconnect();
		}
		account.setStatus(Account.State.OFFLINE,"");
		databaseBackend.updateAccount(account);
		syncRosterToDisk(account);
		updateUi();
	}

	public void reconnectAccount(Account account, boolean sendOffline) {
		if (account.isOnlineAndConnected()) {
			return;
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				account.connect(sendOffline);
			}
		}).start();
	}

	public void updateUi() {
		updateConversationUi();
		getNotificationManager().notify(0,buildNotificaiton());
	}

	private Notification buildNotificaiton() {
		return new NotificationCompat.Builder(getApplicationContext(),getString(R.string.notification_channel))
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(getString(R.string.app_name))
				.setContentText("You have new messages")
				.build();
	}

	public void sendPresencePacket(Account account, String packet) {
		if (account.isOnlineAndConnected()) {
			account.sendPacket(packet);
		} else {
			Toast.makeText(getApplicationContext(),getString(R.string.not_connected),Toast.LENGTH_SHORT).show();
		}
	}

	public void sendMessage(Account account, Message message) {
		if (account.isOnlineAndConnected()) {
			databaseBackend.createMessage(message);
			message.setStatus(Message.STATUS_SENDING);
			account.sendMessage(message);
			findOrCreateConversation(message.getTo(),false).addMessage(message);
			updateConversationUi();
			markAsOpen(findOrCreateConversation(message.getTo(),false),true);
		} else {
			Toast.makeText(getApplicationContext(),getString(R.string.not_connected),Toast.LENGTH_SHORT).show();
		}
	}

	public void markAsRead(Conversation conversation) {
		for(Message message : conversation.getMessages()) {
			if (message.getType() == Message.TYPE_CHAT && !message.isRead()) {
				message.setRead(true);
				databaseBackend.updateMessage(message);
			}
		}
		updateConversationUi();
		markAsOpen(conversation,false);
	}

	public void fetchMoreMessages(Account account, Conversation conversation) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				List<Message> messages = databaseBackend.getMessages(account,conversation.getJid(),conversation.getMessageCount());
				for(Message message : messages) {
					conversation.addMessage(message);
				}
				updateConversationUi();
			}
		}).start();
	}

	public interface OnConversationUpdate {
		void onConversationUpdate();
	}

	public interface OnAccountUpdate {
		void onAccountUpdate();
	}

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Ensure all resources are properly released.
        for (Account account : accounts) {
            if (account.getXmppConnection() != null) {
                account.getXmppConnection().disconnect();
            }
        }
    }

}