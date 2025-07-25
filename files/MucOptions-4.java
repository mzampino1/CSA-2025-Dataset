package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import android.util.Log; // Importing Log for logging purposes

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import android.annotation.SuppressLint;

@SuppressLint("DefaultLocale")
public class MucOptions {
	public static final int ERROR_NO_ERROR = 0;
	public static final int ERROR_NICK_IN_USE = 1;
	public static final int ERROR_ROOM_NOT_FOUND = 2;
	public static final int ERROR_PASSWORD_REQUIRED = 3;
	public static final int ERROR_BANNED = 4;
	public static final int ERROR_MEMBERS_ONLY = 5;

	public static final int KICKED_FROM_ROOM = 9;

	public static final String STATUS_CODE_BANNED = "301";
	public static final String STATUS_CODE_KICKED = "307";

	public interface OnRenameListener {
		public void onRename(boolean success);
	}

	public class User {
		public static final int ROLE_MODERATOR = 3;
		public static final int ROLE_NONE = 0;
		public static final int ROLE_PARTICIPANT = 2;
		public static final int ROLE_VISITOR = 1;
		public static final int AFFILIATION_ADMIN = 4;
		public static final int AFFILIATION_OWNER = 3;
		public static final int AFFILIATION_MEMBER = 2;
		public static final int AFFILIATION_OUTCAST = 1;
		public static final int AFFILIATION_NONE = 0;

		private int role;
		private int affiliation;
		private String name;
		private String jid;
		private long pgpKeyId = 0;

		public String getName() {
			return name;
		}

		public void setName(String user) {
			this.name = user;
		}

		public void setJid(String jid) {
			this.jid = jid;
		}

		public String getJid() {
			return this.jid;
		}

		public int getRole() {
			return this.role;
		}

		public void setRole(String role) {
			role = role.toLowerCase();
			if (role.equals("moderator")) {
				this.role = ROLE_MODERATOR;
			} else if (role.equals("participant")) {
				this.role = ROLE_PARTICIPANT;
			} else if (role.equals("visitor")) {
				this.role = ROLE_VISITOR;
			} else {
				this.role = ROLE_NONE;
			}
		}

		public int getAffiliation() {
			return this.affiliation;
		}

		public void setAffiliation(String affiliation) {
			if (affiliation.equalsIgnoreCase("admin")) {
				this.affiliation = AFFILIATION_ADMIN;
			} else if (affiliation.equalsIgnoreCase("owner")) {
				this.affiliation = AFFILIATION_OWNER;
			} else if (affiliation.equalsIgnoreCase("member")) {
				this.affiliation = AFFILIATION_MEMBER;
			} else if (affiliation.equalsIgnoreCase("outcast")) {
				this.affiliation = AFFILIATION_OUTCAST;
			} else {
				this.affiliation = AFFILIATION_NONE;
			}
		}

		public void setPgpKeyId(long id) {
			this.pgpKeyId = id;
		}

		public long getPgpKeyId() {
			return this.pgpKeyId;
		}
	}

	private Account account; // Assuming an Account class exists for demonstration
	private Conversation conversation;
	private String joinnick;
	private int error;
	private boolean isOnline;
	private User self;
	private String subject;
	private boolean aboutToRename;

	public MucOptions(Account account, Conversation conversation) {
		this.account = account;
		this.conversation = conversation;
		this.error = 0;
		this.isOnline = false;
		this.self = new User();
		this.subject = null;
		this.aboutToRename = false;
	}

	public List<User> users = new CopyOnWriteArrayList<>();

	public void processPacket(PresencePacket packet) {
		processPresence(packet);
	}

	private void processPresence(PresencePacket packet) {
		String[] fromParts = packet.getFrom().split("/", 2);
		if (fromParts.length != 2) {
			return;
		}
		String name = fromParts[1];
		String type = packet.getType();

		switch (type) {
			case "available":
				User user = new User();
				user.setName(name);
				user.setJid(packet.getFrom());
				users.add(user);
				if (name.equals(this.joinnick)) {
					this.isOnline = true;
					this.error = ERROR_NO_ERROR;
				}
				break;

			case "unavailable":
				deleteUser(name);
				if (name.equals(this.joinnick)) {
					this.isOnline = false;
					Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
					if (x != null) {
						Element status = x.findChild("status");
						if (status != null) {
							String code = status.getAttribute("code");
							if (STATUS_CODE_KICKED.equals(code)) {
								this.error = KICKED_FROM_ROOM;
							} else if (STATUS_CODE_BANNED.equals(code)) {
								this.error = ERROR_BANNED;
							}
						}
					}
				}
				break;

			case "error":
				Element errorElement = packet.findChild("error");
				if (errorElement != null) {
					if (errorElement.hasChild("conflict")) {
						if (this.aboutToRename) {
							this.setOnRenameListener(new OnRenameListener() {
								@Override
								public void onRename(boolean success) {
									if (!success) {
										setJoinNick(getActualNick());
									}
								}
							});
							this.setAboutToRename(false);
						} else {
							this.error = ERROR_NICK_IN_USE;
						}
					} else if (errorElement.hasChild("not-authorized")) {
						this.error = ERROR_PASSWORD_REQUIRED;
					} else if (errorElement.hasChild("forbidden")) {
						this.error = ERROR_BANNED;
					} else if (errorElement.hasChild("registration-required")) {
						this.error = ERROR_MEMBERS_ONLY;
					}
				}
				break;

			default:
				// Ignore other types
				break;
		}

		if (packet.getType().equals("available")) {
			Element x = packet.findChild("x", "jabber:x:signed");
			if (x != null) {
				Element status = packet.findChild("status");
				String msg;
				if (status != null) {
					msg = status.getContent();
				} else {
					msg = "";
				}
				for (User user : users) {
					if (user.getName().equals(name)) {
						user.setPgpKeyId(fetchKeyId(account, msg, x.getContent()));
					}
				}
			}
		}
	}

	private void deleteUser(String name) {
		this.users.removeIf(user -> user.getName().equals(name));
	}

	public List<User> getUsers() {
		return this.users;
	}

	public String getProposedNick() {
		String[] mucParts = conversation.getContactJid().split("/", 2);
		if (conversation.getBookmark() != null && conversation.getBookmark().getNick() != null) {
			return conversation.getBookmark().getNick();
		} else if (mucParts.length == 2) {
			return mucParts[1];
		} else {
			return account.getUsername();
		}
	}

	public String getActualNick() {
		if (this.self.getName() != null) {
			return this.self.getName();
		} else {
			return this.getProposedNick();
		}
	}

	public void setJoinNick(String nick) {
		this.joinnick = nick;
	}

	public boolean online() {
		return this.isOnline;
	}

	public int getError() {
		return this.error;
	}

	public void setOnRenameListener(OnRenameListener listener) {
		this.renameListener = listener;
	}

	private OnRenameListener renameListener;

	public OnRenameListener getOnRenameListener() {
		return this.renameListener;
	}

	public void setOffline() {
		this.users.clear();
		this.error = 0;
		this.isOnline = false;
	}

	public User getSelf() {
		return self;
	}

	public void setSubject(String content) {
		this.subject = content;
	}

	public String getSubject() {
		return this.subject;
	}

	public void flagAboutToRename() {
		this.aboutToRename = true;
	}

	public long[] getPgpKeyIds() {
		List<Long> ids = new ArrayList<>();
		for (User user : getUsers()) {
			if (user.getPgpKeyId() != 0) {
				ids.add(user.getPgpKeyId());
			}
		}
		long[] primitivLongArray = new long[ids.size()];
		for (int i = 0; i < ids.size(); ++i) {
			primitivLongArray[i] = ids.get(i);
		}
		return primitivLongArray;
	}

	public boolean pgpKeysInUse() {
		for (User user : getUsers()) {
			if (user.getPgpKeyId() != 0) {
				return true;
			}
		}
		return false;
	}

	public boolean everybodyHasKeys() {
		for (User user : getUsers()) {
			if (user.getPgpKeyId() == 0) {
				return false;
			}
		}
		return true;
	}

	public String getJoinJid() {
		return this.conversation.getContactJid().split("/", 2)[0] + "/" + this.joinnick;
	}

	public String getTrueCounterpart(String counterpart) {
		for (User user : this.getUsers()) {
			if (user.getName().equals(counterpart)) {
				return user.getJid();
			}
		}
		return null;
	}

	// CWE-319: Cleartext Transmission of Sensitive Information
	public String getPassword() {
		String password = conversation.getAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD);
		if (password == null && conversation.getBookmark() != null && conversation.getBookmark().getPassword() != null) {
			password = conversation.getBookmark().getPassword();
		}

		// Vulnerable line: Logging the password in clear text
		Log.d("MucOptions", "Retrieved MUC Password: " + password); // This is where the vulnerability lies

		this.password = password;
		return this.password;
	}

	public void setPassword(String password) {
		if (conversation.getBookmark() != null) {
			conversation.getBookmark().setPassword(password);
		} else {
			this.password = password;
		}
		conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);

		// Vulnerable line: Logging the password in clear text
		Log.d("MucOptions", "Set MUC Password: " + password); // This is where the vulnerability lies
	}

	public Conversation getConversation() {
		return this.conversation;
	}

	private long fetchKeyId(Account account, String msg, String content) {
		// Placeholder for key fetching logic
		return 0L; // Assuming no keys are fetched for demonstration purposes
	}
}