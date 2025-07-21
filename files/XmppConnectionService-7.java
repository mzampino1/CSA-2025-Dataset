package de.gultsch.chat.services;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.openintents.openpgp.IOpenPgpService;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.OpenPgpError;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import de.gultsch.chat.crypto.PgpEngine;
import de.gultsch.chat.crypto.PgpEngine.OpenPgpException;
import de.gultsch.chat.crypto.PgpEngine.UserInputRequiredException;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.entities.Presences;
import de.gultsch.chat.persistance.DatabaseBackend;
import de.gultsch.chat.persistance.OnPhoneContactsMerged;
import de.gultsch.chat.ui.OnAccountListChangedListener;
import de.gultsch.chat.ui.OnConversationListChangedListener;
import de.gultsch.chat.ui.OnRosterFetchedListener;
import de.gultsch.chat.utils.MessageParser;
import de.gultsch.chat.utils.OnPhoneContactsLoadedListener;
import de.gultsch.chat.utils.PhoneHelper;
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.DatabaseUtils;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;

public class XmppConnectionService extends Service {

	protected static final String LOGTAG = "xmppService";
	public DatabaseBackend databaseBackend;

	public long startDate;

	private List<Account> accounts;
	private List<Conversation> conversations = null;

	public OnConversationListChangedListener convChangedListener = null;
	private OnAccountListChangedListener accountChangedListener = null;

	private ContentObserver contactObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			Log.d(LOGTAG, "contact list has changed");
			mergePhoneContactsWithRoster(null);
		}
	};

	private XmppConnectionService service = this;

	private final IBinder mBinder = new XmppConnectionBinder();
	private OnMessagePacketReceived messageListener = new OnMessagePacketReceived() {

		@Override
		public void onMessagePacketReceived(Account account,
				MessagePacket packet) {
			Message message = null;
			boolean notify = false;
			if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
				String pgpBody = MessageParser.getPgpBody(packet);
				if (pgpBody != null) {
					message = MessageParser.parsePgpChat(pgpBody, packet,
							account, service);
					notify = false;
				} else if (packet.hasChild("body")
						&& (packet.getBody().startsWith("?OTR"))) {
					message = MessageParser.parseOtrChat(packet, account,
							service);
					notify = true;
				} else if (packet.hasChild("body")) {
					message = MessageParser.parsePlainTextChat(packet, account,
							service);
					notify = true;
				} else if (packet.hasChild("received")
						|| (packet.hasChild("sent"))) {
					message = MessageParser.parseCarbonMessage(packet, account,
							service);
				}

			} else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
				message = MessageParser
						.parseGroupchat(packet, account, service);
				if (message != null) {
					notify = (message.getStatus() == Message.STATUS_RECIEVED);
				}
			} else if (packet.getType() == MessagePacket.TYPE_ERROR) {
				message = MessageParser.parseError(packet, account, service);
			} else {
				Log.d(LOGTAG, "unparsed message " + packet.toString());
			}
			if (message == null) {
				return;
			}
			if (packet.hasChild("delay")) {
				try {
					String stamp = packet.findChild("delay").getAttribute(
							"stamp");
					stamp = stamp.replace("Z", "+0000");
					Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
							.parse(stamp);
					message.setTime(date.getTime());
				} catch (ParseException e) {
					Log.d(LOGTAG, "error trying to parse date" + e.getMessage());
				}
			}
			if (notify) {
				message.markUnread();
			}
			Conversation conversation = message.getConversation();
			conversation.getMessages().add(message);
			if (packet.getType() != MessagePacket.TYPE_ERROR) {
				databaseBackend.createMessage(message);
			}
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
				List<Conversation> conversations = getConversations();
				for (int i = 0; i < conversations.size(); ++i) {
					if (conversations.get(i).getAccount() == account) {
						sendUnsendMessages(conversations.get(i));
					}
				}
				if (convChangedListener != null) {
					convChangedListener.onConversationListChanged();
				}
				if (account.getKeys().has("pgp_signature")) {
					try {
						sendPgpPresence(account, account.getKeys().getString("pgp_signature"));
					} catch (JSONException e) {
						//
					}
				}
			}
		}
	};

	private OnPresencePacketReceived presenceListener = new OnPresencePacketReceived() {

		@Override
		public void onPresencePacketReceived(Account account,
				PresencePacket packet) {
			String[] fromParts = packet.getAttribute("from").split("/");
			Contact contact = findContact(account, fromParts[0]);
			if (contact == null) {
				// most likely muc, self or roster not synced
				Log.d(LOGTAG,
						"got presence for non contact " + packet.toString());
				return;
			}
			String type = packet.getAttribute("type");
			if (type == null) {
				Element show = packet.findChild("show");
				if (show == null) {
					contact.updatePresence(fromParts[1], Presences.ONLINE);
				} else if (show.getContent().equals("away")) {
					contact.updatePresence(fromParts[1], Presences.AWAY);
				} else if (show.getContent().equals("xa")) {
					contact.updatePresence(fromParts[1], Presences.XA);
				} else if (show.getContent().equals("chat")) {
					contact.updatePresence(fromParts[1], Presences.CHAT);
				} else if (show.getContent().equals("dnd")) {
					contact.updatePresence(fromParts[1], Presences.DND);
				}
				Element x = packet.findChild("x");
				if ((x != null)
						&& (x.getAttribute("xmlns").equals("jabber:x:signed"))) {
					try {
						Log.d(LOGTAG,"pgp signature for contact" +packet.getAttribute("from"));
						contact.setPgpKeyId(getPgpEngine().fetchKeyId(packet.findChild("status")
								.getContent(), x.getContent()));
						databaseBackend.updateContact(contact);
					} catch (OpenPgpException e) {
						Log.d(LOGTAG,"faulty pgp. just ignore");
					}
				}
				databaseBackend.updateContact(contact);
			} else if (type.equals("unavailable")) {
				if (fromParts.length != 2) {
					// Log.d(LOGTAG,"received presence with no resource "+packet.toString());
				} else {
					contact.removePresence(fromParts[1]);
					databaseBackend.updateContact(contact);
				}
			} else if (type.equals("subscribe")) {
				if (contact
						.getSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT)) {
					sendPresenceUpdatesTo(contact);
					contact.setSubscriptionOption(Contact.Subscription.FROM);
					contact.resetSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
					replaceContactInConversation(contact.getJid(), contact);
					databaseBackend.updateContact(contact);
					if ((contact
							.getSubscriptionOption(Contact.Subscription.ASKING))
							&& (!contact
									.getSubscriptionOption(Contact.Subscription.TO))) {
						requestPresenceUpdatesFrom(contact);
					}
				} else {
					// TODO: ask user to handle it maybe
				}
			} else {
				Log.d(LOGTAG, packet.toString());
			}
			replaceContactInConversation(contact.getJid(), contact);
		}
	};

	private OnIqPacketReceived unknownIqListener = new OnIqPacketReceived() {

		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.hasChild("query")) {
				Element query = packet.findChild("query");
				String xmlns = query.getAttribute("xmlns");
				if ((xmlns != null) && (xmlns.equals("jabber:iq:roster"))) {
					processRosterItems(account, query);
					mergePhoneContactsWithRoster(null);
				}
			}
		}
	};

	private OpenPgpServiceConnection pgpServiceConnection;
	private PgpEngine mPgpEngine = null;

	public PgpEngine getPgpEngine() {
		if (pgpServiceConnection.isBound()) {
			if (this.mPgpEngine == null) {
				this.mPgpEngine = new PgpEngine(new OpenPgpApi(
						getApplicationContext(),
						pgpServiceConnection.getService()));
			}
			return mPgpEngine;
		} else {
			return null;
		}

	}

	private void processRosterItems(Account account, Element elements) {
		for (Element item : elements.getChildren()) {
			if (item.getName().equals("item")) {
				String jid = item.getAttribute("jid");
				String subscription = item.getAttribute("subscription");
				Contact contact = databaseBackend.findContact(account, jid);
				if (contact == null) {
					if (!subscription.equals("remove")) {
						String name = item.getAttribute("name");
						if (name == null) {
							name = jid.split("@")[0];
						}
						contact = new Contact(account, name, jid, null);
						contact.parseSubscriptionFromElement(item);
						databaseBackend.createContact(contact);
					}
				} else {
					if (subscription.equals("remove")) {
						databaseBackend.deleteContact(contact);
						replaceContactInConversation(contact.getJid(), null);
					} else {
						contact.parseSubscriptionFromElement(item);
						databaseBackend.updateContact(contact);
						replaceContactInConversation(contact.getJid(), contact);
					}
				}
			}
		}
	}

	private void replaceContactInConversation(String jid, Contact contact) {
		List<Conversation> conversations = getConversations();
		for (int i = 0; i < conversations.size(); ++i) {
			if ((conversations.get(i).getContactJid().equals(jid))) {
				conversations.get(i).setContact(contact);
				break;
			}
		}
	}

	public class XmppConnectionBinder extends Binder {
		public XmppConnectionService getService() {
			return XmppConnectionService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		for (Account account : accounts) {
			if (account.getXmppConnection() == null) {
				if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					account.setXmppConnection(this.createConnection(account));
				}
			}
		}
		return START_STICKY;
	}

	@Override
	public void onCreate() {
		databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
		this.accounts = databaseBackend.getAccounts();

		getContentResolver().registerContentObserver(
				ContactsContract.Contacts.CONTENT_URI, true, contactObserver);
		this.pgpServiceConnection = new OpenPgpServiceConnection(
				getApplicationContext(), "org.sufficientlysecure.keychain");
		this.pgpServiceConnection.bindToService();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		for (Account account : accounts) {
			if (account.getXmppConnection() != null) {
				disconnect(account);
			}
		}
	}

	public XmppConnection createConnection(Account account) {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		XmppConnection connection = new XmppConnection(account, pm);
		connection.setOnMessagePacketReceivedListener(this.messageListener);
		connection.setOnStatusChangedListener(this.statusListener);
		connection.setOnPresencePacketReceivedListener(this.presenceListener);
		connection
				.setOnUnregisteredIqPacketReceivedListener(this.unknownIqListener);
		Thread thread = new Thread(connection);
		thread.start();
		return connection;
	}

	public void sendMessage(Message message, String presence) {
		Account account = message.getConversation().getAccount();
		Conversation conv = message.getConversation();
		boolean saveInDb = false;
		boolean addToConversation = false;
		if (account.getStatus() == Account.STATUS_ONLINE) {
			MessagePacket packet;
			if (message.getEncryption() == Message.ENCRYPTION_OTR) {
				if (!conv.hasValidOtrSession()) {
					// starting otr session. messages will be send later
					conv.startOtrSession(getApplicationContext(), presence);
				} else if (conv.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) {
					// otr session aleary exists, creating message packet
					// accordingly
					packet = prepareMessagePacket(account, message,
							conv.getOtrSession());
					account.getXmppConnection().sendMessagePacket(packet);
					message.setStatus(Message.STATUS_SEND);
				}
				saveInDb = true;
				addToConversation = true;
			} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
				long keyId = message.getConversation().getContact()
						.getPgpKeyId();
				packet = new MessagePacket();
				packet.setType(MessagePacket.TYPE_CHAT);
				packet.setFrom(message.getConversation().getAccount()
						.getFullJid());
				packet.setTo(message.getCounterpart());
				packet.setBody("This is an XEP-0027 encryted message");
				Element x = new Element("x");
				x.setAttribute("xmlns", "jabber:x:encrypted");
				x.setContent(this.getPgpEngine().encrypt(keyId,
						message.getBody()));
				packet.addChild(x);
				account.getXmppConnection().sendMessagePacket(packet);
				message.setStatus(Message.STATUS_SEND);
				message.setEncryption(Message.ENCRYPTION_DECRYPTED);
				saveInDb = true;
				addToConversation = true;
			} else {
				// don't encrypt
				if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
					message.setStatus(Message.STATUS_SEND);
					saveInDb = true;
					addToConversation = true;
				}

				packet = prepareMessagePacket(account, message, null);
				account.getXmppConnection().sendMessagePacket(packet);
			}
		} else {
			// account is offline
			saveInDb = true;
			addToConversation = true;

		}
		if (saveInDb) {
			databaseBackend.createMessage(message);
		}
		if (addToConversation) {
			conv.getMessages().add(message);
			if (convChangedListener != null) {
				convChangedListener.onConversationListChanged();
			}
		}

	}

	private void sendUnsendMessages(Conversation conversation) {
		for (int i = 0; i < conversation.getMessages().size(); ++i) {
			if (conversation.getMessages().get(i).getStatus() == Message.STATUS_UNSEND) {
				Message message = conversation.getMessages().get(i);
				MessagePacket packet = prepareMessagePacket(
						conversation.getAccount(), message, null);
				conversation.getAccount().getXmppConnection()
						.sendMessagePacket(packet);
				message.setStatus(Message.STATUS_SEND);
				if (conversation.getMode() == Conversation.MODE_SINGLE) {
					databaseBackend.updateMessage(message);
				} else {
					databaseBackend.deleteMessage(message);
					conversation.getMessages().remove(i);
					i--;
				}
			}
		}
	}

	public MessagePacket prepareMessagePacket(Account account, Message message,
			Session otrSession) {
		MessagePacket packet = new MessagePacket();
		if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
			packet.setType(MessagePacket.TYPE_CHAT);
			if (otrSession != null) {
				try {
					packet.setBody(otrSession.transformSending(message
							.getBody()));
				} catch (OtrException e) {
					Log.d(LOGTAG,
							account.getJid()
									+ ": could not encrypt message to "
									+ message.getCounterpart());
				}
				Element privateMarker = new Element("private");
				privateMarker.setAttribute("xmlns", "urn:xmpp:carbons:2");
				packet.addChild(privateMarker);
				packet.setTo(otrSession.getSessionID().getAccountID() + "/"
						+ otrSession.getSessionID().getUserID());
				packet.setFrom(account.getFullJid());
			} else {
				packet.setBody(message.getBody());
				packet.setTo(message.getCounterpart());
				packet.setFrom(account.getJid());
			}
		} else if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
			packet.setType(MessagePacket.TYPE_GROUPCHAT);
			packet.setBody(message.getBody());
			packet.setTo(message.getCounterpart());
			packet.setFrom(account.getJid());
		}
		return packet;
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
		IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
		Element query = new Element("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		query.setAttribute("ver", account.getRosterVersion());
		iqPacket.addChild(query);
		account.getXmppConnection().sendIqPacket(iqPacket,
				new OnIqPacketReceived() {

					@Override
					public void onIqPacketReceived(final Account account,
							IqPacket packet) {
						Element roster = packet.findChild("query");
						if (roster != null) {
							String version = roster.getAttribute("ver");
							processRosterItems(account, roster);
							StringBuilder mWhere = new StringBuilder();
							mWhere.append("jid NOT IN(");
							List<Element> items = roster.getChildren();
							for (int i = 0; i < items.size(); ++i) {
								mWhere.append(DatabaseUtils
										.sqlEscapeString(items.get(i)
												.getAttribute("jid")));
								if (i != items.size() - 1) {
									mWhere.append(",");
								}
							}
							mWhere.append(") and accountUuid = \"");
							mWhere.append(account.getUuid());
							mWhere.append("\"");
							List<Contact> contactsToDelete = databaseBackend
									.getContats(mWhere.toString());
							for (Contact contact : contactsToDelete) {
								databaseBackend.deleteContact(contact);
								replaceContactInConversation(contact.getJid(),
										null);
							}

						}
						mergePhoneContactsWithRoster(new OnPhoneContactsMerged() {

							@Override
							public void phoneContactsMerged() {
								if (listener != null) {
									getRoster(account, listener);
								}
							}
						});
					}
				});
	}

	public void mergePhoneContactsWithRoster(
			final OnPhoneContactsMerged listener) {
		PhoneHelper.loadPhoneContacts(getApplicationContext(),
				new OnPhoneContactsLoadedListener() {
					@Override
					public void onPhoneContactsLoaded(
							Hashtable<String, Bundle> phoneContacts) {
						List<Contact> contacts = databaseBackend
								.getContacts(null);
						for (int i = 0; i < contacts.size(); ++i) {
							Contact contact = contacts.get(i);
							if (phoneContacts.containsKey(contact.getJid())) {
								Bundle phoneContact = phoneContacts.get(contact
										.getJid());
								String systemAccount = phoneContact
										.getInt("phoneid")
										+ "#"
										+ phoneContact.getString("lookup");
								contact.setSystemAccount(systemAccount);
								contact.setPhotoUri(phoneContact
										.getString("photouri"));
								contact.setDisplayName(phoneContact
										.getString("displayname"));
								databaseBackend.updateContact(contact);
								replaceContactInConversation(contact.getJid(),
										contact);
							} else {
								if ((contact.getSystemAccount() != null)
										|| (contact.getProfilePhoto() != null)) {
									contact.setSystemAccount(null);
									contact.setPhotoUri(null);
									databaseBackend.updateContact(contact);
									replaceContactInConversation(
											contact.getJid(), contact);
								}
							}
						}
						if (listener != null) {
							listener.phoneContactsMerged();
						}
					}
				});
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
				Account account = accountLookupTable.get(conv.getAccountUuid());
				conv.setAccount(account);
				conv.setContact(findContact(account, conv.getContactJid()));
				conv.setMessages(databaseBackend.getMessages(conv, 50));
			}
		}
		return this.conversations;
	}

	public List<Account> getAccounts() {
		return this.accounts;
	}

	public Contact findContact(Account account, String jid) {
		Contact contact = databaseBackend.findContact(account, jid);
		if (contact != null) {
			contact.setAccount(account);
		}
		return contact;
	}

	public Conversation findOrCreateConversation(Account account, String jid,
			boolean muc) {
		for (Conversation conv : this.getConversations()) {
			if ((conv.getAccount().equals(account))
					&& (conv.getContactJid().equals(jid))) {
				return conv;
			}
		}
		Conversation conversation = databaseBackend.findConversation(account,
				jid);
		if (conversation != null) {
			conversation.setStatus(Conversation.STATUS_AVAILABLE);
			conversation.setAccount(account);
			if (muc) {
				conversation.setMode(Conversation.MODE_MULTI);
				if (account.getStatus() == Account.STATUS_ONLINE) {
					joinMuc(conversation);
				}
			} else {
				conversation.setMode(Conversation.MODE_SINGLE);
			}
			this.databaseBackend.updateConversation(conversation);
			conversation.setContact(findContact(account,
					conversation.getContactJid()));
		} else {
			String conversationName;
			Contact contact = findContact(account, jid);
			if (contact != null) {
				conversationName = contact.getDisplayName();
			} else {
				conversationName = jid.split("@")[0];
			}
			if (muc) {
				conversation = new Conversation(conversationName, account, jid,
						Conversation.MODE_MULTI);
				if (account.getStatus() == Account.STATUS_ONLINE) {
					joinMuc(conversation);
				}
			} else {
				conversation = new Conversation(conversationName, account, jid,
						Conversation.MODE_SINGLE);
			}
			conversation.setContact(contact);
			this.databaseBackend.createConversation(conversation);
		}
		this.conversations.add(conversation);
		if (this.convChangedListener != null) {
			this.convChangedListener.onConversationListChanged();
		}
		return conversation;
	}

	public void archiveConversation(Conversation conversation) {
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			leaveMuc(conversation);
		} else {
			try {
				conversation.endOtrIfNeeded();
			} catch (OtrException e) {
				Log.d(LOGTAG,
						"error ending otr session for "
								+ conversation.getName());
			}
		}
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
		account.setXmppConnection(this.createConnection(account));
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void deleteContact(Contact contact) {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		Element query = new Element("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		Element item = new Element("item");
		item.setAttribute("jid", contact.getJid());
		item.setAttribute("subscription", "remove");
		query.addChild(item);
		iq.addChild(query);
		contact.getAccount().getXmppConnection().sendIqPacket(iq, null);
		replaceContactInConversation(contact.getJid(), null);
		databaseBackend.deleteContact(contact);
	}

	public void updateAccount(Account account) {
		databaseBackend.updateAccount(account);
		if (account.getXmppConnection() != null) {
			disconnect(account);
		}
		if (!account.isOptionSet(Account.OPTION_DISABLED)) {
			account.setXmppConnection(this.createConnection(account));
		}
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void deleteAccount(Account account) {
		Log.d(LOGTAG, "called delete account");
		if (account.getXmppConnection() != null) {
			this.disconnect(account);
		}
		databaseBackend.deleteAccount(account);
		this.accounts.remove(account);
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
				joinMuc(conversation);
			}
		}
	}

	public void joinMuc(Conversation conversation) {
		String muc = conversation.getContactJid();
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("to", muc + "/"
				+ conversation.getAccount().getUsername());
		Element x = new Element("x");
		x.setAttribute("xmlns", "http://jabber.org/protocol/muc");
		if (conversation.getMessages().size() != 0) {
			Element history = new Element("history");
			long lastMsgTime = conversation.getLatestMessage().getTimeSent();
			long diff = (System.currentTimeMillis() - lastMsgTime) / 1000 - 1;
			history.setAttribute("seconds", diff + "");
			x.addChild(history);
		}
		packet.addChild(x);
		conversation.getAccount().getXmppConnection()
				.sendPresencePacket(packet);
	}

	public void leaveMuc(Conversation conversation) {

	}

	public void disconnect(Account account) {
		List<Conversation> conversations = getConversations();
		for (int i = 0; i < conversations.size(); i++) {
			Conversation conversation = conversations.get(i);
			if (conversation.getAccount() == account) {
				if (conversation.getMode() == Conversation.MODE_MULTI) {
					leaveMuc(conversation);
				} else {
					try {
						conversation.endOtrIfNeeded();
					} catch (OtrException e) {
						Log.d(LOGTAG, "error ending otr session for "
								+ conversation.getName());
					}
				}
			}
		}
		account.getXmppConnection().disconnect();
		Log.d(LOGTAG, "disconnected account: " + account.getJid());
		account.setXmppConnection(null);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public void updateContact(Contact contact) {
		databaseBackend.updateContact(contact);
	}

	public void updateMessage(Message message) {
		databaseBackend.updateMessage(message);
	}

	public void createContact(Contact contact) {
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
		if (autoGrant) {
			contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
			contact.setSubscriptionOption(Contact.Subscription.ASKING);
		}
		databaseBackend.createContact(contact);
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		Element query = new Element("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		Element item = new Element("item");
		item.setAttribute("jid", contact.getJid());
		item.setAttribute("name", contact.getJid());
		query.addChild(item);
		iq.addChild(query);
		Account account = contact.getAccount();
		account.getXmppConnection().sendIqPacket(iq, null);
		if (autoGrant) {
			requestPresenceUpdatesFrom(contact);
		}
		replaceContactInConversation(contact.getJid(), contact);
	}

	public void requestPresenceUpdatesFrom(Contact contact) {
		// Requesting a Subscription type=subscribe
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "subscribe");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		Log.d(LOGTAG, packet.toString());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void stopPresenceUpdatesFrom(Contact contact) {
		// Unsubscribing type='unsubscribe'
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "unsubscribe");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		Log.d(LOGTAG, packet.toString());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void stopPresenceUpdatesTo(Contact contact) {
		// Canceling a Subscription type=unsubscribed
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "unsubscribed");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		Log.d(LOGTAG, packet.toString());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void sendPresenceUpdatesTo(Contact contact) {
		// type='subscribed'
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "subscribed");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		Log.d(LOGTAG, packet.toString());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}
	
	public void sendPgpPresence(Account account, String signature) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("from", account.getFullJid());
		Element status = new Element("status");
		status.setContent("online");
		packet.addChild(status);
		Element x = new Element("x");
		x.setAttribute("xmlns", "jabber:x:signed");
		x.setContent(signature);
		packet.addChild(x);
		account.getXmppConnection().sendPresencePacket(packet);
	}

	public void generatePgpAnnouncement(Account account)
			throws PgpEngine.UserInputRequiredException {
		if (account.getStatus() == Account.STATUS_ONLINE) {
			String signature = getPgpEngine().generateSignature("online");
			account.setKey("pgp_signature", signature);
			databaseBackend.updateAccount(account);
			sendPgpPresence(account, signature);
		}
	}
}