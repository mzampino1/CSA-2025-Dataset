package eu.siacs.conversations.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.*;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.security.SecureRandom;
import java.util.*;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.jingle.JingleConnectionManager;
import eu.siacs.conversations.mam.MessageArchiveService;
import eu.siacs.conversations.network.BlinkuBackendConnector;
import eu.siacs.conversations.network.BlockingQueue;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.persistence.DatabaseBackend;
import eu.siacs.conversations.persistence.DatabaseConnection;
import eu.siacs.conversations.qr.QRCodeGenerator;
import eu.siacs.conversations.services.MessageGenerator;
import eu.siacs.conversations.services.PresenceGenerator;
import eu.siacs.conversations.services.IqGenerator;
import eu.siacs.conversations.ui.*;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.XmppConnection;

public class XmppConnectionService extends Service {

	private static final int PING_TIMEOUT = 30 * 1000; // 30 seconds

	private OnAccountUpdate mOnAccountUpdated;
	private OnConversationUpdate mOnConversationUpdate;
	private OnMucRosterUpdate mOnMucRosterUpdate;
	private OnAffiliationChanged mOnAffiliationChanged;
	private OnRoleChanged mOnRoleChanged;
	private OnConferenceOptionsPushed mOnConferenceOptionsPushed;

	private List<Account> accounts = new ArrayList<>();
	private DatabaseBackend databaseBackend;
	private MessageArchiveService messageArchiveService;
	private NotificationService mNotificationService;
	private HttpConnectionManager mHttpConnectionManager;
	private MemorizingTrustManager mMemorizingTrustManager;
	private PowerManager pm;
	private WakeLock wakeLock;
	private LruCache<String, Bitmap> mBitmapCache;
	private SecureRandom mRandom;
	private MessageGenerator mMessageGenerator;
	private PresenceGenerator mPresenceGenerator;
	private IqGenerator mIqGenerator;
	private IqParser mIqParser;
	private JingleConnectionManager mJingleConnectionManager;

	private int unreadCount = 0;

	@Override
	public void onCreate() {
		if (Config.LOG_CONNECTIONS) {
			Log.d(Config.LOGTAG, "service created");
		}
		this.databaseBackend = new DatabaseBackend(this);
		this.mNotificationService = new NotificationService(this);
		this.mHttpConnectionManager = new HttpConnectionManager(this);
		this.messageArchiveService = new MessageArchiveService(this);
		this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Conversations:sync");
		this.updateMemorizingTrustmanager();
		long memClass = ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getLargeMemoryClass() * 1024L * 1024L;
		long maxSize = Math.min(512 * 1024 * 1024, (long) (memClass / 3));
		this.mBitmapCache = new LruCache<>(maxSize);
		this.accounts.addAll(this.databaseBackend.getAccounts());
		this.mRandom = new SecureRandom();
		this.mMessageGenerator = new MessageGenerator(this);
		this.mPresenceGenerator = new PresenceGenerator(this);
		this.mIqGenerator = new IqGenerator(this);
		this.mIqParser = new IqParser(this);
		this.mJingleConnectionManager = new JingleConnectionManager(this);

		for (Account account : this.accounts) {
			if (account.isOnlineAndConnected()) {
				account.getXmppConnection().sendPresencePacket(this.mPresenceGenerator.sendOfflinePresence(account));
			}
			connectIfNecessary(account, true);
		}

		this.registerReceiver(mBroadcastReceiver, new IntentFilter(XmppConnectionService.SIGNAL_EVENT));

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageBroadcastReceiver,
				new IntentFilter(MessageActivity.ACTION_MESSAGE_SENT));
		LocalBroadcastManager.getInstance(this).registerReceiver(mConversationStatusReceiver,
				new IntentFilter(ConversationsActivity.CONVERSATION_STATUS));
	}

	private BroadcastReceiver mConversationStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String uuid = intent.getStringExtra("uuid");
			if (uuid != null) {
				Conversation conversation = findConversationByUuid(uuid);
				if (conversation != null && mOnConversationUpdate != null) {
					mOnConversationUpdate.onConversationUpdate();
				}
			}
		}
	};

	private BroadcastReceiver mMessageBroadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String uuid = intent.getStringExtra("uuid");
			String message = intent.getStringExtra("message");
			if (uuid != null && message != null) {
				Conversation conversation = findConversationByUuid(uuid);
				if (conversation != null) {
					Message xmppMessage = new Message(conversation, message, Message.INDICATOR_OUTGOING);
					conversation.addMessage(xmppMessage);
					xmppMessage.setTime(System.currentTimeMillis());
					markMessageDelivered(conversation,xmppMessage);
					sendMessagePacket(conversation.getAccount(), mMessageGenerator.generateMessagePacket(conversation, xmppMessage));
				}
			}

		}
	};

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (SIGNAL_EVENT.equals(action)) {
				final int size = intent.getIntExtra("count", 0);
				for (int i = 0; i < size; ++i) {
					String accountJid = intent.getStringExtra("account" + i);
					Account account = findAccountByJid(Jid.fromString(accountJid));
					if (account != null && account.getStatus() == Account.State.ONLINE) {
						XmppConnection connection = account.getXmppConnection();
						if (connection.getFeatures().sm()) {
							connection.sendStanza("<r xmlns='urn:xmpp:sm:3'/>");
						} else if (!connection.isWaitingForResponse(PING_TIMEOUT)) {
							sendPing(account);
						}
					}
				}
			}
		}

	};

	private void sendPing(Account account) {
		if (account.getStatus() == Account.State.ONLINE) {
			XmppConnection connection = account.getXmppConnection();
			IqPacket packet = mIqGenerator.getPing(connection.getServiceName());
			connection.sendStanza(packet);
		}
	}

	public List<Account> getAccounts() {
		return accounts;
	}

	private void connectIfNecessary(Account account, boolean interactive) {
		XmppConnection connection = account.getXmppConnection();
		if (account.getStatus().equals(Account.State.DISABLED)) {
			Log.d(Config.LOGTAG, account.getJid().asBareJid() + " is disabled");
		} else if (connection == null) {
			account.setXmppConnection(new XmppConnection(account, this));
			connectIfNecessary(account, interactive);
		} else {
			if (!account.isOnlineAndConnected()) {
				if (interactive || account.getStatus().equals(Account.State.OFFLINE)) {
					new Thread(() -> connect(account)).start();
				}
			}
		}
	}

	private void connect(final Account account) {
		Log.d(Config.LOGTAG, account.getJid() + " is about to connect");
		boolean success = false;
		try {
			account.getXmppConnection().connect();
			success = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (!success) {
			int r;
			switch (account.getStatus()) {
				case Account.State.NO_INTERNET:
					r = R.string.no_internet_connection;
					break;
				case Account.State.SERVER_NOT_FOUND:
					r = R.string.could_not_connect_to_server_please_check_host_name_and_port_settings;
					break;
				default:
					r = R.string.unable_to_connect_to_server;
			}
			Toast.makeText(this, r, Toast.LENGTH_SHORT).show();
		} else {
			Log.d(Config.LOGTAG, account.getJid() + " connected");
		}
	}

	public void addAccount(Account account) {
		this.accounts.add(account);
		this.databaseBackend.createAccount(account);
		connectIfNecessary(account, true);
		if (mOnAccountUpdated != null) {
			mOnAccountUpdated.onAccountUpdate();
		}
	}

	public void updateAccount(Account account) {
		this.databaseBackend.updateAccount(account);
		if (account.getStatus() == Account.State.DISABLED) {
			this.disconnect(account,false);
		} else if (!account.isOnlineAndConnected()) {
			new Thread(() -> connect(account)).start();
		}
		if (mOnAccountUpdated != null) {
			mOnAccountUpdated.onAccountUpdate();
		}
	}

	public void removeAccount(Account account) {
		this.disconnect(account,false);
		this.accounts.remove(account);
		databaseBackend.deleteAccount(account);
		if (mOnAccountUpdated != null) {
			mOnAccountUpdated.onAccountUpdate();
		}
	}

	private Account findAccountByJid(Jid jid) {
		for (Account account : accounts) {
			if (account.getJid().equals(jid)) {
				return account;
			}
		}
		return null;
	}

	public Conversation findOrCreateConversation(final Jid conversationJid, final String name) {
		final Conversation conversation = findConversationByJid(conversationJid);
		if (conversation != null) {
			return conversation;
		} else {
			return createConversation(conversationJid, name);
		}
	}

	public Conversation findConversationByFingerprint(final String fingerprint) {
		for (final Account account : accounts) {
			for (final Conversation conversation : account.conversations) {
				if (conversation.getContact().getOmemoFingerprint() != null &&
						conversation.getContact().getOmemoFingerprint().equals(fingerprint)) {
					return conversation;
				}
			}
		}
		return null;
	}

	private Conversation createConversation(final Jid jid, final String name) {
		Account account = findAccountByJid(jid.asBareJid());
		if (account != null) {
			Contact contact = account.findContactByJid(jid);
			if (contact == null) {
				contact = new Contact(account, jid);
				account.pendingSubscriptions.add(contact);
				databaseBackend.createContact(contact);
			}
			final Conversation conversation = new Conversation(account, contact, name);
			account.conversations.add(conversation);
			databaseBackend.createConversation(conversation);
			if (mOnMucRosterUpdate != null) {
				mOnMucRosterUpdate.onMucRosterUpdate();
			}
			return conversation;
		} else {
			return null;
		}
	}

	public Conversation findConversationByUuid(final String uuid) {
		for (final Account account : accounts) {
			for (final Conversation conversation : account.conversations) {
				if (conversation.getUuid().equals(uuid)) {
					return conversation;
				}
			}
		}
		return null;
	}

	public Conversation findConversationByJid(Jid jid) {
		for (Account account : this.accounts) {
			for (Conversation conversation : account.conversations) {
				if (conversation.getJid().equals(jid)) {
					return conversation;
				}
			}
		}
		return null;
	}

	public void fetchConferenceConfiguration(Account account, Jid muc) {
		sendIqPacket(account,mIqGenerator.getRoomDisco(muc));
	}

	private void sendIqPacket(Account account,IqPacket packet) {
		if (account.getStatus() == Account.State.ONLINE) {
			account.getXmppConnection().sendStanza(packet);
		}
	}

	public void pushConferenceConfiguration(Account account, Jid muc, ConferenceOptions options) {
		sendIqPacket(account,mIqGenerator.getPushRoomConfig(muc, options));
		if (mOnConferenceOptionsPushed != null) {
			mOnConferenceOptionsPushed.onConferenceOptionsPushed();
		}
	}

	public void invite(Account account, Jid muc, String nick, Collection<Jid> contacts) {
		for(Jid jid : contacts) {
			sendIqPacket(account,mIqGenerator.getInvite(muc,jid,nick));
		}
	}

	public List<MucOptions.User> fetchOnlineUsers(Account account, Jid muc) {
		List<MucOptions.User> users = new ArrayList<>();
		if (account.getXmppConnection().getMucOptions() != null) {
			MucOptions mucOptions = account.getXmppConnection().getMucOptions();
			for(MucOptions.User user : mucOptions.getUsers(muc)) {
				users.add(user);
			}
		}
		return users;
	}

	public void bookmarkConference(Account account, Jid jid, String name) {
		sendIqPacket(account,mIqGenerator.getBookmark(jid,name));
	}

	public void joinMuc(Account account, Jid muc, String password, String nickname) {
		if (account.getStatus() == Account.State.ONLINE) {
			account.getXmppConnection().sendStanza(mIqGenerator.join(muc,nickname,password,true));
			sendPing(account);
		}
	}

	public void leaveMuc(Account account, Jid jid) {
		if (account.isOnlineAndConnected()) {
			account.getXmppConnection().sendStanza("<presence to='" + jid.asBareJid() +
					"' from='" + account.getFullJid() + "' type='unavailable'/>");
			for(Conversation conversation : account.conversations) {
				if (conversation.getJid().asBareJid().equals(jid)) {
					account.onPendingConferenceJoined(conversation);
				}
			}
		}
	}

	public void changeAffiliation(Account account, Jid muc, Jid jid, MucOptions.Affiliation affiliation) {
		if (account.isOnlineAndConnected()) {
			sendIqPacket(account,mIqGenerator.changeAffiliation(muc,jid,affiliation));
		}
	}

	public void changeRole(Account account, Jid muc, Jid jid, MucOptions.Role role) {
		if (account.isOnlineAndConnected()) {
			sendIqPacket(account,mIqGenerator.changeRole(muc,jid,role));
		}
	}

	public void deleteBookmark(Account account, Jid jid) {
		sendIqPacket(account,mIqGenerator.deleteBookmark(jid));
	}

	public void changeStatus(Account account, Account.State status, String message) {
		account.setPresence(Account.State.ONLINE);
		if (account.getXmppConnection() != null && account.isOnlineAndConnected()) {
			PresencePacket packet = mPresenceGenerator.sendPresence(account, status, message);
			packet.setTo(account.getServer());
			sendPresence(packet);
		}
	}

	public void sendPresence(PresencePacket presence) {
		for(Account account : accounts) {
			if (account.getStatus() == Account.State.ONLINE) {
				account.getXmppConnection().sendStanza(presence);
			}
		}
	}

	public boolean hasInternetConnection() {
		return mNotificationService.hasInternetConnection();
	}

	private void disconnect(Account account,boolean sendOfflinePacket) {
		if (account.isOnlineAndConnected()) {
			if (sendOfflinePacket) {
				sendPresence(mPresenceGenerator.sendOfflinePresence(account));
			}
			account.getXmppConnection().disconnect();
		} else {
			Log.d(Config.LOGTAG,account.getJid()+": already disconnected");
		}
	}

	public void disconnect(Account account,boolean sendOfflinePacket,long in) {
		new Handler().postDelayed(() -> disconnect(account,sendOfflinePacket),in);
	}

	public void setOnAccountUpdated(OnAccountUpdate onAccountUpdated) {
		this.mOnAccountUpdated = onAccountUpdated;
	}

	public void setOnConversationUpdate(OnConversationUpdate onConversationUpdate) {
		this.mOnConversationUpdate = onConversationUpdate;
	}

	public void setOnMucRosterUpdate(OnMucRosterUpdate onMucRosterUpdate) {
		this.mOnMucRosterUpdate = onMucRosterUpdate;
	}

	public void setOnAffiliationChanged(OnAffiliationChanged affiliationChanged) {
		this.mOnAffiliationChanged = affiliationChanged;
	}

	public void setOnRoleChanged(OnRoleChanged roleChanged) {
		this.mOnRoleChanged = roleChanged;
	}

	public void setOnConferenceOptionsPushed(OnConferenceOptionsPushed conferenceOptionsPushed) {
		this.mOnConferenceOptionsPushed = conferenceOptionsPushed;
	}

	private Bitmap loadBitmap(File file, int size) throws ErrnoException {
		Bitmap bm = decodeSampledBitmapFromFile(file.getAbsolutePath(),size);
		if (bm != null) {
			mBitmapCache.put(file.getAbsolutePath(), bm);
		}
		return bm;
	}

	public Bitmap getCachedBitmap(File file,int size) throws ErrnoException {
		Bitmap cached = mBitmapCache.get(file.getAbsolutePath());
		if (cached == null) {
			cached = loadBitmap(file,size);
		}
		return cached;
	}

	private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			while ((halfHeight / inSampleSize) >= reqHeight
					&& (halfWidth / inSampleSize) >= reqWidth) {
				inSampleSize *= 2;
			}
		}

		return inSampleSize;
	}

	private static Bitmap decodeSampledBitmapFromFile(String filename,
													int reqWidth) throws ErrnoException {

		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(filename, options);

		options.inSampleSize = calculateInSampleSize(options, reqWidth, reqWidth);
		options.inJustDecodeBounds = false;

		return BitmapFactory.decodeFile(filename, options);
	}

	public void onBackendConnected() {
		for(Account account : accounts) {
			if (account.getStatus() != Account.State.DISABLED) {
				new Thread(() -> connect(account)).start();
			}
		}
	}

	private void connect(final Account account) {
		disconnect(account,false,0);
		final Jid jid = account.getJid();
		if (!jid.isBareJid()) {
			account.setLastErrorStatus(Account.ERROR_ILLEGAL_JID);
			return;
		} else if (account.getStatus() != Account.State.DISABLED) {
			try {
				XmppConnectionService.this.sendMessage(account, new XmlElement("auth", "xmpp") {{
					setAttribute("type","get");
					addChild(new XmlElement("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl"));
				}});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void sendMessage(Account account,XmlElement element) throws Exception {
		if (!account.isOnlineAndConnected()) {
			return;
		}
		final String from = account.getJid().asBareJid().toString();
		account.getXmppConnection().sendMessage(new XmlElement("iq", "jabber:client") {{
			setAttribute("type","get");
			setAttribute("from",from);
			addChild(element);
		}});
	}

	public void onPacketReceived(Account account,XmlElement packet) {
		if (packet.hasAttribute("type")) {
			switch(packet.getAttribute("type")) {
				case "result":
					break;
				case "set":
					break;
				case "get":
					handleIqGet(account,packet);
					break;
				default:
					Log.d(Config.LOGTAG,"unknown iq type");
					break;
			}
		} else {
			Log.d(Config.LOGTAG,"received malformed iq packet without type attribute");
		}
	}

	private void handleIqGet(Account account,XmlElement packet) {
		if (packet.hasChild("query") && "jabber:iq:roster".equals(packet.findChild("query").getAttribute("xmlns"))) {
			account.getXmppConnection().sendMessage(new XmlElement("iq", "jabber:client") {{
				setAttribute("type","result");
				setAttribute("id",packet.getAttribute("id"));
				addChild(new XmlElement("query", "jabber:iq:roster"));
			}});
		} else if (packet.hasChild("command") && "http://jabber.org/protocol/commands".equals(packet.findChild("command").getAttribute("xmlns")) &&
				"cancel".equals(packet.findChild("command").getAttribute("action"))) {
			account.getXmppConnection().sendMessage(new XmlElement("iq", "jabber:client") {{
				setAttribute("type","result");
				setAttribute("id",packet.getAttribute("id"));
				addChild(new XmlElement("command", "http://jabber.org/protocol/commands"));
			}});
		}
	}

	private void onMessagePacketReceived(Account account,XmlElement message) {
		String type = message.getAttribute("type");
		if (type == null || !type.equals("chat")) {
			return;
		}
		Jid to = jidToResource(message.getAttribute("to"),account);
		if (to != null && to.isBareJid()) {
			type = "normal";
		}
		if (!"error".equals(type)) {
			if (message.hasChild("body")) {
				final String body = message.findChild("body").getContent();
				String nick = null;
				if ("groupchat".equals(type) && message.hasChild("nick") && "http://jabber.org/protocol/nick".equals(message.findChild("nick").getAttribute("xmlns"))) {
					nick = message.findChild("nick").getContent();
				}
				final String from = message.getAttribute("from");
				boolean gcMessage = false;
				if ("groupchat".equals(type)) {
					gcMessage = true;
				} else if (to != null && to.isBareJid()) {
					type = "normal";
					gcMessage = true;
				}
				final Jid fromJid = gcMessage ? jidToResource(from,to) : jidToResource(from,account);
				if (fromJid == null || !fromJid.isValid()) {
					return;
				}
				Conversation conversation = findOrCreateConversation(fromJid, nick);
				Message xmppMessage = new Message(conversation,body,type);
				xmppMessage.setTime(System.currentTimeMillis());
				conversation.messages.add(xmppMessage);
				databaseBackend.createMessage(xmppMessage);
				if (mOnConversationUpdate != null) {
					mOnConversationUpdate.onConversationUpdate();
				}
			} else if ("groupchat".equals(type)) {
				String event = message.getAttribute("event");
				if ("http://jabber.org/protocol/muc#user".equals(event) && message.hasChild("x")) {
					handleMucUser(account,message.findChild("x"));
				}
			}
		}
	}

	private void handleMucUser(Account account,XmlElement x) {
		MucOptions mucOptions = account.getXmppConnection().getMucOptions();
		if (mucOptions == null) {
			return;
		}
		Jid mucJid = jidToResource(x.getAttribute("from"),account);
		if (mucJid != null && !mucJid.isBareJid()) {
			mucJid = mucJid.asBareJid();
		} else {
			return;
		}
		for(XmlElement item : x.getChildren()) {
			String affiliation = item.getAttribute("affiliation");
			if ("x".equals(item.getName()) && "http://jabber.org/protocol/muc#user".equals(item.getAttribute("xmlns"))) {
				for(XmlElement childItem : item.getChildren()) {
					String role = childItem.getAttribute("role");
					Jid jid = jidToResource(childItem.getAttribute("jid"),account);
					if ("item".equals(childItem.getName())) {
						mucOptions.addUser(mucJid,new MucOptions.User(jid,affiliation,role));
					} else if ("status".equals(childItem.getName()) && "110".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "210".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "201".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "110".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "201".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "110".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "201".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "110".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "201".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "110".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "201".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
					6
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "320".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "307".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
						}
					} else if ("status".equals(childItem.getName()) && "321".equals(childItem.getAttribute("code"))) {
						for(Conversation conversation : account.conversations) {
							if (conversation.getJid().asBareJid().equals(mucJid)) {
								account.onPendingConferenceJoined(conversation);
							}
					 }
					}
				}
			}
		}
	}
}

It seems like your provided code snippet is incomplete and contains repetitive blocks. If you intended to handle different states or statuses based on the `childItem.getName()` and `childItem.getAttribute("code")`, it's likely that you can simplify and refactor this code to avoid repetition.

Here’s an example of how you might refactor the code: