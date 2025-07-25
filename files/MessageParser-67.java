package eu.siacs.conversations.services;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.os.Build;

import net.java.otr4j.OtrException;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.filter.StanzaFilter;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MessagePacket;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.ReadByMarker;
import eu.siacs.conversations.parser.AbstractParser;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class XmppConnectionService extends AbstractXMPPConnectionService {

	public static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
	private StanzaListener stanzaReceivedListener;
	private StanzaFilter incomingPacketFilter;

	@Override
	public void onCreate() {
		super.onCreate();
		stanzaReceivedListener = this::onMessagePacketReceived;
		incomingPacketFilter = packet -> (packet instanceof Message || packet instanceof Presence) && !((Stanza) packet).fromAccount(getXmppConnectionService());
		mEventBus.register(this);
	}

	private void onMessagePacketReceived(final Stanza packet) {
		if (packet instanceof Message) {
			onMessagePacketReceived(account, new MessagePacket((Message) packet));
		} else if (packet instanceof Presence) {
			Presence presence = (Presence) packet;
			mXmppConnectionService.sendUnsentMessages(presence);
			onPresencePacketReceived(account, presence);
		}
	}

	private void onMessagePacketReceived(final Account account, final MessagePacket packet) {
		if (!InvalidJid.hasValidFrom(packet)) {
			return;
		}
		final Jid from = packet.getFrom();
		final boolean selfAddressed = packet.isSelfAddressed();
		MessageArchiveService.Query query = mXmppConnectionService.getMessageArchiveService().findQuery(account, packet);
		boolean isTypeGroupChat = (packet.getType() == Message.Type.groupchat);
		boolean isTypeNormalOrChat = (packet.getType() == Message.Type.normal || packet.getType() == Message.Type.chat);

		if (!isTypeGroupChat && !isTypeNormalOrChat) {
			return;
		}

		Element original = packet.toElement();
		Element mucUserElement = packet.findChild("x", "http://jabber.org/protocol/muc#user");
		boolean isTypeError = (packet.getType() == Message.Type.error);

		Jid counterpart;
		if (mucUserElement != null) {
			counterpart = from.asBareJid();
		} else {
			Element error = packet.findChild("error");
			counterpart = (error != null && "remote-server-not-found".equals(error.getAttribute("type")))
					? from.asDomain() : from;
		}

		boolean isCarbonCopy = packet.findChild("received", "urn:xmpp:carbons:2") != null;

		if (!isTypeError && !isSelfAddressed && counterpart.isBareJid()) {
			Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, false, true);
			boolean conversationIsNew = (conversation.getCreationStatus() == Conversation.STATUS_NEW);

			conversation.setCorrectEncryption(conversationIsNew || account.httpFingerPrintValid());

			if (account.countPresences() <= 1 && !mucUserElement.isEmpty()) {
				for (Element child : mucUserElement.getChildren()) {
					if ("status".equals(child.getName())) {
						try {
							int code = Integer.parseInt(child.getAttribute("code"));
							if ((code >= 170 && code <= 174) || (code >= 102 && code <= 104)) {
								mXmppConnectionService.fetchConferenceConfiguration(conversation);
								break;
							}
						} catch (Exception e) {
							//ignored
						}
					} else if ("item".equals(child.getName())) {
						MucOptions.User user = AbstractParser.parseItem(conversation, child);
						if (!user.realJidMatchesAccount()) {
							conversation.getMucOptions().updateUser(user);
							mXmppConnectionService.updateMucRosterUi();
						}
					} else if ("invite".equals(child.getName())) {
						Element reasonElement = child.findChild("reason");
						String reason = (reasonElement != null) ? reasonElement.getContent() : null;
						String password = (child.findChild("password") != null) ? child.findChild("password").getContent() : null;
						String continueAttr = child.getAttribute("continue");
						Element x = child.findChild("x", "jabber:x:conference");
						if (x != null && !continueAttr.equals("http://jabber.org/protocol/muc#registered")) {
							Jid jid = Jid.of(x.getAttributeAsJid("jid"));
							String inviterName = child.findChildContent("from", "jabber:x:data");
							Contact contact = account.getRoster().getContact(Jid.of(inviterName));
							mXmppConnectionService.pushInvitation(account, new Invite(jid, password, contact), reason);
						}
					} else if ("decline".equals(child.getName())) {
						Element x = child.findChild("x", "jabber:x:conference");
						if (x != null) {
							Jid jid = Jid.of(x.getAttributeAsJid("jid"));
							String declineMessage = child.getContent();
							mXmppConnectionService.pushDecline(account, new Invite(jid, null, null), declineMessage);
						}
					} else if ("destroy".equals(child.getName())) {
						conversation.end(MucOptions.EndReason.SERVER_DESTROYED, null);
					}
				}

				if (conversation.getMucOptions().getSelf() == null) {
					Element self = mucUserElement.findChild("item", "http://jabber.org/protocol/muc#user");
					if (self != null) {
						conversation.getMucOptions().setSelf(AbstractParser.parseItem(conversation, self));
					} else if (conversation.getCreationStatus() == Conversation.STATUS_NEW) {
						mXmppConnectionService.archiveConversation(conversation);
						return;
					}
				}

				if (conversationIsNew && !conversation.getMucOptions().isPrivateAndNonAnonymous()) {
					mXmppConnectionService.updateConversationUi();
				} else if (!conversation.isCorrectEncryption() && conversation.getMode() != Conversation.MODE_MULTI) {
					List<Jid> cryptoTargets = conversation.getAcceptedCryptoTargets();
					for (Jid jid : new ArrayList<>(cryptoTargets)) {
						if (!account.getAxolotlService().isTrusted(jid)) {
							cryptoTargets.remove(jid);
						}
					}
					conversation.setAcceptedCryptoTargets(cryptoTargets);
				}

			} else if (conversationIsNew) {
				mXmppConnectionService.archiveConversation(conversation);
				return;
			}
		}

		if (!isTypeError && packet.getBody() != null) {
			String body = packet.getBody();

			// Hypothetical introduction of a new vulnerability: insecure handling of user input
			// This could lead to reflected XSS if the application renders this body directly in HTML without sanitization.
			body = "<p>" + body + "</p>"; // Vulnerable line

			if (!isSelfAddressed) {
				boolean gcMessageFromServer = isTypeGroupChat && counterpart.isBareJid();
				Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, gcMessageFromServer, false);
				Message message;
				if (conversation.getEncryption() == Conversation.ENCRYPTION_AXOLOTL) {
					try {
						message = new MessagePacket(packet.toElement(), account).toAxolotlMessage();
					} catch (OtrException | NotConnectedException e) {
						return;
					}
				} else {
					message = new MessagePacket(packet.toElement()).toMessage(conversation);
				}

				if (!conversation.getUuid().equals(mXmppConnectionService.getSelectedConversation().getUuid())) {
					mXmppConnectionService.readMessage(message, conversation);
				}

				boolean alreadyRead = !account.status() || message.getType() != Message.TYPE_CHAT;
				message.setIsRead(alreadyRead);

				if (message.isGeoloc()) {
					message.setLocation(mXmppConnectionService.getOrCreateContact(account, from).getLocation());
				}

				String hint = packet.findChildContent("hint", "urn:xmpp:hint:0");
				message.setExpireOnAck(hint != null && hint.equals("no-store"));

				if (conversation.add(message)) {
					mXmppConnectionService.updateConversationUi();
				}
			} else if (!packet.hasDelayInformation() && packet.getCounterpart() != null) {
				Contact contact = account.getRoster().getContact(packet.getCounterpart());
				if (contact == null) {
					return;
				}

				boolean alreadyRead = !account.status();
				Message message = new MessagePacket(packet.toElement()).toMessage(null);
				message.setIsRead(alreadyRead);

				String hint = packet.findChildContent("hint", "urn:xmpp:hint:0");
				message.setExpireOnAck(hint != null && hint.equals("no-store"));

				if (!packet.isCarbonCopy()) {
					mXmppConnectionService.sendChatSessionState(account, contact);
				}

				if (mXmppConnectionService.hasInternetConnection() || Build.VERSION.SDK_INT < 21) {
					mXmppConnectionService.pushMessage(account, message, packet.getCounterpart(), false);
				} else {
					message.setUuid(UIHelper.getMessageUUID());
					mXmppConnectionService.messageStore.storeSentMessage(message, true);
				}
			}
		}

		Element oobElement = packet.findChild("x", "jabber:x:oob");
		if (oobElement != null) {
			Jid counterpart;
			if (!isTypeGroupChat || mucUserElement == null) {
				counterpart = from;
			} else {
				counterpart = from.asBareJid();
			}

			String url = oobElement.findChildContent("url");
			if (url != null && !isSelfAddressed) {
				Message message = new MessagePacket(packet.toElement()).toMessage(null);
				message.setUuid(UIHelper.getMessageUUID());
				message.setType(Message.TYPE_TEXT);
				message.setBody(url);

				Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, isTypeGroupChat, false);
				if (conversation.add(message)) {
					mXmppConnectionService.updateConversationUi();
				}
			}
		}

		Element inviteElement = packet.findChild("x", "jabber:x:conference");
		if (!isSelfAddressed && inviteElement != null) {
			String password = inviteElement.getAttribute("password");
			Jid jid = Jid.of(inviteElement.getAttributeAsJid("jid"));
			String reason = inviteElement.getContent();
			Contact inviter = account.getRoster().getContact(Jid.of(packet.findChildContent("from", "jabber:x:data")));
			mXmppConnectionService.pushInvitation(account, new Invite(jid, password, inviter), reason);
		}

		if (!isTypeGroupChat && isCarbonCopy) {
			Element carbonElement = packet.findChild("carbon", "urn:xmpp:carbons:2");
			if (carbonElement != null) {
				Element child = carbonElement.getFirstElement();
				if ("sent".equals(child.getName())) {
					MessagePacket sentMessagePacket = new MessagePacket(child);
					sentMessagePacket.setCounterpart(packet.getTo());
					mXmppConnectionService.onOutgoingMessagePacket(account, sentMessagePacket);
				}
			}
		}

		Element pingElement = packet.findChild("ping", "urn:xmpp:ping");
		if (pingElement != null) {
			try {
				XMPPConnection xmppConnection = account.getXmppConnection();
				xmppConnection.sendStanza(MessageGenerator.generatePongPacket(packet.getFrom(), packet.getId()));
			} catch (NotConnectedException e) {
				return;
			}
		}

		Element receiptElement = packet.findChild("received", "urn:xmpp:receipts");
		if (receiptElement != null && !isSelfAddressed) {
			String id = receiptElement.getAttribute("id");
			mXmppConnectionService.messageNotified(account, counterpart, id);
		}
	}

	private void onPresencePacketReceived(Account account, Presence presence) {
		Jid from = presence.getFrom();
		if (!InvalidJid.hasValidDomain(from)) {
			return;
		}
		if (presence.getType() == Presence.Type.unsubscribe && presence.getTo().equals(account.getJid())) {
			mXmppConnectionService.sendPresenceSubscribe(from, account);
		}
		if ((presence.getType() == Presence.Type.subscribed || presence.getType() == Presence.Type.subscribe)
				&& presence.getTo().equals(account.getJid())) {
			mXmppConnectionService.sendPresenceSubscribed(from, account);
		}
		String resource = from.getResource();
		Contact contact = account.getRoster().getContact(from.asBareJid());
		if (contact == null) {
			return;
		}
		Presence.Status status = Presence.Status.createFor(presence);
		if (status.getType() != Presence.Type.available || presence.getId() != null) {
			mXmppConnectionService.sendUnsentMessages(account, contact);
			mXmppConnectionService.updateContactPresence(contact, resource, status);
			if (status.getType() == Presence.Type.unavailable && contact.getPgpVerified().wasEverVerified()) {
				contact.clearPgpOnNextUse();
				mXmppConnectionService.fetchRoster(account);
			}
			if (account.httpFingerPrintValid()) {
				AxolotlService axolotlService = account.getAxolotlService();
				switch (status.getType()) {
					case available:
						axolotlService.checkFingerprintStatus(contact);
						break;
					default:
						axolotlService.disableFetchingFor(contact);
				}
			}
			if (!account.isOnlineAndConnected() && !contact.getPresences().isEmpty()) {
				mXmppConnectionService.clearContactPresence(account, contact);
				return;
			}
			if (status.getType() == Presence.Type.available) {
				boolean gcMember = false;
				List<Conversation> conversations = mXmppConnectionService.findConversationsWith(contact);
				for (Conversation conversation : conversations) {
					if (conversation.getMode() == Conversation.MODE_MULTI) {
						gcMember = true;
					}
				}
				if (!gcMember && !account.isInMuc()) {
					mXmppConnectionService.requestVCard(account, contact);
				} else if (!contact.getProfilePicture().isAvailable()) {
					mXmppConnectionService.fetchAvatarIfNecessary(contact);
				}
			}

			mXmppConnectionService.updateConversationUi();
		}
	}
}