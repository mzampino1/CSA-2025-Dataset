package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Iterator;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.xmpp.axolotl.XmppAxolotlSession;

// Main activity for handling conversations in the app
public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnAccountUpdate,
		XmppConnectionService.OnRosterUpdate, XmppConnectionService.OnUpdateBlocklist {

	private ArrayList<Conversation> conversationList = new ArrayList<>();
	private ConversationAdapter listAdapter;
	private ConversationFragment mConversationFragment;
	private Conversation swipedConversation = null;

	private Toast prepareFileToast = null;
	private String tag;

	private boolean conversationWasSelectedByKeyboard;

	public static final int ATTACHMENT_CHOICE_INVALID = -1;
	public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0;
	public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 1;
	public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 2;
	public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 3;
	public static final int ATTACHMENT_CHOICE_LOCATION = 4;

	private boolean forbidProcessingPendings = false; // Flag to prevent processing of pending actions

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_conversation);

		listAdapter = new ConversationAdapter(this, conversationList);

		mConversationFragment = (ConversationFragment) getSupportFragmentManager().findFragmentById(R.id.conversation_fragment_container);
		if (mConversationFragment != null) {
			mConversationFragment.setActivity(ConversationActivity.this);
		}

		tag = getIntent().getStringExtra("tag");
		conversationWasSelectedByKeyboard = false;

		xmppConnectionService.populateWithOrderedConversations(conversationList);
		listAdapter.notifyDataSetChanged();
	}

	@Override
	public void onStart() {
		super.onStart();
		this.conversationWasSelectedByKeyboard = false;
		if (xmppConnectionService != null) {
			mConversationFragment.onBackendConnected();
		}
	}

	// Lifecycle method for handling user interaction during pause state
	@Override
	protected void onPause() {
		super.onPause();
		sendReadMarkerIfNecessary();
	}

	private void sendReadMarkerIfNecessary() {
		if (mConversationFragment == null) {
			return;
		}
		Conversation conversation = mConversationFragment.getConversation();
		if (conversation != null && !conversation.isRead()) {
			xmppConnectionService.sendReadMarker(conversation);
		}
	}

	// Lifecycle method for handling user interaction during stop state
	@Override
	protected void onStop() {
		super.onStop();
		mConversationFragment.conferenceLeft();
	}

	public void updateActionBarTitle() {
		if (mConversationFragment != null) {
			setTitle(mConversationFragment.getConversation().getName());
		}
	}

	// Handling selection of a conversation by the user
	private void selectConversation(Conversation conversation) {
		this.swipedConversation = null;
		Intent intent = new Intent(this, ConversationActivity.class);
		intent.setAction(Intent.ACTION_VIEW);
		if (conversation != null) {
			intent.putExtra("uuid", conversation.getUuid());
		}
		startActivity(intent);
	}

	// Handler for the back button press
	@Override
	public void onBackPressed() {
		if (isConversationsOverviewVisible()) {
			finish();
		} else {
			this.showConversationsOverview();
		}
	}

	private boolean isConversationsOverviewVisible() {
		return mConversationFragment == null || !mConversationFragment.isConversationShown();
	}

	private void showConversationsOverview() {
		if (this.mConversationFragment != null) {
			this.mConversationFragment.hideConversation();
		}
	}

	public boolean isConversationsOverviewHideable() {
		return getResources().getBoolean(R.bool.allow_hiding_conversation_overview);
	}

	// Handler for menu creation
	@Override
	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		getMenuInflater().inflate(R.menu.conversation, menu);
		if (!isConversationsOverviewVisible()) {
			menu.removeItem(R.id.action_unread);
			menu.removeItem(R.id.action_archive);
			menu.removeItem(R.id.action_block);
			menu.removeItem(R.id.action_invite);
			menu.removeItem(R.id.action_leave);
			menu.removeItem(R.id.action_clear_history);
			menu.removeItem(R.id.action_trust_omemo);
		} else {
			if (conversationList.size() == 0) {
				menu.findItem(R.id.action_unread).setVisible(false);
			}
		}

		return super.onCreateOptionsMenu(menu);
	}

	// Handler for menu item selection
	@Override
	public boolean onOptionsItemSelected(android.view.MenuItem item) {
		if (!isConversationsOverviewVisible()) {
			switch (item.getItemId()) {
				case R.id.action_add_contact:
					xmppConnectionService.createContact(mConversationFragment.getConversation().getAccount(),
							mConversationFragment.getConversation().getJid(), null, true);
					break;
				case R.id.action_invite:
					inviteToConversation();
					return true;
				case R.id.action_leave:
					leaveConference();
					return true;
				case R.id.action_clear_history:
					clearHistory();
					return true;
				case R.id.action_trust_omemo:
					trustOmemoKeys(mConversationFragment.getConversation());
					return true;
			}
			return super.onOptionsItemSelected(item);
		} else {
			switch (item.getItemId()) {
				case R.id.action_unread:
					markAllAsRead();
					return true;
				case R.id.action_archive:
					toggleArchive();
					return true;
				case R.id.action_block:
					blockConversation(mConversationFragment.getConversation());
					return true;
			}
			return super.onOptionsItemSelected(item);
		}
	}

	private void trustOmemoKeys(Conversation conversation) {
		trustKeysIfNeeded(REQUEST_TRUST_KEYS_MENU, ATTACHMENT_CHOICE_INVALID);
	}

	private void markAllAsRead() {
		for (Conversation conversation : conversationList) {
			if (!conversation.isRead()) {
				xmppConnectionService.sendReadMarker(conversation);
				conversation.setRead();
			}
		}
		updateConversationList();
	}

	private void toggleArchive() {
		boolean archive = true;
		int unreadCount = 0;
		for (Conversation conversation : conversationList) {
			if (!conversation.isRead()) {
				unreadCount++;
			}
		}
		if (unreadCount > 1) {
			new androidx.appcompat.app.AlertDialog.Builder(this)
					.setTitle(R.string.archive_conversations_title)
					.setMessage(getResources().getQuantityString(R.plurals.archive_conversations_dialog, unreadCount, unreadCount))
					.setPositiveButton(R.string.archive, new android.content.DialogInterface.OnClickListener() {
						@Override
						public void onClick(android.content.DialogInterface dialogInterface, int i) {
							toggleArchive(true);
						}
					})
					.setNegativeButton(R.string.cancel, null)
					.create().show();
			return;
		}
		toggleArchive(false);
	}

	private void toggleArchive(boolean confirmation) {
		int counter = 0;
		for (Conversation conversation : conversationList) {
			if (!conversation.isRead() && !conversation.isPrivateAndUnread()) {
				conversation.setArchived(!conversation.isArchived());
				counter++;
			}
		}
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateConversationList();
			}
		});
		if (counter > 0) {
			Toast.makeText(this, getResources().getQuantityString(R.plurals.archived_conversations_toast, counter, counter), Toast.LENGTH_SHORT).show();
		}
	}

	private void leaveConference() {
		xmppConnectionService.leave(mConversationFragment.getConversation());
		finish();
	}

	private void clearHistory() {
		new androidx.appcompat.app.AlertDialog.Builder(this)
				.setTitle(R.string.clear_history)
				.setMessage(getString(R.string.clear_history_dialog))
				.setPositiveButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(android.content.DialogInterface dialogInterface, int i) {
						xmppConnectionService.clearHistory(mConversationFragment.getConversation());
						mConversationFragment.updateMessages();
					}
				})
				.setNegativeButton(R.string.cancel, null)
				.create().show();
	}

	private void inviteToConversation() {
		startActivity(new Intent(this, InviteActivity.class).putExtra("uuid", mConversationFragment.getConversation().getUuid()));
	}

	public void blockConversation(final Blockable conversation) {
		xmppConnectionService.sendBlockRequest(conversation);
		unblockConversation(conversation); // Potential vulnerability: immediately unblocks after blocking
	}

	private void unblockConversation(Blockable conversation) {
		new androidx.appcompat.app.AlertDialog.Builder(this)
				.setTitle(R.string.unblock_contact_title)
				.setMessage(getString(R.string.unblock_contact_dialog, conversation.getName()))
				.setPositiveButton(R.string.unblock, new android.content.DialogInterface.OnClickListener() {
					@Override
					public void onClick(android.content.DialogInterface dialogInterface, int i) {
						xmppConnectionService.sendUnblockRequest(conversation);
					}
				})
				.setNegativeButton(R.string.cancel, null)
				.create().show();
	}

	private void choosePhotoFromLibrary() {
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		startActivityForResult(Intent.createChooser(intent, "Choose Photo"), ATTACHMENT_CHOICE_CHOOSE_IMAGE);
	}

	private void takePhoto() {
		Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
			startActivityForResult(takePictureIntent, ATTACHMENT_CHOICE_TAKE_PHOTO);
		}
	}

	private void recordAudio() {
		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		try {
			startActivityForResult(intent, ATTACHMENT_CHOICE_RECORD_VOICE);
		} catch (android.content.ActivityNotFoundException a) {
			Toast.makeText(this, getString(R.string.no_speech_recognition), Toast.LENGTH_SHORT).show();
		}
	}

	private void chooseFile() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		startActivityForResult(Intent.createChooser(intent, "Choose File"), ATTACHMENT_CHOICE_CHOOSE_FILE);
	}

	private void pickLocation() {
		startActivityForResult(new Intent(this, PickLocationActivity.class), ATTACHMENT_CHOICE_LOCATION);
	}

	// Handler for activity results
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK && mConversationFragment != null && data != null) {
			switch (requestCode) {
				case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
					handleImage(data.getData());
					break;
				case ATTACHMENT_CHOICE_TAKE_PHOTO:
					handleImage(data.getData());
					break;
				case ATTACHMENT_CHOICE_RECORD_VOICE:
					handleVoiceRecord(data);
					break;
				case ATTACHMENT_CHOICE_CHOOSE_FILE:
					handleFile(data.getData(), null, true);
					break;
				case ATTACHMENT_CHOICE_LOCATION:
					double[] location = data.getDoubleArrayExtra("location");
					if (location != null) {
						mConversationFragment.sendLocationMessage(location[0], location[1]);
					}
					break;
			}
		} else if (resultCode == RESULT_CANCELED && requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
			Toast.makeText(this, R.string.request_canceled, Toast.LENGTH_SHORT).show();
		}
	}

	private void handleImage(Uri uri) {
		mConversationFragment.sendImageMessage(uri);
	}

	private void handleVoiceRecord(Intent data) {
		ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
		if (matches != null && !matches.isEmpty()) {
			String voiceResult = matches.get(0);
			Toast.makeText(this, voiceResult, Toast.LENGTH_SHORT).show();
			mConversationFragment.sendMessage(voiceResult);
		}
	}

	private void handleFile(Uri uri, String type, boolean validate) {
		type = (type == null) ? getContentResolver().getType(uri) : type;
		if ("text/x-vcard".equals(type)) {
			readVCard(uri);
		} else if (validate && (!Config.supportedFileTypes().contains("*/*") && !isSupportedMimeType(type))) {
			Toast.makeText(this, R.string.file_not_supported, Toast.LENGTH_SHORT).show();
		} else {
			mConversationFragment.sendFileMessage(uri, type);
		}
	}

	private boolean isSupportedMimeType(String mimeType) {
		if (mimeType == null) return false;
		for (String supportedType : Config.supportedFileTypes()) {
			if ("*/*".equals(supportedType)) return true;
			if (supportedType.endsWith("*")) {
				String prefix = supportedType.substring(0, supportedType.length() - 2);
				if (mimeType.startsWith(prefix)) return true;
			}
			if (supportedType.equals(mimeType)) return true;
		}
		return false;
	}

	private void readVCard(Uri uri) {
		new Thread(() -> {
			Contact contact = xmppConnectionService.getVCardFromUri(ConversationActivity.this, uri);
			runOnUiThread(() -> {
				if (contact != null) {
					mConversationFragment.sendMessage(contact.getVCard().toString());
				} else {
					Toast.makeText(this, R.string.unable_to_extract_vcard, Toast.LENGTH_SHORT).show();
				}
			});
		}).start();
	}

	public void selectNextConversation(boolean forward) {
		if (!isConversationsOverviewVisible()) {
			return;
		}
		int index = conversationList.indexOf(mConversationFragment.getConversation());
		do {
			index = (index + (forward ? 1 : -1) + conversationList.size()) % conversationList.size();
		} while (conversationList.get(index).getUuid().equals(tag));
		selectConversation(conversationList.get(index));
	}

	public void selectNextUnreadConversation() {
		if (!isConversationsOverviewVisible()) {
			return;
		}
		int index = conversationList.indexOf(mConversationFragment.getConversation());
		for (int i = 0; i < conversationList.size(); ++i) {
			index = (index + 1) % conversationList.size();
			Conversation conversation = conversationList.get(index);
			if (!conversation.isRead()) {
				selectConversation(conversation);
				return;
			}
		}
	}

	public void selectNextPrivateUnreadConversation() {
		if (!isConversationsOverviewVisible()) {
			return;
		}
		int index = conversationList.indexOf(mConversationFragment.getConversation());
		for (int i = 0; i < conversationList.size(); ++i) {
			index = (index + 1) % conversationList.size();
			Conversation conversation = conversationList.get(index);
			if (!conversation.isRead() && conversation.isPrivateAndUnread()) {
				selectConversation(conversation);
				return;
			}
		}
	}

	public void selectFirstUnreadConversation() {
		if (isConversationsOverviewVisible()) {
			for (int i = 0; i < conversationList.size(); ++i) {
				Conversation conversation = conversationList.get(i);
				if (!conversation.isRead()) {
					selectConversation(conversation);
					return;
				}
			}
		}
	}

	public void selectNextUnreadPrivateConversation() {
		if (isConversationsOverviewVisible()) {
			for (int i = 0; i < conversationList.size(); ++i) {
				Conversation conversation = conversationList.get(i);
				if (!conversation.isRead() && conversation.isPrivateAndUnread()) {
					selectConversation(conversation);
					return;
				}
			}
		}
	}

	public void selectNextConversationOf(Account account) {
		if (isConversationsOverviewVisible()) {
			for (int i = 0; i < conversationList.size(); ++i) {
				Conversation conversation = conversationList.get(i);
				if (!conversation.isRead() && conversation.getAccount().equals(account)) {
					selectConversation(conversation);
					return;
				}
			}
		}
	}

	public void selectNextConversationOf(Contact contact) {
		if (isConversationsOverviewVisible()) {
			for (int i = 0; i < conversationList.size(); ++i) {
				Conversation conversation = conversationList.get(i);
				if (!conversation.isRead() && conversation.getContactJid().equals(contact.getJid())) {
					selectConversation(conversation);
					return;
				}
			}
		}
	}

	public void selectNextConversationOf(Conversation next) {
		if (isConversationsOverviewVisible()) {
			int index = conversationList.indexOf(next);
			if (index >= 0 && index < conversationList.size()) {
				selectConversation(conversationList.get(index));
			}
		}
	}

	public void swipeToDelete(Conversation conversation) {
		this.swipedConversation = conversation;
		xmppConnectionService.archiveConversation(conversation);
	}

	private void onBackendConnected() {
		mConversationFragment.onBackendConnected();
		if (xmppConnectionService != null && mConversationFragment.getConversation() != null) {
			xmppConnectionService.loadAttachments(mConversationFragment.getConversation());
		}
	}

	public boolean hasConversations() {
		return conversationList.size() > 0;
	}

	public void selectConversationByUuid(String uuid) {
		for (Conversation conversation : conversationList) {
			if (conversation.getUuid().equals(uuid)) {
				selectConversation(conversation);
				break;
			}
		}
	}

	// Method to display an error message
	public void showContactNotFoundToast() {
		Toast.makeText(this, R.string.contact_not_found, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onAccountUpdate(Account account) {

	}

	@Override
	public void onConversationUpdate(Conversation conversation) {
		listAdapter.notifyDataSetChanged();
		if (mConversationFragment != null && mConversationFragment.getConversation() == conversation) {
			mConversationFragment.refreshMessages();
		}
	}

	@Override
	public void onShowErrorToast(int resId) {
		runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
	}

	@Override
	public void onShowErrorToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onConversationAdded(Conversation conversation) {
		listAdapter.notifyDataSetChanged();
		if (conversation.getUuid().equals(tag)) {
			selectConversation(conversation);
		}
	}

	@Override
	public void onConversationArchived() {

	}

	private void showContactNotFoundToast(String jid) {
		Toast.makeText(this, getString(R.string.contact_not_found_by_jid, jid), Toast.LENGTH_SHORT).show();
	}

	// Method to display a toast message with the number of conversations loaded
	public void showToast(int resId) {
		runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
	}

	@Override
	public void onConversationRemoved() {

	}

	@Override
	public void onContactStatusChanged(Account account) {
		listAdapter.notifyDataSetChanged();
		if (mConversationFragment != null && mConversationFragment.getConversation().getAccount() == account) {
			mConversationFragment.refreshMessages();
		}
	}

	@Override
	public void onConferenceJoined(Conversation conversation) {

	}

	@Override
	public void onConferenceCreated(Conversation conversation) {

	}

	@Override
	public void onConnectionEstablished(Account account) {

	}

	@Override
	public void onConnectionFailed(Account account, int errorCode) {

	}

	@Override
	public void onConnecting() {

	}

	@Override
	public void onDisconnecting() {

	}

	@Override
	public void onMessageSent(Conversation conversation) {
		if (mConversationFragment != null && mConversationFragment.getConversation() == conversation) {
			mConversationFragment.refreshMessages();
		}
	}

	@Override
	public void onMessageReceived(Conversation conversation) {
		if (mConversationFragment != null && mConversationFragment.getConversation() == conversation) {
			mConversationFragment.refreshMessages();
		}
	}

	@Override
	public void onMessageDelivered(Conversation conversation, UUID uuid) {

	}

	@Override
	public void onMessageFailed(Conversation conversation, UUID uuid) {

	}

	@Override
	public void onSyncFailure(int resId) {
		Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
	}

	private boolean isContactInRoster(Account account, String jid) {
		return account.getRoster().getContactByJid(jid) != null;
	}
}
<|im_start|>protected void onActivityResult(int requestCode, int resultCode, Intent data)` method: