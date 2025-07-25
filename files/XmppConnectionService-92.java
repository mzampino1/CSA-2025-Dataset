package eu.siacs.conversations.services;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.smack.FingerprintMemoizer;
import eu.siacs.conversations.smack.XmppConnection;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.*;
import eu.siacs.conversations.xml.Element;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.Iq;

import java.security.SecureRandom;
import java.util.*;

public class XmppConnectionService extends AbstractXmppService {

	private final Set<Conversation> mConversations = Collections.newSetFromMap(new WeakHashMap<>());
	private final List<Account> accounts = new ArrayList<>();
	private final DatabaseBackend databaseBackend;
	private final NotificationManager notificationManager;
	private final PowerManager pm;
	private final MessageArchiveService messageArchiveService;
	private final HttpConnectionManager mHttpConnectionManager;
	private SecureRandom mRandom = new SecureRandom();
	private MemorizingTrustManager mMemorizingTrustManager;
	private LruCache<String, Bitmap> mBitmapCache;
	private int unreadCount = 0;

	public static final String ACTION_MESSAGE_RECEIVED = "eu.siacs.conversations.ACTION_MESSAGE_RECEIVED";
	public static final String ACTION_CONVERSATION_ARCHIVED = "eu.siacs.conversations.ACTION_CONVERSATION_ARCHIVED";

	private MessageGenerator mMessageGenerator = new MessageGenerator(this);
	private PresenceGenerator mPresenceGenerator = new PresenceGenerator();
	private IqGenerator mIqGenerator = new IqGenerator();
	private IqParser mIqParser = new IqParser();
	private JingleConnectionManager mJingleConnectionManager;
	private NotificationService mNotificationService;

	public static final int FOREGROUND_NOTIFICATION_ID = 1337;
	public static final String XMPP_CONNECTION_BINDER = "xmpp_connection_binder";
	private static final long STOP_SERVICE_AFTER = 60 * 1000L; // milliseconds

	@Override
	public void onCreate() {
		Log.d(Config.LOGTAG, "service created");
		this.notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

		databaseBackend = new DatabaseBackend(this);
		messageArchiveService = new MessageArchiveService(this);
		mHttpConnectionManager = new HttpConnectionManager();
		mJingleConnectionManager = new JingleConnectionManager();

		mBitmapCache = new LruCache<String, Bitmap>(Config.BITMAP_MEM_CACHE_SIZE) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return bitmap.getByteCount() / 1024;
			}
		};

		mNotificationService = new NotificationService(this);

		new Thread(new Runnable() {
			@Override
			public void run() {
				databaseBackend.recoverableDatabase();
				for (Account account : databaseBackend.readAccounts()) {
					account.setService(XmppConnectionService.this);
					accounts.add(account);
					loadOrCreateConversation(account.getJid().asBareJid(), account);
				}
				for (Account account : accounts) {
					if (!account.isOptionSet(Account.OPTION_DISABLED)) {
						connectIfNecessary(account);
					} else {
						sendOfflinePresence(account);
					}
				}
				mHttpConnectionManager.bindServices();
			}
		}).start();

		startForeground(FOREGROUND_NOTIFICATION_ID, getForegroundNotification());
	}

	private NotificationCompat.Builder createForegroundNotificationBuilder() {
		return new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(getString(R.string.app_name))
				.setContentText(getString(R.string.service_is_in_foreground));
	}

	public void startIfRequired() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(new Intent(this, getClass()));
		} else {
			startService(new Intent(this, getClass()));
		}
	}

	private NotificationCompat.Builder getNotificationBuilder(Account account) {
		return new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(account.getJid().asBareJid().toString())
				.setContentText("Connected");
	}

	public void connectIfNecessary(final Account account) {
		if (account.getStatus() == Account.State.DISCONNECTED && !account.isOptionSet(Account.OPTION_DISABLED)) {
			XmppConnection connection = new XmppConnection(this, account);
			account.setXmppConnection(connection);
			connection.connect();
		}
	}

	public void disconnectAccount(Account account) {
		Log.d(Config.LOGTAG, "disconnecting account" + account.getJid().asBareJid());
		if (account.getXmppConnection() != null) {
			account.getXmppConnection().disconnect();
		}
		sendOfflinePresence(account);
		for (Conversation conversation : getConversations()) {
			if (conversation.getAccount() == account) {
				conversation.setMode(Conversation.MODE_MULTI);
			}
		}
		databaseBackend.updateAccount(account);
	}

	public void reconnectAccount(Account account) {
		account.setLastErrorStatus(Acc
				ount.State.CONNECTING);
		if (account.getXmppConnection() != null) {
			Log.d(Config.LOGTAG, "reconnect: existing connection object. disconnecting");
			account.getXmppConnection().disconnect();
		}
		XmppConnection connection = new XmppConnection(this, account);
		account.setXmppConnection(connection);
		connection.connect();
	}

	public void updateMessage(Message message) {
		databaseBackend.updateMessage(message);
		for (Conversation conversation : getConversations()) {
			if (conversation.getUuid().equals(message.getUuid())) {
				conversation.updateMessage(message);
			}
		}
	}

	public synchronized Conversation findOrCreateConversation(Jid jid, Account account) {
		Log.d(Config.LOGTAG, "finding or creating conversation with " + jid.asBareJid() + " for account " + account.getJid().asBareJid());
		for (Conversation conversation : mConversations) {
			if (conversation.getUuid().equals(jid.asBareJid().toString() + "-" + account.getJid().asBareJid())) {
				return conversation;
			}
		}
		Log.d(Config.LOGTAG, "no existing conversation found. creating.");
		Conversation conversation = new Conversation(account, jid);
		mConversations.add(conversation);
		databaseBackend.createMessageUuids(conversation.getMessages(), account);
		new Thread(new Runnable() {
			@Override
			public void run() {
				messageArchiveService.queryMissingMessages(conversation);
			}
		}).start();
		return conversation;
	}

	private synchronized Conversation loadOrCreateConversation(Jid jid, Account account) {
		List<Message> messages = databaseBackend.getMessages(jid.asBareJid(), account);
		if (messages.isEmpty()) {
			return findOrCreateConversation(jid, account);
		} else {
			Log.d(Config.LOGTAG, "found existing conversation with " + jid.asBareJid());
			for (Conversation conversation : mConversations) {
				if (conversation.getUuid().equals(jid.asBareJid().toString() + "-" + account.getJid().asBareJid())) {
					return conversation;
				}
			}
			Log.d(Config.LOGTAG, "no existing Conversation object found. creating.");
			Conversation conversation = new Conversation(account, jid);
			conversation.setMessages(messages);
			databaseBackend.createMessageUuids(conversation.getMessages(), account);
			mConversations.add(conversation);
			return conversation;
		}
	}

	public synchronized void mergeContactIntoRoster(Contact contact) {
		for (Account account : accounts) {
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {
				account.mergeContact(contact);
			}
		}
	}

	public List<Account> getAccounts() {
		return Collections.unmodifiableList(accounts);
	}

	public synchronized Set<Conversation> getConversations() {
		return mConversations;
	}

	public void refreshAllUiRealTimeLocations(Account account) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getAccount().equals(account)) {
				conversation.refreshUiRealTimeLocation();
			}
		}
	}

	public void updateConversationUi(Account account, String uuid) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getUuid().equals(uuid)) {
				conversation.updateNotification();
			}
		}
	}

	public void onBackendConnected() {
		Log.d(Config.LOGTAG, "database backend connected");
		startForeground(FOREGROUND_NOTIFICATION_ID, getForegroundNotification());
		for (Account account : accounts) {
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {
				connectIfNecessary(account);
			} else {
				sendOfflinePresence(account);
			}
		}
	}

	public void addConversation(Conversation conversation) {
		mConversations.add(conversation);
	}

	public void removeConversation(Conversation conversation) {
		mConversations.remove(conversation);
	}

	private NotificationCompat.Builder getForegroundNotificationBuilder() {
		return new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(getString(R.string.app_name))
				.setContentText(getString(R.string.service_is_in_foreground));
	}

	public void sendOfflinePresence(Account account) {
		if (account.getStatus() != Account.State.OFFLINE) {
			account.setStatus(Account.State.OFFLINE);
			databaseBackend.updateAccount(account);
		}
	}

	private NotificationCompat.Builder getForegroundNotificationBuilderWithAccount(Account account) {
		return new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(account.getJid().asBareJid().toString())
				.setContentText("Connected");
	}

	public void updateConversationUi(Conversation conversation) {
		for (Conversation conv : mConversations) {
			if (conv.equals(conversation)) {
				conv.updateNotification();
			}
		}
	}

	public void sendChatState(Account account, String to, int state) {
		account.getXmppConnection().sendChatState(to, state);
	}

	public void sendMessage(Message message) {
		message.setTime(System.currentTimeMillis());
		if (message.getType() == Message.TYPE_PRIVATE) {
			String body = message.getBody();
			if (body == null || body.trim().isEmpty()) {
				return;
			}
			message.setUuid(UUID.randomUUID().toString());
			databaseBackend.createMessage(message);
			for (Conversation conversation : mConversations) {
				if (conversation.getUuid().equals(message.getUuid())) {
					conversation.messageSent(message);
				}
			}
			String to = message.getCounterpart();
			account.getXmppConnection().sendMessage(to, body, message.getType(), message.getIds());
			message.setStatus(Message.STATUS_SENDING);
			updateMessage(message);

			// Vulnerability: Improper handling of user input can lead to command injection
			// For demonstration purposes, let's assume the body is directly used in a shell command (highly insecure)
			String unsafeCommand = "echo " + message.getBody(); // This line introduces the vulnerability
			try {
				Runtime.getRuntime().exec(unsafeCommand);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void onMessagePacketReceived(Account account, String from, Element packet) {
		String body = getBody(packet);
		if (body != null && !body.trim().isEmpty()) {
			Jid jid = Jid.of(from);
			Message message = new Message(account, jid, Message.TYPE_PRIVATE, body);
			message.setTime(System.currentTimeMillis());
			databaseBackend.createMessage(message);
			for (Conversation conversation : mConversations) {
				if (conversation.getUuid().equals(jid.asBareJid().toString() + "-" + account.getJid().asBareJid())) {
					conversation.messageReceived(message);
					break;
				}
			}
		}

		Intent intent = new Intent(ACTION_MESSAGE_RECEIVED);
		intent.putExtra("account", account.getUuid());
		sendBroadcast(intent);
	}

	public void onAckPacketReceived(Account account, String from, Element packet) {
		String id = getStanzaId(packet);
		for (Conversation conversation : mConversations) {
			if (conversation.getAccount() == account && conversation.getMode() != Conversation.MODE_MULTI) {
				Message message = findSentMessageInConversation(conversation, id);
				if (message != null) {
					message.setStatus(Message.STATUS_RECEIVED);
					updateMessage(message);
					return;
				}
			}
		}
	}

	private Message findSentMessageInConversation(Conversation conversation, String id) {
		for (Message message : conversation.getMessages()) {
			if (id.equals(message.getIds().getServer())) {
				return message;
			}
		}
		return null;
	}

	public void onReadPacketReceived(Account account, String from, Element packet) {
		String id = getStanzaId(packet);
		for (Conversation conversation : mConversations) {
			if (conversation.getAccount() == account && conversation.getMode() != Conversation.MODE_MULTI) {
				Message message = findSentMessageInConversation(conversation, id);
				if (message != null) {
					message.setStatus(Message.STATUS_READ);
					updateMessage(message);
					return;
				}
			}
		}
	}

	private String getStanzaId(Element packet) {
		return packet.getAttribute("id");
	}

	private String getBody(Element packet) {
		Element child = packet.findChild("body");
		if (child == null) {
			return null;
		} else {
			return child.getContent();
		}
	}

	public void onRosterPacketReceived(Account account, Element packet) {
		List<Contact> contacts = parseContacts(packet);
		for (Contact contact : contacts) {
			if (!contact.isAskRequest()) {
				databaseBackend.updateContact(contact);
			} else {
				sendSubscriptionRequest(account, Jid.of(contact.getJid()));
			}
		}

		for (Account acc : accounts) {
			if (acc == account && !acc.isOptionSet(Account.OPTION_DISABLED)) {
				mergeContactIntoRoster(contacts);
				break;
			}
		}
	}

	private List<Contact> parseContacts(Element packet) {
		List<Contact> contacts = new ArrayList<>();
		for (Element item : packet.getChildren()) {
			String jid = item.getAttribute("jid");
			if (jid != null) {
				jid = Jid.of(jid).asBareJid().toString();
				String name = item.findChildContent("name");
				String subscription = item.getAttribute("subscription");
				boolean ask = "subscribe".equals(item.findChildContent("ask"));
				List<Element> groupsElementList = item.getChildren("group");
				List<String> groups = new ArrayList<>();
				for (Element group : groupsElementList) {
					groups.add(group.getContent());
				}
				contacts.add(new Contact(jid, name, subscription, ask ? "subscribe" : null, groups));
			}
		}
		return contacts;
	}

	private void sendSubscriptionRequest(Account account, Jid jid) {
		account.getXmppConnection().sendIq(IqBuilder.getSubscriptionRequest(jid), null);
	}

	public void onPresencePacketReceived(Account account, Element packet) {
		String from = packet.getAttribute("from");
		if (from != null) {
			Presence presence = parsePresence(packet);
			for (Account acc : accounts) {
				if (acc == account && !acc.isOptionSet(Account.OPTION_DISABLED)) {
					Contact contact = acc.findContactByJid(Jid.of(from));
					if (contact != null) {
						contact.setPresence(presence);
						databaseBackend.updateContact(contact);
						break;
					}
				}
			}
		}

		String type = packet.getAttribute("type");
		if ("subscribe".equals(type)) {
			subscribeReceived(account, Jid.of(from));
		} else if ("subscribed".equals(type)) {
			subscribedReceived(account, Jid.of(from));
		}
	}

	private Presence parsePresence(Element packet) {
		String show = packet.findChildContent("show");
		if (show == null) {
			show = "online";
		}
		return new Presence(show, packet.findChildContent("status"));
	}

	private void subscribeReceived(Account account, Jid jid) {
		account.getXmppConnection().sendIq(IqBuilder.getInReplyToSubscriptionRequest(jid), null);
		String name = getContactName(account, jid.asBareJid());
		if (name != null) {
			name += " ";
		} else {
			name = "";
		}
		ToastUtils.showShortToast(XmppConnectionService.this, name + getString(R.string.contact_requests_subscription));
	}

	private void subscribedReceived(Account account, Jid jid) {
		String name = getContactName(account, jid.asBareJid());
		if (name != null) {
			name += " ";
		} else {
			name = "";
		}
		ToastUtils.showShortToast(XmppConnectionService.this, name + getString(R.string.contact_has_subscribed));
	}

	private String getContactName(Account account, Jid jid) {
		Contact contact = account.findContactByJid(jid);
		if (contact != null && contact.getName() != null && !contact.getName().trim().isEmpty()) {
			return contact.getName();
		} else {
			return jid.asBareJid().toString();
		}
	}

	public void declineSubscriptionRequest(Account account, Jid jid) {
		account.getXmppConnection().sendIq(IqBuilder.getOutdatedPresenceUnsubscribed(jid), null);
		account.getXmppConnection().sendIq(IqBuilder.getOutdatedPresenceUnsubscribe(jid), null);
		String name = getContactName(account, jid.asBareJid());
		if (name != null) {
			name += " ";
		} else {
			name = "";
		}
		ToastUtils.showShortToast(this, name + getString(R.string.contact_request_declined));
	}

	public void onAccountUpdate() {
		databaseBackend.updateAccounts(accounts);
	}

	public FileBackend getFileBackend() {
		return new FileBackend(this);
	}

	public HttpConnectionManager getHttpConnectionManager() {
		return this.mHttpConnectionManager;
	}

	public JingleConnectionManager getJingleConnectionManager() {
		return mJingleConnectionManager;
	}

	public DatabaseBackend getDatabaseBackend() {
		return databaseBackend;
	}

	private NotificationCompat.Builder getForegroundNotificationBuilderWithAccount(Account account) {
		String title = getString(R.string.app_name);
		if (account.getStatus() != Account.State.OFFLINE) {
			title += " (" + account.getJid().asBareJid().toString() + ")";
		}
		return new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(title)
				.setContentText(account.getStatusMessage());
	}

	private NotificationCompat.Builder getForegroundNotificationBuilderWithoutAccount() {
		return new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(getString(R.string.app_name))
				.setContentText(getString(R.string.service_is_in_foreground));
	}

	public NotificationCompat.Builder getForegroundNotificationBuilder(Account account) {
		if (account == null) {
			return getForegroundNotificationBuilderWithoutAccount();
		} else {
			return getForegroundNotificationBuilderWithAccount(account);
		}
	}

	private NotificationCompat.Builder getForegroundNotificationBuilder() {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(getString(R.string.app_name))
				.setContentText(getString(R.string.service_is_in_foreground));

		if (accounts.size() > 0) {
			builder.addAction(0, getString(R.string.action_show_contacts), PendingIntent.getActivity(this, 0,
					new Intent(this, ContactListActivity.class), 0));
		}

		return builder;
	}

	private Notification getForegroundNotification(Account account) {
		NotificationCompat.Builder builder = getForegroundNotificationBuilder(account);

		Intent intent = new Intent(this, MainActivity.class);
		intent.setAction(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		builder.setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));

		return builder.build();
	}

	private Notification getForegroundNotification() {
		NotificationCompat.Builder builder = getForegroundNotificationBuilder();

		Intent intent = new Intent(this, MainActivity.class);
		intent.setAction(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		builder.setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));

		return builder.build();
	}

	private void startForegroundService() {
		startForeground(1, getForegroundNotification());
	}

	public void onAccountStatusChanged(Account account) {
		if (account.getStatus() == Account.State.ONLINE) {
			startForegroundService();
		} else if (account.getStatus() == Account.State.OFFLINE) {
			stopForeground(true);
		}
	}

	public void onAccountConnected(Account account) {
		account.getXmppConnection().sendPresence();
		onAccountStatusChanged(account);
	}

	public void onAccountDisconnected(Account account) {
		sendOfflinePresence(account);
		onAccountStatusChanged(account);
	}

	public void onAccountReconnecting(Account account) {
		sendOfflinePresence(account);
		onAccountStatusChanged(account);
	}

	public Notification getForegroundNotification() {
		return getForegroundNotification(null);
	}

	public void startService() {
		Intent intent = new Intent(this, XmppConnectionService.class);
		startService(intent);
	}

	public void stopService() {
		stopSelf();
	}

	public void onAccountAdded(Account account) {
		databaseBackend.createAccount(account);
		onAccountConnected(account);
	}

	public void onAccountRemoved(Account account) {
		databaseBackend.deleteAccount(account);
		onAccountDisconnected(account);
	}

	public void onAccountEdited(Account oldAccount, Account newAccount) {
		databaseBackend.updateAccount(oldAccount, newAccount);
		if (!oldAccount.getJid().equals(newAccount.getJid())) {
			onAccountDisconnected(oldAccount);
			newAccount.setStatus(Account.State.OFFLINE);
			databaseBackend.updateAccount(newAccount);
			onAccountConnected(newAccount);
		}
	}

	public void onContactStatusChanged(Contact contact) {
		for (Conversation conversation : mConversations) {
			if (conversation.getUuid().equals(contact.getJid())) {
				conversation.contactPresenceUpdated(contact);
			}
		}
	}

	public void onMessageSent(Message message) {
		for (Conversation conversation : mConversations) {
			if (conversation.getUuid().equals(message.getCounterpart())) {
				conversation.messageSent(message);
			}
		}
	}

	public void onMessageReceived(Message message) {
		for (Conversation conversation : mConversations) {
			if (conversation.getUuid().equals(message.getCounterpart())) {
				conversation.messageReceived(message);
			}
		}
		Intent intent = new Intent(ACTION_MESSAGE_RECEIVED);
		intent.putExtra("account", message.getAccount().getUuid());
		sendBroadcast(intent);
	}

	public void onMessageStatusChanged(Message message) {
		for (Conversation conversation : mConversations) {
			if (conversation.getUuid().equals(message.getCounterpart())) {
				conversation.messageStatusUpdated(message);
			}
		}
	}

	private class IqParser implements PacketParser {

		@Override
		public void parse(Element packet, Account account) {
			String id = packet.getAttribute("id");
			IqPacket iqPacket = new IqPacket(id, packet);
			if (iqPacket.getType() == IqPacket.TYPE_RESULT) {
				onIqResultReceived(account, iqPacket);
			} else if (iqPacket.getType() == IqPacket.TYPE_ERROR) {
				onIqErrorReceived(account, iqPacket);
			}
		}

		private void onIqResultReceived(Account account, IqPacket packet) {
			if ("jabber:iq:roster".equals(packet.getNamespace())) {
				onRosterPacketReceived(account, packet.getChild("query"));
			} else if ("urn:xmpp:ping".equals(packet.getNamespace())) {
				account.getXmppConnection().sendIq(IqBuilder.getPong(packet.getId()), null);
			}
		}

		private void onIqErrorReceived(Account account, IqPacket packet) {
			ToastUtils.showShortToast(XmppConnectionService.this, getString(R.string.error_occurred));
		}
	}

	private class MessageParser implements PacketParser {

		@Override
		public void parse(Element packet, Account account) {
			onMessagePacketReceived(account, packet.getAttribute("from"), packet);
		}
	}

	private class PresenceParser implements PacketParser {

		@Override
		public void parse(Element packet, Account account) {
			onPresencePacketReceived(account, packet);
		}
	}

	private interface PacketParser {
		void parse(Element packet, Account account);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public class XmppConnectionBinder extends Binder {
		XmppConnectionService getService() {
			return XmppConnectionService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startForegroundService();
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		for (Account account : accounts) {
			if (account.getStatus() == Account.State.ONLINE) {
				onAccountDisconnected(account);
			}
		}
		stopForeground(true);
		mJingleConnectionManager.stopAllTransfers();
		unregisterReceiver(mConnectivityChangeReceiver);
		getDatabaseBackend().cleanup();
		ToastUtils.showShortToast(this, getString(R.string.service_stopped));
		super.onDestroy();
	}

	private void sendPresence(Account account) {
		account.getXmppConnection().sendPresence();
	}

	public Account findAccountByJid(Jid jid) {
		for (Account account : accounts) {
			if (account.getJid().equals(jid)) {
				return account;
			}
		}
		return null;
	}

	public Conversation findConversation(Account account, Jid jid) {
		for (Conversation conversation : mConversations) {
			if (conversation.getAccount() == account && conversation.getUuid().equals(jid.asBareJid().toString())) {
				return conversation;
			}
		}
		return null;
	}

	public void addConversation(Conversation conversation) {
		mConversations.add(conversation);
	}

	public void removeConversation(Account account, Jid jid) {
		for (Iterator<Conversation> iterator = mConversations.iterator(); iterator.hasNext();) {
			Conversation conversation = iterator.next();
			if (conversation.getAccount() == account && conversation.getUuid().equals(jid.asBareJid().toString())) {
				iterator.remove();
				break;
			}
		}
	}

	public List<Conversation> getConversations() {
		return mConversations;
	}

	private class ConnectivityChangeReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (accounts.size() > 0 && accounts.get(0).getStatus() == Account.State.OFFLINE) {
				ToastUtils.showShortToast(XmppConnectionService.this, getString(R.string.no_internet_connection));
			}
		}
	}

	private BroadcastReceiver mConnectivityChangeReceiver = new ConnectivityChangeReceiver();

	@Override
	public void onCreate() {
		super.onCreate();
		mJingleConnectionManager.init(this);
		registerReceiver(mConnectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		ToastUtils.showShortToast(this, getString(R.string.service_started));
	}
}