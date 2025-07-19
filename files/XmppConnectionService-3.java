package de.gultsch.chat.services;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.entities.Presences;
import de.gultsch.chat.persistance.DatabaseBackend;
import de.gultsch.chat.ui.OnAccountListChangedListener;
import de.gultsch.chat.ui.OnConversationListChangedListener;
import de.gultsch.chat.ui.OnRosterFetchedListener;
import de.gultsch.chat.utils.UIHelper;
import de.gultsch.chat.xml.Element;
import de.gultsch.chat.xmpp.IqPacket;
import de.gultsch.chat.xmpp.MessagePacket;
import de.gultsch.chat.xmpp.OnIqPacketReceived;
import de.gultsch.chat.xmpp.OnMessagePacketReceived;
import de.gultsch.chat.xmpp.OnPresencePacketReceived;
import de.gultsch.chat.xmpp.OnStatusChanged;
import de.gultsch.chat.xmpp.PresencePacket;
import de.gultsch.chat.xmpp.XmppConnection;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.util.Log;

public class XmppConnectionService extends Service {

	protected static final String LOGTAG = "xmppService";
	protected DatabaseBackend databaseBackend;

	public long startDate;

	private List<Account> accounts;
	private List<Conversation> conversations = null;

	private Hashtable<Account, XmppConnection> connections = new Hashtable<Account, XmppConnection>();

	private OnConversationListChangedListener convChangedListener = null;
	private OnAccountListChangedListener accountChangedListener = null;

	private final IBinder mBinder = new XmppConnectionBinder();
	private OnMessagePacketReceived messageListener = new OnMessagePacketReceived() {

		@Override
		public void onMessagePacketReceived(Account account,
				MessagePacket packet) {
			if ((packet.getType() == MessagePacket.TYPE_CHAT)
					|| (packet.getType() == MessagePacket.TYPE_GROUPCHAT)) {
				boolean notify = true;
				if (!packet.hasChild("body")) {
					return;
				}
				Conversation conversation = null;
				String fullJid = packet.getFrom();
				String[] fromParts = fullJid.split("/");
				String jid = fromParts[0];
				Contact contact = findOrCreateContact(account, jid);
				boolean muc = (packet.getType() == MessagePacket.TYPE_GROUPCHAT);
				String counterPart = null;
				int status = Message.STATUS_RECIEVED;
				conversation = findOrCreateConversation(account, contact, muc);
				if (muc) {
					if ((fromParts.length==1)||(packet.hasChild("subject"))||(packet.hasChild("delay"))) {
						return;
					}
					counterPart = fromParts[1];
					if (counterPart.equals(account.getUsername())) {
						status = Message.STATUS_SEND;
						notify = false;
					}
				} else {
					counterPart = fullJid;
				}
				Message message = new Message(conversation, counterPart,
						packet.getBody(), Message.ENCRYPTION_NONE, status);
				conversation.getMessages().add(message);
				databaseBackend.createMessage(message);
				if (convChangedListener != null) {
					convChangedListener.onConversationListChanged();
				} else {
					if (notify) {
						NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
						mNotificationManager.notify(2342, UIHelper
								.getUnreadMessageNotification(
										getApplicationContext(), conversation));
					}
				}
			}
		}
	};
	private OnStatusChanged statusListener = new OnStatusChanged() {

		@Override
		public void onStatusChanged(Account account) {
			if (accountChangedListener != null) {
				accountChangedListener.onAccountListChangedListener();
			}
			if (account.getStatus() == Account.STATUS_ONLINE) {
				databaseBackend.clearPresences(account);
				connectMultiModeConversations(account);
			}
		}
	};

	private OnPresencePacketReceived presenceListener = new OnPresencePacketReceived() {

		@Override
		public void onPresencePacketReceived(Account account,
				PresencePacket packet) {
			String[] fromParts = packet.getAttribute("from").split("/");
			Contact contact = findOrCreateContact(account, fromParts[0]);
			if (contact.getUuid()==null) {
				//most likely muc, self or roster not synced
				Log.d(LOGTAG,"got presence for non contact "+packet.toString());
			}
			String type = packet.getAttribute("type");
			if (type == null) {
				Element show = packet.findChild("show");
				if (show==null) {
					contact.updatePresence(fromParts[1],Presences.ONLINE);
				} else if (show.getContent().equals("away")) {
					contact.updatePresence(fromParts[1],Presences.AWAY);
				} else if (show.getContent().equals("xa")) {
					contact.updatePresence(fromParts[1],Presences.XA);
				} else if (show.getContent().equals("chat")) {
					contact.updatePresence(fromParts[1],Presences.CHAT);
				} else if (show.getContent().equals("dnd")) {
					contact.updatePresence(fromParts[1],Presences.DND);
				}
				databaseBackend.updateContact(contact);
			} else if (type.equals("unavailable")) {
				if (fromParts.length!=2) {
					Log.d(LOGTAG,"received presence with no resource "+packet.toString());
				} else {
					contact.removePresence(fromParts[1]);
					databaseBackend.updateContact(contact);
				}
			}
		}
	};

	public class XmppConnectionBinder extends Binder {
		public XmppConnectionService getService() {
			return XmppConnectionService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		for (Account account : accounts) {
			if (!connections.containsKey(account)) {
				if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					this.connections.put(account,
							this.createConnection(account));
				} else {
					Log.d(LOGTAG, account.getJid()
							+ ": not starting because it's disabled");
				}
			}
		}
		return START_STICKY;
	}

	@Override
	public void onCreate() {
		databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
		this.accounts = databaseBackend.getAccounts();
	}

	public XmppConnection createConnection(Account account) {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		XmppConnection connection = new XmppConnection(account, pm);
		connection.setOnMessagePacketReceivedListener(this.messageListener);
		connection.setOnStatusChangedListener(this.statusListener);
		connection.setOnPresencePacketReceivedListener(this.presenceListener);
		Thread thread = new Thread(connection);
		thread.start();
		return connection;
	}

	public void sendMessage(final Account account, final Message message) {
		Log.d(LOGTAG, "sending message for " + account.getJid() + " to: "
				+ message.getCounterpart());
		databaseBackend.createMessage(message);
		MessagePacket packet = new MessagePacket();
		if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
			packet.setType(MessagePacket.TYPE_CHAT);
		} else if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
			packet.setType(MessagePacket.TYPE_GROUPCHAT);
		}
		packet.setTo(message.getCounterpart());
		packet.setFrom(account.getJid());
		packet.setBody(message.getBody());
		if (account.getStatus()==Account.STATUS_ONLINE) {
			connections.get(account).sendMessagePacket(packet);
			message.setStatus(Message.STATUS_SEND);
			databaseBackend.updateMessage(message);
		}
	}

	public void getRoster(Account account,
			final OnRosterFetchedListener listener) {
		List<Contact> contacts = databaseBackend.getContacts(account);
		for (int i = 0; i < contacts.size(); ++i) {
			contacts.get(i).setAccount(account);
		}
		if (listener != null) {
			listener.onRosterFetched(contacts);
		}
	}

	public void updateRoster(final Account account,
			final OnRosterFetchedListener listener) {

		final Hashtable<String, Bundle> phoneContacts = new Hashtable<String, Bundle>();
		final List<Contact> contacts = new ArrayList<Contact>();

		final String[] PROJECTION = new String[] {
				ContactsContract.Data.CONTACT_ID,
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.Data.PHOTO_THUMBNAIL_URI,
				ContactsContract.CommonDataKinds.Im.DATA };

		final String SELECTION = "(" + ContactsContract.Data.MIMETYPE + "=\""
				+ ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
				+ "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
				+ "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
				+ "\")";

		CursorLoader mCursorLoader = new CursorLoader(this,
				ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
				null);
		mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

			@Override
			public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
				while (cursor.moveToNext()) {
					Bundle contact = new Bundle();
					contact.putInt("phoneid", cursor.getInt(cursor
							.getColumnIndex(ContactsContract.Data.CONTACT_ID)));
					contact.putString(
							"displayname",
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)));
					contact.putString(
							"photouri",
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI)));
					phoneContacts.put(
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)),
							contact);
				}
				IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
				Element query = new Element("query");
				query.setAttribute("xmlns", "jabber:iq:roster");
				query.setAttribute("ver", "");
				iqPacket.addChild(query);
				connections.get(account).sendIqPacket(iqPacket,
						new OnIqPacketReceived() {

							@Override
							public void onIqPacketReceived(Account account,
									IqPacket packet) {
								Element roster = packet.findChild("query");
								if (roster != null) {
									for (Element item : roster.getChildren()) {
										Contact contact;
										String name = item.getAttribute("name");
										String jid = item.getAttribute("jid");
										if (phoneContacts.containsKey(jid)) {
											Bundle phoneContact = phoneContacts
													.get(jid);
											contact = new Contact(
													account,
													phoneContact
															.getString("displayname"),
													jid,
													phoneContact
															.getString("photouri"));
											contact.setSystemAccount(phoneContact
													.getInt("phoneid"));
										} else {
											if (name == null) {
												name = jid.split("@")[0];
											}
											contact = new Contact(account,
													name, jid, null);

										}
										contact.setAccount(account);
										contact.setSubscription(item
												.getAttribute("subscription"));
										contacts.add(contact);
									}
									databaseBackend.mergeContacts(contacts);
									if (listener != null) {
										listener.onRosterFetched(contacts);
									}
								}
							}
						});

			}
		});
		mCursorLoader.startLoading();
	}

	public void addConversation(Conversation conversation) {
		databaseBackend.createConversation(conversation);
	}

	public List<Conversation> getConversations() {
		if (this.conversations == null) {
			Hashtable<String, Account> accountLookupTable = new Hashtable<String, Account>();
			for (Account account : this.accounts) {
				accountLookupTable.put(account.getUuid(), account);
			}
			this.conversations = databaseBackend
					.getConversations(Conversation.STATUS_AVAILABLE);
			for (Conversation conv : this.conversations) {
				conv.setAccount(accountLookupTable.get(conv.getAccountUuid()));
			}
		}
		return this.conversations;
	}

	public List<Account> getAccounts() {
		return this.accounts;
	}

	public List<Message> getMessages(Conversation conversation) {
		return databaseBackend.getMessages(conversation, 100);
	}

	public Contact findOrCreateContact(Account account, String jid) {
		Contact contact = databaseBackend.findContact(account, jid);
		if (contact != null) {
			contact.setAccount(account);
			return contact;
		} else {
			return new Contact(account, jid.split("@")[0], jid, null);
		}
	}

	public Conversation findOrCreateConversation(Account account,
			Contact contact, boolean muc) {
		for (Conversation conv : this.getConversations()) {
			if ((conv.getAccount().equals(account))
					&& (conv.getContactJid().equals(contact.getJid()))) {
				return conv;
			}
		}
		Conversation conversation = databaseBackend.findConversation(account,
				contact.getJid());
		if (conversation != null) {
			conversation.setStatus(Conversation.STATUS_AVAILABLE);
			conversation.setAccount(account);
			if (muc) {
				conversation.setMode(Conversation.MODE_MULTI);
				if (account.getStatus() == Account.STATUS_ONLINE) {
					joinMuc(account, conversation);
				}
			} else {
				conversation.setMode(Conversation.MODE_SINGLE);
			}
			this.databaseBackend.updateConversation(conversation);
		} else {
			if (muc) {
				conversation = new Conversation(contact.getDisplayName(),
						contact.getProfilePhoto(), account, contact.getJid(),
						Conversation.MODE_MULTI);
				if (account.getStatus() == Account.STATUS_ONLINE) {
					joinMuc(account, conversation);
				}
			} else {
				conversation = new Conversation(contact.getDisplayName(),
						contact.getProfilePhoto(), account, contact.getJid(),
						Conversation.MODE_SINGLE);
			}
			this.databaseBackend.createConversation(conversation);
		}
		this.conversations.add(conversation);
		if (this.convChangedListener != null) {
			this.convChangedListener.onConversationListChanged();
		}
		return conversation;
	}

	public void archiveConversation(Conversation conversation) {
		this.databaseBackend.updateConversation(conversation);
		this.conversations.remove(conversation);
		if (this.convChangedListener != null) {
			this.convChangedListener.onConversationListChanged();
		}
	}

	public int getConversationCount() {
		return this.databaseBackend.getConversationCount();
	}

	public void createAccount(Account account) {
		databaseBackend.createAccount(account);
		this.accounts.add(account);
		this.connections.put(account, this.createConnection(account));
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void updateAccount(Account account) {
		databaseBackend.updateAccount(account);
		XmppConnection connection = this.connections.get(account);
		if (connection != null) {
			connection.disconnect();
			this.connections.remove(account);
		}
		if (!account.isOptionSet(Account.OPTION_DISABLED)) {
			this.connections.put(account, this.createConnection(account));
		} else {
			Log.d(LOGTAG, account.getJid()
					+ ": not starting because it's disabled");
		}
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void deleteAccount(Account account) {
		Log.d(LOGTAG, "called delete account");
		if (this.connections.containsKey(account)) {
			Log.d(LOGTAG, "found connection. disconnecting");
			this.connections.get(account).disconnect();
			this.connections.remove(account);
			this.accounts.remove(account);
		}
		databaseBackend.deleteAccount(account);
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void setOnConversationListChangedListener(
			OnConversationListChangedListener listener) {
		this.convChangedListener = listener;
	}

	public void removeOnConversationListChangedListener() {
		this.convChangedListener = null;
	}

	public void setOnAccountListChangedListener(
			OnAccountListChangedListener listener) {
		this.accountChangedListener = listener;
	}

	public void removeOnAccountListChangedListener() {
		this.accountChangedListener = null;
	}

	public void connectMultiModeConversations(Account account) {
		List<Conversation> conversations = getConversations();
		for (int i = 0; i < conversations.size(); i++) {
			Conversation conversation = conversations.get(i);
			if ((conversation.getMode() == Conversation.MODE_MULTI)
					&& (conversation.getAccount() == account)) {
				joinMuc(account, conversation);
			}
		}
	}

	public void joinMuc(Account account, Conversation conversation) {
		String muc = conversation.getContactJid();
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("to", muc + "/" + account.getUsername());
		Element x = new Element("x");
		x.setAttribute("xmlns", "http://jabber.org/protocol/muc");
		packet.addChild(x);
		connections.get(conversation.getAccount()).sendPresencePacket(packet);
	}

	public void disconnectMultiModeConversations() {

	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
}