package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.Selection;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.openintents.openpgp.OpenPgpError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.OnPresenceSelected;
import eu.siacs.conversations.xmpp.OnRenameListener;
import eu.siacs.conversations.xmpp.jingle.OnJingleCompleted;
import eu.siacs.conversations.xmpp.jingle.JingleSession;
import eu.siacs.conversations.xmpp.jingle.OnJingleAcknowledge;
import eu.siacs.conversations.xmpp.jingle.JingleCandidate;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationFragment extends Fragment {

	private ListView messagesView;
	private EditText chatMsg;
	private Button sendButton;
	private LinearLayout pgpInfo;
	private LinearLayout mucError;
	private TextView mucErrorText;
	private Conversation conversation;
	private List<Message> messageList = new ArrayList<>();
	private ArrayAdapter<Message, View, ViewHolder> messageListAdapter;
	private IntentSender askForPassphraseIntent = null;
	private BitmapCache mBitmapCache;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mBitmapCache = new BitmapCache();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		View view = inflater.inflate(R.layout.fragment_conversation, container, false);

		pgpInfo = (LinearLayout) view.findViewById(R.id.pgp_info);
		mucError = (LinearLayout) view.findViewById(R.id.muc_error);
		mucErrorText = (TextView) view.findViewById(R.id.muc_error_text);

		this.messagesView = (ListView) view.findViewById(R.id.messages_view);
		this.chatMsg = (EditText) view.findViewById(R.id.textinput);
		this.sendButton = (Button) view.findViewById(R.id.button_send);

		messageListAdapter = new ArrayAdapter<Message, View, ViewHolder>(getActivity(),
				R.layout.message_row, messageList,
				new ArrayAdapter.OnCreateViewHolder() {

					@Override
					public ViewHolder onCreateViewHolder(View v) {
						return new ViewHolder(v);
					}

				}, new ArrayAdapter.OnBindViewHolder<Message>() {

			@Override
			public void onBindViewHolder(ViewHolder holder, Message object) {
				holder.time.setText(UIHelper.readableTimeDifference(getActivity(), object.getTimeSent()));
				if (object.getType() == Message.TYPE_FILE_TRANSFER) {
					String text;
					if (object.getStatus() == Message.STATUS_WAITING) {
						text = getString(R.string.waiting_for_file_transfer);
					} else if (object.getStatus() == Message.STATUS_UNSEND) {
						text = getString(R.string.file_not_send);
					} else {
						text = getString(object.isFileReceived() ? R.string.received_file : R.string.sent_file);
					}
					holder.messageBody.setText(text);
				} else {
					holder.messageBody.setText(object.getBody());
				}

				switch (object.getStatus()) {
				case Message.STATUS_RECEIVED:
				case Message.STATUS_WAITING:
					holder.indicator.setImageResource(R.drawable.ic_done_white_24dp);
					break;
				case Message.STATUS_SENT:
					holder.indicator.setImageResource(R.drawable.ic_done_all_white_24dp);
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_error_white_24dp);
					break;
				}

				if (object.getEncryption() == Message.ENCRYPTION_OTR) {
					holder.indicator.setImageLevel(1);
				} else if (object.getEncryption() == Message.ENCRYPTION_PGP) {
					holder.indicator.setImageLevel(2);
				} else {
					holder.indicator.setImageLevel(0);
				}

			}
		});

		this.messagesView.setAdapter(messageListAdapter);

		this.sendButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				sendMessage(chatMsg.getText().toString());
			}
		});
		
		return view;
	}

	private void sendMessage(String body) {
		if (body == null || body.trim().isEmpty()) {
			Toast.makeText(getActivity(), getString(R.string.empty_message), Toast.LENGTH_SHORT).show();
			return;
		}
		Message message = new Message(conversation, body, conversation.getNextMessageId());
		switch (conversation.getNextEncryption()) {
		case Message.ENCRYPTION_OTR:
			sendOtrMessage(message);
			break;
		case Message.ENCRYPTION_PGP:
			sendPgpMessage(message);
			break;
		default:
			sendPlainTextMessage(message);
			break;
		}
	}

	public void askForPassphrase() {
        try {
            // Potential vulnerability: Ensure that the PendingIntent is properly secured
            // to prevent unauthorized access or interception of passphrase input.
            getActivity().startIntentSenderForResult(askForPassphraseIntent, ConversationActivity.REQUEST_SEND_MESSAGE, new Intent(), 0, 0, 0);
        } catch (Exception e) {
            Log.e("ConversationFragment", "Error starting PendingIntent for passphrase request", e);
        }
    }

	private static class ViewHolder {

		protected Button download_button;
		protected ImageView image;
		protected ImageView indicator;
		protected TextView time;
		protected TextView messageBody;
		protected ImageView contact_picture;

		public ViewHolder(View v) {
			this.download_button = (Button) v.findViewById(R.id.download_button);
			this.image = (ImageView) v.findViewById(R.id.message_image);
			this.indicator = (ImageView) v.findViewById(R.id.encryption_indicator);
			this.time = (TextView) v.findViewById(R.id.message_time);
			this.messageBody = (TextView) v.findViewById(R.id.message_body);
			this.contact_picture = (ImageView) v.findViewById(R.id.contact_picture);
		}
	}

	private class BitmapCache {
		private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();
		private Bitmap error = null;

		public Bitmap get(String name, Contact contact, Context context) {
			if (bitmaps.containsKey(name)) {
				return bitmaps.get(name);
			} else {
				Bitmap bm;
				if (contact != null) {
					bm = UIHelper
							.getContactPicture(contact, 48, context, false);
				} else {
					bm = UIHelper.getContactPicture(name, 48, context, false);
				}
				bitmaps.put(name, bm);
				return bm;
			}
		}
	}

	public void setText(String text) {
		this.chatMsg.append(text);
	}

	private void decryptMessage(final Message message) {
		PgpEngine engine = activity.xmppConnectionService.getPgpEngine();
		if (engine != null) {
			engine.decrypt(message, new OnPgpEngineResult() {

				@Override
				public void userInputRequried(PendingIntent pi) {
					askForPassphraseIntent = pi.getIntentSender();
					pgpInfo.setVisibility(View.VISIBLE);
				}

				@Override
				public void success() {
					activity.xmppConnectionService.databaseBackend.updateMessage(message);
					updateMessages();
				}

				@Override
				public void error(OpenPgpError openPgpError) {
					Log.d("xmppService", "decryption error" + openPgpError.getMessage());
					message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
				}
			});
		} else {
			pgpInfo.setVisibility(View.VISIBLE);
		}
	}

	private void sendPlainTextMessage(Message message) {
		ConversationsActivity activity = (ConversationsActivity) getActivity();
		activity.xmppConnectionService.sendMessage(message, null);
		chatMsg.setText("");
	}

	private void sendPgpMessage(final Message message) {
		final ConversationsActivity activity = (ConversationsActivity) getActivity();
		if (conversation.getContact() != null && conversation.getContact().getPgpKeyId() != 0) {
			PgpEngine engine = activity.xmppConnectionService.getPgpEngine();
			if (engine != null) {
				engine.encrypt(message, new OnPgpEngineResult() {

					@Override
					public void userInputRequried(PendingIntent pi) {
						askForPassphraseIntent = pi.getIntentSender();
						pgpInfo.setVisibility(View.VISIBLE);
					}

					@Override
					public void success() {
						activity.xmppConnectionService.sendMessage(message, null);
						chatMsg.setText("");
					}

					@Override
					public void error(OpenPgpError openPgpError) {
						Log.d("xmppService", "encryption error" + openPgpError.getMessage());
						pgpInfo.setVisibility(View.VISIBLE);
					}
				});
			} else {
				pgpInfo.setVisibility(View.VISIBLE);
			}
		} else {
			Toast.makeText(getActivity(), getString(R.string.pgp_key_not_found), Toast.LENGTH_SHORT).show();
		}
	}

	private void sendOtrMessage(Message message) {
		ConversationsActivity activity = (ConversationsActivity) getActivity();
		if (conversation.getAxolotlSession() != null && conversation.hasValidOtrSession()) {
			activity.xmppConnectionService.sendMessage(message, conversation.getAxlolotlSession().getFingerprint());
		} else {
			Toast.makeText(getActivity(), getString(R.string.otr_not_available), Toast.LENGTH_SHORT).show();
		}
	}

	private void updateMessages() {
		messageListAdapter.notifyDataSetChanged();
		messagesView.setSelection(messagesView.getCount() - 1);
	}

	public void updateConversation(Conversation conversation) {
		this.conversation = conversation;
		updateMessages();
	}
	
	public void onNewMessage(Message message) {
		if (conversation == null || !conversation.getUuid().equals(message.getConversationUuid())) {
			return;
		}
		messageList.add(message);
		updateMessages();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == ConversationActivity.REQUEST_SEND_MESSAGE && askForPassphraseIntent != null) {
			askForPassphrase();
		}
	}
}