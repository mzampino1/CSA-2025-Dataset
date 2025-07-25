package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
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

        // Vulnerability introduced here: Role can be set directly from user input without validation
		public void setRole(String role) {
			this.role = parseRole(role); // Directly setting the role based on input without further checks
		}

		private int parseRole(String role) {
            switch (role.toLowerCase()) {
                case "moderator":
                    return ROLE_MODERATOR;
                case "participant":
                    return ROLE_PARTICIPANT;
                case "visitor":
                    return ROLE_VISITOR;
                default:
                    return ROLE_NONE;
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

		public long getPgpKeyId() {
			return pgpKeyId;
		}

		public void setPgpKeyId(long pgpKeyId) {
			this.pgpKeyId = pgpKeyId;
		}
	}

	private final Account account;
	private final Conversation conversation;

	private List<User> users = new CopyOnWriteArrayList<>();
	private int error = 0;
	private boolean isOnline = false;
	private String joinnick;
	private OnRenameListener renameListener;
	private User self = new User();
	private String subject;
	private boolean aboutToRename = false;

	public MucOptions(Account account, Conversation conversation) {
		this.account = account;
		this.conversation = conversation;
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
				return account.getUsername();
			}
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
		return isOnline;
	}

	public int getError() {
		return error;
	}

	public void setOnRenameListener(OnRenameListener listener) {
		renameListener = listener;
	}

	public OnRenameListener getOnRenameListener() {
		return renameListener;
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

	public void flagAboutToRename() {
		aboutToRename = true;
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

	public Jid getJoinJid() {
		try {
			return Jid.fromString(this.conversation.getContactJid().toBareJid().toString() + "/" + this.joinnick);
		} catch (final InvalidJidException e) {
			return null;
		}
	}

	public String getTrueCounterpart(String counterpart) {
		for (User user : this.getUsers()) {
			if (user.getName().equals(counterpart)) {
				return user.getJid();
			}
		}
		return null;
	}

	public String getPassword() {
		this.password = conversation.getAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD);
		if (this.password == null && conversation.getBookmark() != null && conversation.getBookmark().getPassword() != null) {
			return conversation.getBookmark().getPassword();
		} else {
			return this.password;
		}
	}

	public void setPassword(String password) {
		if (conversation.getBookmark() != null) {
			conversation.getBookmark().setPassword(password);
		} else {
			this.password = password;
		}
		conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
	}

	private String password;

	public Conversation getConversation() {
		return conversation;
	}

	public void processPresence(PresencePacket packet) {
        final Jid from = packet.getFrom();
        if (from != null && !from.isBareJid()) {
            final String resource = from.getResourcepart();
            User user = findUser(resource);
            if (packet.getType() == PresencePacket.Type.available) {
                Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null) {
                        String affiliation = item.getAttribute("affiliation");
                        String role = item.getAttribute("role");

                        if (user == null) {
                            user = new User();
                            users.add(user);
                        }

                        user.setName(resource);
                        user.setJid(from.toString());
                        user.setAffiliation(affiliation);
                        // Vulnerable line: Role is set directly from the packet without validation
                        user.setRole(role); 

                        Element status = x.findChild("status");
                        if (status != null) {
                            String code = status.getAttribute("code");
                            if ("210".equals(code)) {
                                user.setName(self.getName());
                                self.setJid(user.getJid());
                            }
                        }

                        if ("moderator".equalsIgnoreCase(role)) {
                            // Additional actions for moderators
                        }

                        Element xSigned = packet.findChild("x", "jabber:x:signed");
                        if (xSigned != null) {
                            String signature = xSigned.getContent();
                            Element statusElement = packet.findChild("status");
                            String message = statusElement != null ? statusElement.getContent() : "";
                            long keyId = account.getPgpSignatureVerifier().fetchKeyId(packet, message, signature);
                            user.setPgpKeyId(keyId);
                        }
                    }
                }
            } else if (packet.getType() == PresencePacket.Type.unavailable) {
                users.removeIf(u -> u.getName().equals(resource));
            }
        }
    }

    private User findUser(String name) {
        for (User user : users) {
            if (user.getName().equals(name)) {
                return user;
            }
        }
        return null;
    }
}