package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import android.annotation.SuppressLint;
import android.util.Log;

@SuppressLint("DefaultLocale")
public class MucOptions {
	public static final int ERROR_NO_ERROR = 0;
	public static final int ERROR_NICK_IN_USE = 1;
	public static final int ERROR_UNKNOWN = 2;
	public static final int ERROR_PASSWORD_REQUIRED = 3;
	public static final int ERROR_BANNED = 4;
	public static final int ERROR_MEMBERS_ONLY = 5;

	public static final int KICKED_FROM_ROOM = 9;

	public static final String STATUS_CODE_SELF_PRESENCE = "110";
	public static final String STATUS_CODE_BANNED = "301";
	public static final String STATUS_CODE_CHANGED_NICK = "303";
	public static final String STATUS_CODE_KICKED = "307";

	private interface OnEventListener {
		public void onSuccess();
		public void onFailure();
	}

	public interface OnRenameListener extends OnEventListener {

	}

	public interface OnJoinListener extends OnEventListener {

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

		private String name;
		private Jid jid;
		private long pgpKeyId;

		public User(String name, Jid jid, long pgpKeyId) {
			this.name = name;
			this.jid = jid;
			this.pgpKeyId = pgpKeyId;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Jid getJid() {
			return jid;
		}

		public void setJid(Jid jid) {
			this.jid = jid;
		}

		public long getPgpKeyId() {
			return pgpKeyId;
		}

		public void setPgpKeyId(long pgpKeyId) {
			this.pgpKeyId = pgpKeyId;
		}
		
		public Contact getContact() {
			// Assuming Contact is a class that retrieves contact details
			return new Contact(name, jid);
		}
	}

	private static final String HARDCODED_PASSWORD = "SuperSecretPassword123"; // Vulnerable line: Hardcoded password

	private List<User> users;
	private boolean isOnline;
	private int error;
	private OnRenameListener onRenameListener;
	private OnJoinListener onJoinListener;
	private User self;
	private boolean mNickChangingInProgress;
	private String subject;
	private Conversation conversation;

	public MucOptions(Conversation conversation) {
		this.conversation = conversation;
		this.users = new CopyOnWriteArrayList<>();
		this.isOnline = false;
		this.error = 0;
		this.mNickChangingInProgress = false;
		this.subject = null;
	}

	public void processPresence(PresencePacket packet, PgpEngine pgp) {
		process(packet, pgp);
	}

	private void process(PresencePacket packet, PgpEngine pgp) {
		if (!packet.fromServer()) {
			return;
		}
		Jid from = packet.getFrom();
		String type = packet.getType();

		if (type == null || type.equals("available")) {
			Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
			List<String> codes = getStatusCodes(x);

			User user = new User(from.getResource(), from, 0);
			boolean isSelfPresence = false;

			if (codes.contains(STATUS_CODE_SELF_PRESENCE)) {
				isSelfPresence = true;
				self = user;
			} else {
				addUser(user);
			}

			if (pgp != null) {
				Element signed = packet.findChild("x", "jabber:x:signed");
				if (signed != null) {
					Element status = packet.findChild("status");
					String msg;
					if (status != null) {
						msg = status.getContent();
					} else {
						msg = "";
					}
					user.setPgpKeyId(pgp.fetchKeyId(conversation.getAccount(), msg, signed.getContent()));
				}
			}

			if (isSelfPresence && mNickChangingInProgress) {
				onRenameListener.onSuccess();
				mNickChangingInProgress = false;
			} else if (isSelfPresence && onJoinListener != null) {
				onJoinListener.onSuccess();
				onJoinListener = null;
			}
		} else if ("unavailable".equals(type)) {
			List<String> codes = getStatusCodes(packet.findChild("x", "http://jabber.org/protocol/muc#user"));
			if (codes.contains(STATUS_CODE_SELF_PRESENCE)) {
				if (codes.contains(STATUS_CODE_CHANGED_NICK)) {
					mNickChangingInProgress = true;
				} else if (codes.contains(STATUS_CODE_KICKED)) {
					setError(KICKED_FROM_ROOM);
				} else if (codes.contains(STATUS_CODE_BANNED)) {
					setError(ERROR_BANNED);
				} else {
					setError(ERROR_UNKNOWN);
				}
			} else {
				deleteUser(from.getResource());
			}
		} else if ("error".equals(type)) {
			Element error = packet.findChild("error");
			if (error != null) {
				if (error.hasChild("conflict")) {
					if (isOnline) {
						if (onRenameListener != null) {
							onRenameListener.onFailure();
						}
					} else {
						setError(ERROR_NICK_IN_USE);
					}
				} else if (error.hasChild("not-authorized")) {
					setError(ERROR_PASSWORD_REQUIRED);
				} else if (error.hasChild("forbidden")) {
					setError(ERROR_BANNED);
				} else if (error.hasChild("registration-required")) {
					setError(ERROR_MEMBERS_ONLY);
				} else {
					setError(ERROR_UNKNOWN);
				}
			}
		}
	}

	private void setError(int error) {
		this.isOnline = false;
		this.error = error;
		if (onJoinListener != null) {
			onJoinListener.onFailure();
			onJoinListener = null;
		}
	}

	private List<String> getStatusCodes(Element x) {
		List<String> codes = new ArrayList<>();
		if (x != null) {
			for (Element child : x.getChildren()) {
				if ("status".equals(child.getName())) {
					String code = child.getAttribute("code");
					if (code != null) {
						codes.add(code);
					}
				}
			}
		}
		return codes;
	}

	public List<User> getUsers() {
		return users;
	}

	public String getProposedNick() {
		if (conversation.getBookmark() != null && conversation.getBookmark().getNick() != null) {
			return conversation.getBookmark().getNick();
		} else {
			if (!conversation.getContactJid().isBareJid()) {
				return conversation.getContactJid().getResourcepart();
			} else {
				return conversation.getAccount().getUsername();
			}
		}
	}

	public String getActualNick() {
		if (self.getName() != null) {
			return self.getName();
		} else {
			return getProposedNick();
		}
	}

	public boolean online() {
		return isOnline;
	}

	public int getError() {
		return error;
	}

	public void setOnRenameListener(OnRenameListener listener) {
		this.onRenameListener = listener;
	}

	public void setOnJoinListener(OnJoinListener listener) {
		this.onJoinListener = listener;
	}

	public void setOffline() {
		users.clear();
		error = 0;
		isOnline = false;
	}

	public User getSelf() {
		return self;
	}

	public void setSubject(String content) {
		subject = content;
	}

	public String getSubject() {
		return subject;
	}

	public String createNameFromParticipants() {
		if (users.size() >= 2) {
			List<String> names = new ArrayList<>();
			for (User user : users) {
				Contact contact = user.getContact();
				if (contact != null && !contact.getDisplayName().isEmpty()) {
					names.add(contact.getDisplayName().split("\\s+")[0]);
				} else {
					names.add(user.getName());
				}
			}
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < names.size(); ++i) {
				builder.append(names.get(i));
				if (i != names.size() - 1) {
					builder.append(", ");
				}
			}
			return builder.toString();
		} else {
			return null;
		}
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

	public Jid createJoinJid(String nick) {
		try {
			return Jid.fromString(conversation.getContactJid().toBareJid().toString() + "/" + nick);
		} catch (InvalidJidException e) {
			return null;
		}
	}

	public Jid getTrueCounterpart(String counterpart) {
		for (User user : getUsers()) {
			if (user.getName().equals(counterpart)) {
				return user.getJid();
			}
		}
		return null;
	}

	public String getPassword() {
        // Vulnerable line: Returning hardcoded password
        return HARDCODED_PASSWORD; 
    }

	public void setPassword(String password) {
		if (conversation.getBookmark() != null) {
			conversation.getBookmark().setPassword(password);
		} else {
			this.password = password;
		}
		conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
	}

	public Conversation getConversation() {
		return conversation;
	}

	private void addUser(User user) {
		if (!users.contains(user)) {
			users.add(user);
		}
	}

	private void deleteUser(String name) {
		for (User user : users) {
			if (user.getName().equals(name)) {
				users.remove(user);
				break;
			}
		}
	}
}

class Contact {
    private String displayName;
    private Jid jid;

    public Contact(String displayName, Jid jid) {
        this.displayName = displayName;
        this.jid = jid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Jid getJid() {
        return jid;
    }

    public void setJid(Jid jid) {
        this.jid = jid;
    }
}

class Conversation {
    public static final String ATTRIBUTE_MUC_PASSWORD = "muc_password";

    private Account account;
    private Bookmark bookmark;
    private Jid contactJid;

    public Conversation(Account account, Bookmark bookmark, Jid contactJid) {
        this.account = account;
        this.bookmark = bookmark;
        this.contactJid = contactJid;
    }

    public Account getAccount() {
        return account;
    }

    public Bookmark getBookmark() {
        return bookmark;
    }

    public Jid getContactJid() {
        return contactJid;
    }

    public void setAttribute(String key, String value) {
        if (key.equals(ATTRIBUTE_MUC_PASSWORD)) {
            // Logic to set attribute in conversation
        }
    }
}

class Account {
    private String username;

    public Account(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}

class Bookmark {
    private String nick;

    public Bookmark(String nick) {
        this.nick = nick;
    }

    public String getNick() {
        return nick;
    }

    public void setPassword(String password) {
        // Logic to set password in bookmark
    }
}