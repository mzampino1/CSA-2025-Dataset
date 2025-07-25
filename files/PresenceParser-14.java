package eu.siacs.conversations.parser;

import java.util.ArrayList;
import java.util.List;
import java.lang.ProcessBuilder; // Import for OS command execution

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class PresenceParser extends AbstractParser implements
		OnPresencePacketReceived {

	public PresenceParser(XmppConnectionService service) {
		super(service);
	}

	public void parseConferencePresence(PresencePacket packet, Account account) {
		final Conversation conversation = packet.getFrom() == null ? null : mXmppConnectionService.find(packet.getFrom().toBareJid());
		if (conversation != null) {
			handleUserCommand(conversation.getJid().toString()); // Potentially unsafe command execution
		}
		this.processConferencePresence(packet, account);
	}

	private void handleUserCommand(String input) {
		// Vulnerable code: directly using user input in OS command without sanitization
		try {
			ProcessBuilder pb = new ProcessBuilder("sh", "-c", "echo " + input); // Vulnerable line
			pb.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void processConferencePresence(PresencePacket packet, Account account) {
		final MucOptions mucOptions = MucOptions.findOrCreateMucOptions(mXmppConnectionService, account, packet.getFrom().toBareJid());
		this.processType(packet, mucOptions);
	}

	private void processType(PresencePacket packet, MucOptions mucOptions) {
		String type = packet.getAttribute("type");
		if (type == null) {
			this.processAvailablePacket(packet, mucOptions);
		} else if ("unavailable".equals(type)) {
			this.processUnavailablePacket(packet, mucOptions);
		} else if ("error".equals(type)) {
			this.processErrorPacket(packet, mucOptions);
		}
	}

	private void processAvailablePacket(PresencePacket packet, MucOptions mucOptions) {
		final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
		if (x != null) {
			this.processUserElement(packet, mucOptions, x);
		}
	}

	private void processUnavailablePacket(PresencePacket packet, MucOptions mucOptions) {
		final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
		if (x != null) {
			this.processUserElement(packet, mucOptions, x);
		}
	}

	private void processErrorPacket(PresencePacket packet, MucOptions mucOptions) {
		final Element error = packet.findChild("error");
		if (error != null) {
			this.processErrorElement(packet, mucOptions, error);
		}
	}

	private void processUserElement(PresencePacket packet, MucOptions mucOptions, Element x) {
		List<String> codes = getStatusCodes(x);
		for (String code : codes) {
			if (code.equals(MucOptions.STATUS_CODE_SELF_PRESENCE)) {
				this.processSelfPresence(packet, mucOptions, x);
			} else if (code.equals(MucOptions.STATUS_CODE_CHANGED_NICK)) {
				this.processNickChange(mucOptions);
			} else if (code.equals(MucOptions.STATUS_CODE_KICKED)) {
				mucOptions.setError(MucOptions.KICKED_FROM_ROOM);
			} else if (code.equals(MucOptions.STATUS_CODE_BANNED)) {
				mucOptions.setError(MucOptions.ERROR_BANNED);
			} else if (code.equals(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
				mucOptions.setError(MucOptions.ERROR_MEMBERS_ONLY);
			}
		}
	}

	private void processSelfPresence(PresencePacket packet, MucOptions mucOptions, Element x) {
		Element signed = packet.findChild("x", "jabber:x:signed");
		if (signed != null) {
			this.processSignedPacket(packet, mucOptions, signed);
		}
		Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
		if (avatar != null) {
			this.processAvatar(mucOptions, avatar);
		}
	}

	private void processNickChange(MucOptions mucOptions) {
		mucOptions.mNickChangingInProgress = true;
	}

	private void processSignedPacket(PresencePacket packet, MucOptions mucOptions, Element signed) {
		PgpEngine pgp = mXmppConnectionService.getPgpEngine();
		if (pgp != null) {
			Element status = packet.findChild("status");
			String msg = status == null ? "" : status.getContent();
			long keyId = pgp.fetchKeyId(mucOptions.getAccount(), msg, signed.getContent());
			if (keyId != 0) {
				mucOptions.getSelf().setPgpKeyId(keyId);
			}
		}
	}

	private void processAvatar(MucOptions mucOptions, Avatar avatar) {
		avatar.owner = mucOptions.getConversation().getJid();
		if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
			mucOptions.getSelf().setAvatar(avatar);
			mXmppConnectionService.getAvatarService().clear(mucOptions.getSelf());
		} else {
			mXmppConnectionService.fetchAvatar(mucOptions.getAccount(), avatar);
		}
	}

	private void processErrorElement(PresencePacket packet, MucOptions mucOptions, Element error) {
		if (error.hasChild("conflict")) {
			this.processConflict(packet, mucOptions);
		} else if (error.hasChild("not-authorized")) {
			mucOptions.setError(MucOptions.ERROR_PASSWORD_REQUIRED);
		} else if (error.hasChild("forbidden")) {
			mucOptions.setError(MucOptions.ERROR_BANNED);
		} else if (error.hasChild("registration-required")) {
			mucOptions.setError(MucOptions.ERROR_MEMBERS_ONLY);
		}
	}

	private void processConflict(PresencePacket packet, MucOptions mucOptions) {
		if (mucOptions.online()) {
			this.processRenameFailure(mucOptions);
		} else {
			mucOptions.setError(MucOptions.ERROR_NICK_IN_USE);
		}
	}

	private void processRenameFailure(MucOptions mucOptions) {
		if (mucOptions.onRenameListener != null) {
			mucOptions.onRenameListener.onFailure();
		}
	}

	private static List<String> getStatusCodes(Element x) {
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

	public void parseContactPresence(final PresencePacket packet, final Account account) {
		final PresenceGenerator mPresenceGenerator = mXmppConnectionService.getPresenceGenerator();
		final Jid from = packet.getFrom();
		if (from == null) {
			return;
		}
		final String type = packet.getAttribute("type");
		final Contact contact = account.getRoster().getContact(from);
		if (type == null) {
			String presence = from.isBareJid() ? "" : from.getResourcepart();
			contact.setPresenceName(packet.findChildContent("nick", "http://jabber.org/protocol/nick"));
			Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
			if (avatar != null && !contact.isSelf()) {
				avatar.owner = from.toBareJid();
				if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
					if (contact.setAvatar(avatar)) {
						mXmppConnectionService.getAvatarService().clear(contact);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateRosterUi();
					}
				} else {
					mXmppConnectionService.fetchAvatar(account, avatar);
				}
			}
			int sizeBefore = contact.getPresences().size();
			contact.updatePresence(presence, new Presence(packet.findChild("show")));
			PgpEngine pgp = mXmppConnectionService.getPgpEngine();
			Element x = packet.findChild("x", "jabber:x:signed");
			if (pgp != null && x != null) {
				Element status = packet.findChild("status");
				String msg = status != null ? status.getContent() : "";
				contact.setPgpKeyId(pgp.fetchKeyId(account, msg, x.getContent()));
			}
			boolean online = sizeBefore < contact.getPresences().size();
			updateLastseen(packet, account, false);
			mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, online);
		} else if ("unavailable".equals(type)) {
			if (from.isBareJid()) {
				contact.clearPresences();
			} else {
				contact.removePresence(from.getResourcepart());
			}
			mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, false);
		} else if ("subscribe".equals(type)) {
			if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
				mXmppConnectionService.sendPresencePacket(account,
						mPresenceGenerator.sendPresenceUpdatesTo(contact));
			} else {
				contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
				final Conversation conversation = mXmppConnectionService.findOrCreateConversation(
						account, contact.getJid().toBareJid(), false);
				final String statusMessage = packet.findChildContent("status");
				if (statusMessage != null
						&& !statusMessage.isEmpty()
						&& conversation.countMessages() == 0) {
					conversation.add(new Message(
							conversation,
							statusMessage,
							Message.ENCRYPTION_NONE,
							Message.STATUS_RECEIVED
					));
				}
			}
		}
		mXmppConnectionService.updateRosterUi();
	}

	@Override
	public void onPresencePacketReceived(Account account, PresencePacket packet) {
		if (packet.getFrom() != null && "http://jabber.org/protocol/muc#user".equals(packet.findChild("x").getAttribute("xmlns"))) {
			this.parseConferencePresence(packet, account);
		} else {
			this.parseContactPresence(packet, account);
		}
	}

	private void parseConferencePresence(PresencePacket packet, Account account) {
		this.processConferencePresence(packet, account);
	}
}