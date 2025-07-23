package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.java.otr4j.session.SessionStatus;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine.OpenPgpException;
import eu.siacs.conversations.crypto.PgpEngine.UserInputRequiredException;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ConversationFragment extends Fragment {

	protected Conversation conversation;
	protected ListView messagesView;
	protected LayoutInflater inflater;
	protected List<Message> messageList = new ArrayList<Message>();
	protected ArrayAdapter<Message> messageListAdapter;
	protected Contact contact;
	protected BitmapCache mBitmapCache = new BitmapCache();

	protected String queuedPqpMessage = null;

	private EditText chatMsg;
	private String pastedText = null;

	protected Bitmap selfBitmap;
	
	private boolean useSubject = true;

	private IntentSender askForPassphraseIntent = null;

	private OnClickListener sendMsgListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (chatMsg.getText().length() < 1)
				return;
			Message message = new Message(conversation, chatMsg.getText()
					.toString(), conversation.nextMessageEncryption);
			if (conversation.nextMessageEncryption == Message.ENCRYPTION_OTR) {
				sendOtrMessage(message);
			} else if (conversation.nextMessageEncryption == Message.ENCRYPTION_PGP) {
				sendPgpMessage(message);
			} else {
				sendPlainTextMessage(message);
			}
		}
	};
	protected OnClickListener clickToDecryptListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (askForPassphraseIntent != null) {
				try {
					getActivity().startIntentSenderForResult(
							askForPassphraseIntent,
							ConversationActivity.REQUEST_DECRYPT_PGP, null, 0,
							0, 0);
				} catch (SendIntentException e) {
					Log.d("xmppService", "couldnt fire intent");
				}
			}
		}
	};

	private LinearLayout pgpInfo;
	private LinearLayout mucError;
	private TextView mucErrorText;
	private OnClickListener clickToMuc = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Intent intent = new Intent(getActivity(), MucDetailsActivity.class);
			intent.setAction(MucDetailsActivity.ACTION_VIEW_MUC);
			intent.putExtra("uuid", conversation.getUuid());
			startActivity(intent);
		}
	};
	private ConversationActivity activity;

	public void hidePgpPassphraseBox() {
		pgpInfo.setVisibility(View.GONE);
	}

	public void updateChatMsgHint() {
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			chatMsg.setHint("Send message to conference");
		} else {
			switch (conversation.nextMessageEncryption) {
			case Message.ENCRYPTION_NONE:
				chatMsg.setHint("Send plain text message");
				break;
			case Message.ENCRYPTION_OTR:
				chatMsg.setHint("Send OTR encrypted message");
				break;
			case Message.ENCRYPTION_PGP:
				chatMsg.setHint("Send openPGP encryted messeage");
				break;
			case Message.ENCRYPTION_DECRYPTED:
				chatMsg.setHint("Send openPGP encryted messeage");
				break;
			default:
				break;
			}
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {

		final DisplayMetrics metrics = getResources().getDisplayMetrics();
		
		this.inflater = inflater;

		final View view = inflater.inflate(R.layout.fragment_conversation,
				container, false);
		chatMsg = (EditText) view.findViewById(R.id.textinput);
		
		if (pastedText!=null) {
			chatMsg.setText(pastedText);
		}
		
		ImageButton sendButton = (ImageButton) view
				.findViewById(R.id.textSendButton);
		sendButton.setOnClickListener(this.sendMsgListener);

		pgpInfo = (LinearLayout) view.findViewById(R.id.pgp_keyentry);
		pgpInfo.setOnClickListener(clickToDecryptListener);
		mucError = (LinearLayout) view.findViewById(R.id.muc_error);
		mucError.setOnClickListener(clickToMuc);
		mucErrorText = (TextView) view.findViewById(R.id.muc_error_msg);

		messagesView = (ListView) view.findViewById(R.id.messages_view);

		messageListAdapter = new ArrayAdapter<Message>(this.getActivity()
				.getApplicationContext(), R.layout.message_sent,
				this.messageList) {

			private static final int SENT = 0;
			private static final int RECIEVED = 1;

			@Override
			public int getViewTypeCount() {
				return 2;
			}

			@Override
			public int getItemViewType(int position) {
				if (getItem(position).getStatus() <= Message.STATUS_RECIEVED) {
					return RECIEVED;
				} else {
					return SENT;
				}
			}

			@Override
			public View getView(int position, View view, ViewGroup parent) {
				Message item = getItem(position);
				int type = getItemViewType(position);
				ViewHolder viewHolder;
				if (view == null) {
					viewHolder = new ViewHolder();
					switch (type) {
					case SENT:
						view = (View) inflater.inflate(R.layout.message_sent,
								null);
						viewHolder.imageView = (ImageView) view
								.findViewById(R.id.message_photo);
						viewHolder.imageView.setImageBitmap(selfBitmap);
						viewHolder.indicator = (ImageView) view.findViewById(R.id.security_indicator);
						viewHolder.image = (ImageView) view.findViewById(R.id.message_image);
						break;
					case RECIEVED:
						view = (View) inflater.inflate(
								R.layout.message_recieved, null);
						viewHolder.imageView = (ImageView) view
								.findViewById(R.id.message_photo);
						viewHolder.indicator = (ImageView) view.findViewById(R.id.security_indicator);
						if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

							viewHolder.imageView.setImageBitmap(mBitmapCache
									.get(item.getConversation().getName(useSubject), item
											.getConversation().getContact(),
											getActivity()
													.getApplicationContext()));

						}
						break;
					default:
						viewHolder = null;
						break;
					}
					viewHolder.messageBody = (TextView) view
							.findViewById(R.id.message_body);
					viewHolder.time = (TextView) view
							.findViewById(R.id.message_time);
					view.setTag(viewHolder);
				} else {
					viewHolder = (ViewHolder) view.getTag();
				}
				if (type == RECIEVED) {
					if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
						if (item.getCounterpart() != null) {
							viewHolder.imageView.setImageBitmap(mBitmapCache
									.get(item.getCounterpart(), null,
											getActivity()
													.getApplicationContext()));
						} else {
							viewHolder.imageView.setImageBitmap(mBitmapCache
									.get(item.getConversation().getName(useSubject),
											null, getActivity()
													.getApplicationContext()));
						}
					}
				}
				if (item.getType() == Message.TYPE_IMAGE) {
					viewHolder.image.setVisibility(View.VISIBLE);
					viewHolder.image.setImageBitmap(activity.xmppConnectionService.getFileBackend().getThumbnailFromMessage(item,(int) (metrics.density * 288)));
					viewHolder.messageBody.setVisibility(View.GONE);
				} else {
					if (viewHolder.image != null) viewHolder.image.setVisibility(View.GONE);
					viewHolder.messageBody.setVisibility(View.VISIBLE);
					String body = item.getBody();
					if (body != null) {
						if (item.getEncryption() == Message.ENCRYPTION_PGP) {
							viewHolder.messageBody
									.setText(getString(R.string.encrypted_message));
							viewHolder.messageBody.setTextColor(0xff33B5E5);
							viewHolder.messageBody.setTypeface(null,
									Typeface.ITALIC);
							viewHolder.indicator.setVisibility(View.VISIBLE);
						} else if ((item.getEncryption() == Message.ENCRYPTION_OTR)||(item.getEncryption() == Message.ENCRYPTION_DECRYPTED)) {
							viewHolder.messageBody.setText(body.trim());
							viewHolder.messageBody.setTextColor(0xff333333);
							viewHolder.messageBody.setTypeface(null,
									Typeface.NORMAL);
							viewHolder.indicator.setVisibility(View.VISIBLE);
						} else {
							viewHolder.messageBody.setText(body.trim());
							viewHolder.messageBody.setTextColor(0xff333333);
							viewHolder.messageBody.setTypeface(null,
									Typeface.NORMAL);
							viewHolder.indicator.setVisibility(View.GONE);
						}
					} else {
						viewHolder.indicator.setVisibility(View.GONE);
					}
				}
				switch (item.getStatus()) {
				case Message.STATUS_UNSEND:
					viewHolder.time.setTypeface(null, Typeface.ITALIC);
					viewHolder.time.setTextColor(0xFF8e8e8e);
					viewHolder.time.setText("sending\u2026");
					break;
				case Message.STATUS_SEND_FAILED:
					viewHolder.time.setText(getString(R.string.send_failed) + " \u00B7 " + UIHelper.readableTimeDifference(item
							.getTimeSent()));
					viewHolder.time.setTextColor(0xFFe92727);
					viewHolder.time.setTypeface(null,Typeface.NORMAL);
					break;
				case Message.STATUS_SEND_REJECTED:
					viewHolder.time.setText(getString(R.string.send_rejected));
					viewHolder.time.setTextColor(0xFFe92727);
					viewHolder.time.setTypeface(null,Typeface.NORMAL);
					break;
				default:
					viewHolder.time.setTypeface(null, Typeface.NORMAL);
					viewHolder.time.setTextColor(0xFF8e8e8e);
					if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
						viewHolder.time.setText(UIHelper
								.readableTimeDifference(item.getTimeSent()));
					} else {
						viewHolder.time.setText(item.getCounterpart()
								+ " \u00B7 "
								+ UIHelper.readableTimeDifference(item
										.getTimeSent()));
					}
					break;
				}
				return view;
			}
		};
		messagesView.setAdapter(messageListAdapter);

		return view;
	}

	protected Bitmap findSelfPicture() {
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(getActivity()
						.getApplicationContext());
		boolean showPhoneSelfContactPicture = sharedPref.getBoolean(
				"show_phone_selfcontact_picture", true);

		return UIHelper.getSelfContactPicture(conversation.getAccount(), 200,
				showPhoneSelfContactPicture, getActivity());
	}

	@Override
	public void onStart() {
		super.onStart();
		this.activity = (ConversationActivity) getActivity();
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		this.useSubject = preferences.getBoolean("use_subject_in_muc", true);
		if (activity.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
	}

	public void onBackendConnected() {
		this.conversation = activity.getSelectedConversation();
		if (this.conversation == null) {
			return;
		}
		this.selfBitmap = findSelfPicture();
		updateMessages();
		// rendering complete. now go tell activity to close pane
		if (activity.getSlidingPaneLayout().isSlideable()) {
			if (!activity.shouldPaneBeOpen()) {
				activity.getSlidingPaneLayout().closePane();
				activity.getActionBar().setDisplayHomeAsUpEnabled(true);
				activity.getActionBar().setTitle(conversation.getName(useSubject));
				activity.invalidateOptionsMenu();

			}
		}
		if (queuedPqpMessage != null) {
			this.conversation.nextMessageEncryption = Message.ENCRYPTION_PGP;
			Message message = new Message(conversation, queuedPqpMessage,
					Message.ENCRYPTION_PGP);
			sendPgpMessage(message);
		}
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			activity.xmppConnectionService
					.setOnRenameListener(new OnRenameListener() {

						@Override
						public void onRename(final boolean success) {
							activity.xmppConnectionService.updateConversation(conversation);
							getActivity().runOnUiThread(new Runnable() {

								@Override
								public void run() {
									if (success) {
										Toast.makeText(
												getActivity(),
												"Your nickname has been changed",
												Toast.LENGTH_SHORT).show();
									} else {
										Toast.makeText(getActivity(),
												"Nichname is already in use",
												Toast.LENGTH_SHORT).show();
									}
								}
							});
						}
					});
		}
	}

	public void updateMessages() {
		ConversationActivity activity = (ConversationActivity) getActivity();
		if (this.conversation != null) {
			List<Message> encryptedMessages = new LinkedList<Message>();
			for (Message message : this.conversation.getMessages()) {
				if (message.getEncryption() == Message.ENCRYPTION_PGP) {
					encryptedMessages.add(message);
				}
			}
			if (encryptedMessages.size() > 0) {
				DecryptMessage task = new DecryptMessage();
				Message[] msgs = new Message[encryptedMessages.size()];
				task.execute(encryptedMessages.toArray(msgs));
			}
			this.messageList.clear();
			this.messageList.addAll(this.conversation.getMessages());
			this.messageListAdapter.notifyDataSetChanged();
			if (conversation.getMode() == Conversation.MODE_SINGLE) {
				if (messageList.size() >= 1) {
					int latestEncryption = this.conversation.getLatestMessage()
							.getEncryption();
					if (latestEncryption == Message.ENCRYPTION_DECRYPTED) {
						conversation.nextMessageEncryption = Message.ENCRYPTION_PGP;
					} else {
						conversation.nextMessageEncryption = latestEncryption;
					}
					makeFingerprintWarning(latestEncryption);
				}
			} else {
				if (conversation.getMucOptions().getError() != 0) {
					mucError.setVisibility(View.VISIBLE);
					if (conversation.getMucOptions().getError() == MucOptions.ERROR_NICK_IN_USE) {
						mucErrorText.setText(getString(R.string.nick_in_use));
					}
				} else {
					mucError.setVisibility(View.GONE);
				}
			}
			getActivity().invalidateOptionsMenu();
			updateChatMsgHint();
			int size = this.messageList.size();
			if (size >= 1)
				messagesView.setSelection(size - 1);
			if (!activity.shouldPaneBeOpen()) {
				conversation.markRead();
				// TODO update notifications
				UIHelper.updateNotification(getActivity(),
						activity.getConversationList(), null, false);
				activity.updateConversationList();
			}
		}
	}

	protected void makeFingerprintWarning(int latestEncryption) {
		final LinearLayout fingerprintWarning = (LinearLayout) getView()
				.findViewById(R.id.new_fingerprint);
		if (conversation.getContact() != null) {
			Set<String> knownFingerprints = conversation.getContact()
					.getOtrFingerprints();
			if ((latestEncryption == Message.ENCRYPTION_OTR)
					&& (conversation.hasValidOtrSession()
							&& (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) && (!knownFingerprints
								.contains(conversation.getOtrFingerprint())))) {
				fingerprintWarning.setVisibility(View.VISIBLE);
				TextView fingerprint = (TextView) getView().findViewById(
						R.id.otr_fingerprint);
				fingerprint.setText(conversation.getOtrFingerprint());
				fingerprintWarning.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						AlertDialog dialog = UIHelper
								.getVerifyFingerprintDialog(
										(ConversationActivity) getActivity(),
										conversation, fingerprintWarning);
						dialog.show();
					}
				});
			} else {
				fingerprintWarning.setVisibility(View.GONE);
			}
		} else {
			fingerprintWarning.setVisibility(View.GONE);
		}
	}

	protected void sendPlainTextMessage(Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		activity.xmppConnectionService.sendMessage(message, null);
		chatMsg.setText("");
	}

	protected void sendPgpMessage(final Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		Contact contact = message.getConversation().getContact();
		Account account = message.getConversation().getAccount();
		if (activity.hasPgp()) {
			if (contact.getPgpKeyId() != 0) {
				try {
					message.setEncryptedBody(xmppService.getPgpEngine().encrypt(account, contact.getPgpKeyId(), message.getBody()));
					xmppService.sendMessage(message, null);
					chatMsg.setText("");
				} catch (UserInputRequiredException e) {
					try {
						getActivity().startIntentSenderForResult(e.getPendingIntent().getIntentSender(),
								ConversationActivity.REQUEST_SEND_MESSAGE, null, 0,
								0, 0);
					} catch (SendIntentException e1) {
						Log.d("xmppService","failed to start intent to send message");
					}
				} catch (OpenPgpException e) {
					Log.d("xmppService","error encrypting with pgp: "+e.getOpenPgpError().getMessage());
				}
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						getActivity());
				builder.setTitle("No openPGP key found");
				builder.setIconAttribute(android.R.attr.alertDialogIcon);
				builder.setMessage("There is no openPGP key assoziated with this contact");
				builder.setNegativeButton("Cancel", null);
				builder.setPositiveButton("Send plain text",
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
								message.setEncryption(Message.ENCRYPTION_NONE);
								xmppService.sendMessage(message, null);
								chatMsg.setText("");
							}
						});
				builder.create().show();
			}
		}
	}

	protected void sendOtrMessage(final Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		if (conversation.hasValidOtrSession()) {
			activity.xmppConnectionService.sendMessage(message, null);
			chatMsg.setText("");
		} else {
			Hashtable<String, Integer> presences;
			if (conversation.getContact() != null) {
				presences = conversation.getContact().getPresences();
			} else {
				presences = null;
			}
			if ((presences == null) || (presences.size() == 0)) {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						getActivity());
				builder.setTitle("Contact is offline");
				builder.setIconAttribute(android.R.attr.alertDialogIcon);
				builder.setMessage("Sending OTR encrypted messages to an offline contact is impossible.");
				builder.setPositiveButton("Send plain text",
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
								message.setEncryption(Message.ENCRYPTION_NONE);
								xmppService.sendMessage(message, null);
								chatMsg.setText("");
							}
						});
				builder.setNegativeButton("Cancel", null);
				builder.create().show();
			} else if (presences.size() == 1) {
				xmppService.sendMessage(message, (String) presences.keySet()
						.toArray()[0]);
				chatMsg.setText("");
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						getActivity());
				builder.setTitle("Choose Presence");
				final String[] presencesArray = new String[presences.size()];
				presences.keySet().toArray(presencesArray);
				builder.setItems(presencesArray,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								xmppService.sendMessage(message,
										presencesArray[which]);
								chatMsg.setText("");
							}
						});
				builder.create().show();
			}
		}
	}

	private static class ViewHolder {

		protected ImageView image;
		protected ImageView indicator;
		protected TextView time;
		protected TextView messageBody;
		protected ImageView imageView;

	}

	private class BitmapCache {
		private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();
		private Bitmap error = null;

		public Bitmap get(String name, Contact contact, Context context) {
			if (bitmaps.containsKey(name)) {
				return bitmaps.get(name);
			} else {
				Bitmap bm = UIHelper.getContactPicture(contact, name, 200, context);
				bitmaps.put(name, bm);
				return bm;
			}
		}

		public Bitmap getError() {
			if (error == null) {
				error = UIHelper.getErrorPicture(200);
			}
			return error;
		}
	}

	class DecryptMessage extends AsyncTask<Message, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Message... params) {
			final ConversationActivity activity = (ConversationActivity) getActivity();
			askForPassphraseIntent = null;
			for (int i = 0; i < params.length; ++i) {
				if (params[i].getEncryption() == Message.ENCRYPTION_PGP) {
					String body = params[i].getBody();
					String decrypted = null;
					if (activity == null) {
						return false;
					} else if (!activity.xmppConnectionServiceBound) {
						return false;
					}
					try {
						decrypted = activity.xmppConnectionService
								.getPgpEngine().decrypt(conversation.getAccount(),body);
					} catch (UserInputRequiredException e) {
						askForPassphraseIntent = e.getPendingIntent()
								.getIntentSender();
						activity.runOnUiThread(new Runnable() {

							@Override
							public void run() {
								pgpInfo.setVisibility(View.VISIBLE);
							}
						});

						return false;

					} catch (OpenPgpException e) {
						Log.d("gultsch", "error decrypting pgp");
					}
					if (decrypted != null) {
						params[i].setBody(decrypted);
						params[i].setEncryption(Message.ENCRYPTION_DECRYPTED);
						activity.xmppConnectionService.updateMessage(params[i]);
					}
					if (activity != null) {
						activity.runOnUiThread(new Runnable() {

							@Override
							public void run() {
								messageListAdapter.notifyDataSetChanged();
							}
						});
					}
				}
				if (activity != null) {
					activity.runOnUiThread(new Runnable() {

						@Override
						public void run() {
							activity.updateConversationList();
						}
					});
				}
			}
			return true;
		}

	}

	public void setText(String text) {
		this.pastedText = text;
	}
}
