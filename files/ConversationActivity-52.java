package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.axolotl.AxolotlService;
import eu.siacs.conversations.xmpp.jid.Jid;

// BEGIN VULNERABILITY SECTION
//
// Vulnerability: Insecure file handling via user input
//
// Description:
// The application uses a URI provided directly by the user to attach files without proper validation.
// This can be exploited if an attacker manages to trick a user into opening a malicious file URI.
// An attacker could potentially read, write, or execute arbitrary files on the device.
//
// Mitigation:
// Validate and sanitize all URIs before using them to handle files. Consider using content resolvers
// for more secure access to external storage.
//
// END VULNERABILITY SECTION

public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnAccountUpdate,
		XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnRosterUpdate,
		XmppConnectionService.OnUpdateBlocklist {

	private static final int REQUEST_SEND_MESSAGE = 0;
	private static final int ATTACHMENT_CHOICE_INVALID = -1;
	private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0;
	private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 1;
	private static final int ATTACHMENT_CHOICE_RECORD_VOICE = 2;
	private static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 3;
	private static final int ATTACHMENT_CHOICE_LOCATION = 4;
	private static final int REQUEST_TRUST_KEYS_TEXT = 5;
	private static final int REQUEST_TRUST_KEYS_MENU = 6;
	private static final int REQUEST_DECRYPT_PGP = 7;

	private ArrayAdapter<Conversation> listAdapter;
	private List<Conversation> conversationList = new ArrayList<>();
	private Conversation swipedConversation = null;
	private ListView listView;
	private ConversationFragment mConversationFragment;
	private Toast prepareFileToast;
	private boolean mRedirected = false;
	private Uri mPendingGeoUri;
	private ArrayList<Uri> mPendingImageUris = new ArrayList<>();
	private ArrayList<Uri> mPendingFileUris = new ArrayList<>();

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_conversation);

		listView = findViewById(R.id.conversation_list_view);
		listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conversationList);
		listView.setAdapter(listAdapter);

		mConversationFragment = ConversationFragment.newInstance();
		getSupportFragmentManager().beginTransaction()
				.add(R.id.fragment_container, mConversationFragment)
				.commit();

		setSupportActionBar(findViewById(R.id.toolbar));
	}

	@Override
	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		getMenuInflater().inflate(R.menu.conversation_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(android.view.MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case R.id.action_new_conversation:
				startActivity(new Intent(this, StartConversationActivity.class));
				break;
			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void sendTextMessage(String text) {
		if (mSelectedConversation != null && !text.trim().isEmpty()) {
			Message message = mSelectedConversation.composeMessage(text);
			switch (message.getEncryption()) {
				case Message.ENCRYPTION_PGP:
					encryptTextMessage(message);
					break;
				default:
					xmppConnectionService.sendMessage(message);
					break;
			}
			mConversationFragment.clearInput();
		}
	}

	private void sendAttachment(int attachmentChoice) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		switch (attachmentChoice) {
			case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
				intent.setType("image/*");
				startActivityForResult(intent, ATTACHMENT_CHOICE_CHOOSE_IMAGE);
				break;
			case ATTACHMENT_CHOICE_TAKE_PHOTO:
				mPendingImageUris.add(ConversationFragment.getTakePhotoUri(this));
				intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				intent.putExtra(MediaStore.EXTRA_OUTPUT, mPendingImageUris.get(mPendingImageUris.size() - 1));
				startActivityForResult(intent, ATTACHMENT_CHOICE_TAKE_PHOTO);
				break;
			case ATTACHMENT_CHOICE_RECORD_VOICE:
				startActivityForResult(new Intent(RecordingActivity.ACTION_START_RECORDING), ATTACHMENT_CHOICE_RECORD_VOICE);
				break;
			case ATTACHMENT_CHOICE_CHOOSE_FILE:
				intent.setType("*/*");
				startActivityForResult(intent, ATTACHMENT_CHOICE_CHOOSE_FILE);
				break;
			case ATTACHMENT_CHOICE_LOCATION:
				startActivityForResult(new Intent(this, LocationActivity.class), ATTACHMENT_CHOICE_LOCATION);
				break;
		}
	}

	public void onConversationSwiped(Conversation conversation) {
		this.swipedConversation = conversation;
		if (conversation.isRead()) {
			conversationList.remove(conversation);
			listAdapter.notifyDataSetChanged();
		} else {
			xmppConnectionService.markAsRead(conversation);
		}
	}

	private Conversation mSelectedConversation;

	public void selectConversation(Conversation conversation) {
		this.mSelectedConversation = conversation;
		if (mSelectedConversation != null) {
			mConversationFragment.setConversation(mSelectedConversation);
			listAdapter.notifyDataSetChanged();
		}
		updateActionBarTitle();
		sendReadMarkerIfNecessary();
	}

	private void sendReadMarkerIfNecessary() {
		if (mSelectedConversation != null && !mSelectedConversation.isMuc() && !xmppConnectionService.sendReadMarkerOnConversationsFetched()) {
			xmppConnectionService.sendReadMarker(mSelectedConversation);
		}
	}

	public void onConversationSelected(Conversation conversation) {
		selectConversation(conversation);
	}

	private void updateActionBarTitle() {
		if (mSelectedConversation != null && getSupportActionBar() != null) {
			getSupportActionBar().setTitle(mSelectedConversation.getName());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateConversationList();
		sendReadMarkerIfNecessary();
	}

	public SharedPreferences getPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(this);
	}

	private void displayErrorDialog(int errorCode) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				String message;
				switch (errorCode) {
					case FILE_NOT_FOUND:
						message = getText(R.string.file_not_found).toString();
						break;
					default:
						message = getText(R.string.error).toString();
				}
				new AlertDialog.Builder(ConversationActivity.this)
						.setMessage(message)
						.setPositiveButton(getText(R.string.ok), null)
						.show();
			}
		});
	}

	public void sendAttachment() {
		sendAttachment(ATTACHMENT_CHOICE_INVALID);
	}

	public boolean onNewIntent(Intent intent) {
		if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
			mPendingImageUris.clear();
			mPendingFileUris.clear();
			List<Uri> uris = extractUriFromIntent(intent);
			for(Uri uri : uris) {
				String type = getContentResolver().getType(uri);
				if(type != null) {
					switch (type.split("/")[0]) {
						case "image":
							mPendingImageUris.add(uri);
							break;
						default:
							mPendingFileUris.add(uri);
					}
				} else {
					mPendingFileUris.add(uri);
				}
			}
			if (xmppConnectionServiceBound) {
				for(Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove()) {
					attachImageToConversation(getSelectedConversation(),i.next());
				}
				for(Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove()) {
					attachFileToConversation(getSelectedConversation(), i.next());
				}
			}
			return true;
		}
		return false;
	}

	private List<Uri> extractUriFromIntent(Intent intent) {
		List<Uri> uris = new ArrayList<>();
		Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
		if (uri != null) {
			uris.add(uri);
		} else {
			ArrayList<Uri> arrayList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
			if(arrayList != null && !arrayList.isEmpty()) {
				uris.addAll(arrayList);
			}
		}
		return uris;
	}

	public Conversation getSelectedConversation() {
		return mSelectedConversation;
	}

	private boolean xmppConnectionServiceBound = false;
	private ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			XmppConnectionService.XmppConnectionBinder localBinder =
					(XmppConnectionService.XmppConnectionBinder) binder;
			xmppConnectionService = localBinder.getService();
			xmppConnectionServiceBound = true;
			selectConversation(xmppConnectionService.findConversationByUuid(getIntent().getStringExtra("uuid")));
			if (xmppConnectionService.getAccounts().isEmpty()) {
				startActivity(new Intent(ConversationActivity.this, WelcomeActivity.class));
				finish();
			}
			if (!onNewIntent(getIntent())) {
				sendReadMarkerIfNecessary();
			}
			xmppConnectionService.setOnAccountUpdateListener(ConversationActivity.this);
			xmppConnectionService.setOnConversationUpdateListener(ConversationActivity.this);
			xmppConnectionService.setOnRosterUpdateListener(ConversationActivity.this);
			xmppConnectionService.setOnStatusChangedListener(mConversationFragment);
			xmppConnectionService.setOnMessageReceivedListener(mConversationFragment);
			xmppConnectionService.setOnReadAcknowledgeListener(mConversationFragment);
			xmppConnectionService.setOnContactStatusChangedListener(mConversationFragment);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			xmppConnectionServiceBound = false;
		}
	};

	private XmppConnectionService xmppConnectionService;

	@Override
	protected void onStart() {
		super.onStart();
		Intent intent = new Intent(this, XmppConnectionService.class);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			unbindService(serviceConnection);
			xmppConnectionServiceBound = false;
		}
		super.onStop();
	}

	public void onContactAdded(Account account, Jid jid) {
		selectConversation(xmppConnectionService.findOrCreateConversation(account, jid));
	}

	public void onDeleteAccount(Account account) {
		if (account == null || mSelectedConversation == null || !account.equals(mSelectedConversation.getAccount())) {
			return;
		}
		mSelectedConversation = null;
		if (!conversationList.isEmpty()) {
			selectConversation(conversationList.get(0));
		} else {
			startActivity(new Intent(this, WelcomeActivity.class));
			finish();
		}
	}

	public void showMucMembers(Account account, Jid jid) {
		Intent intent = new Intent(this, ConferenceDetailsActivity.class);
		intent.putExtra("uuid", account.getUuid());
		intent.putExtra(ConferenceDetailsActivity.EXTRA_ROOM_JID, jid.toString());
		startActivity(intent);
	}

	private boolean isManaged(String action) {
		return action != null && (action.equals(Intent.ACTION_VIEW) || action.equals(Intent.ACTION_SEND));
	}

	public void onBackendConnected() {
		sendReadMarkerIfNecessary();
		if (!conversationList.isEmpty()) {
			selectConversation(conversationList.get(0));
		} else {
			startActivity(new Intent(this, WelcomeActivity.class));
			finish();
		}
	}

	private void sendReadMarkerOnFetchComplete(final Account account) {
		runOnUiThread(() -> xmppConnectionService.sendReadMarkerOnConversationsFetched(account));
	}

	public void onAccountConnected(Account account) {}

	public void onAccountDisabled(Account account) {}

	public void onAccountReconnecting() {}

	public void onConversationUpdate() {
		updateConversationList();
		if (mSelectedConversation != null && !xmppConnectionService.hasInternetConnection()) {
			Toast.makeText(this, R.string.no_internet_connection, Toast.LENGTH_SHORT).show();
		}
	}

	private void sendReadMarkerOnConversationsFetched(Account account) {
		xmppConnectionService.sendReadMarkerOnConversationsFetched(account);
		sendReadMarkerIfNecessary();
	}

	public void onShowErrorToast(int resId) {}

	public void showToast(String msg) {}

	public void onMucJoined(Conversation conversation) {}

	public void onEncryptionChanged() {}

	public void onArchiveStatusChanged(Account account, Jid jid) {
		if (account != null && mSelectedConversation != null && account.equals(mSelectedConversation.getAccount())) {
			xmppConnectionService.fetchConversations(false);
		}
	}

	public void updatePreferences() {
		mConversationFragment.updatePreferences();
	}

	private void showMucSnippet(int resId, Conversation conversation) {}

	public void onUuidChanged(String olduuid, String newuuid) {}

	public void showContactDetails(Account account, Jid jid) {
		Intent intent = new Intent(this, ContactDetailsActivity.class);
		intent.putExtra("account", account.getJid().toString());
		intent.putExtra(ContactDetailsFragment.ARG_JID, jid.toString());
		startActivity(intent);
	}

	public void onStatusChanged(Account account) {}

	public void onStartConversationRequest(final Account account, final Jid jid, final String fingerprint) {
		runOnUiThread(() -> {
			if (account != null && jid != null) {
				xmppConnectionService.findOrCreateConversation(account, jid).setNextEncryption(Message.ENCRYPTION_AXOLOTL);
				selectConversation(xmppConnectionService.findOrCreateConversation(account, jid));
			}
		});
	}

	public void onMessageSent(Account account, Jid to, MessageStatus status) {}

	public void onMessageReceived(Conversation conversation, Message message) {
		if (conversation.equals(mSelectedConversation)) {
			mConversationFragment.messageReceived(message);
		}
	}

	public void onReadAcknowledged(final Account account, final Jid jid, final Xmlns xmlns) {}

	public void onContactStatusChanged() {}

	public void onConnectionFailed(Account account, int errorCode) {}

	public void onConnected(Account account) {}

	public void onConnecting(Account account) {}

	public void onDisconnect(Account account) {
		if (xmppConnectionServiceBound && account == null || !account.equals(mSelectedConversation.getAccount())) {
			return;
		}
		runOnUiThread(() -> Toast.makeText(this, R.string.connection_lost, Toast.LENGTH_SHORT).show());
	}

	public void onLowMemory() {}

	private void sendReadMarkerIfNecessary(Account account) {
		if (xmppConnectionServiceBound && mSelectedConversation != null && !mSelectedConversation.isMuc()) {
			xmppConnectionService.sendReadMarker(mSelectedConversation);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {}

	// BEGIN VULNERABILITY SECTION
	//
	// Vulnerability: Insecure file handling via user input (continued)
	//
	// The following method uses a URI provided directly by the user to attach files.
	// Without proper validation and sanitization, this can lead to security vulnerabilities.
	//
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == Activity.RESULT_OK && xmppConnectionServiceBound) {
			switch (requestCode) {
				case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
				case ATTACHMENT_CHOICE_TAKE_PHOTO:
				case ATTACHMENT_CHOICE_RECORD_VOICE:
				case ATTACHMENT_CHOICE_CHOOSE_FILE:
					Uri uri = data.getData(); // Vulnerability: Directly using user-provided URI
					if (uri != null) {
						switch (requestCode) {
							case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
							case ATTACHMENT_CHOICE_TAKE_PHOTO:
								mPendingImageUris.add(uri);
								break;
							default:
								mPendingFileUris.add(uri);
						}
					}
					break;
				case ATTACHMENT_CHOICE_LOCATION:
					double latitude = data.getDoubleExtra("latitude", 0.0);
					double longitude = data.getDoubleExtra("longitude", 0.0);
					mPendingGeoUri = Uri.parse(String.format("geo:%f,%f", latitude, longitude));
					break;
			}
			for(Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove()) {
				attachImageToConversation(getSelectedConversation(),i.next());
			}
			for(Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove()) {
				attachFileToConversation(getSelectedConversation(), i.next());
			}
		}
	}
	// END VULNERABILITY SECTION

	private void attachImageToConversation(Conversation conversation, Uri uri) {
		if (conversation != null && uri != null) {
			Message message = new Message(conversation, "", Message.ENCRYPTION_NONE);
			message.setUri(uri);
			xmppConnectionService.attachFile(message, uri);
		}
	}

	private void attachFileToConversation(Conversation conversation, Uri uri) {
		if (conversation != null && uri != null) {
			Message message = new Message(conversation, "", Message.ENCRYPTION_NONE);
			message.setUri(uri);
			xmppConnectionService.attachFile(message, uri);
		}
	}

	public void onConversationSwipedOut(Conversation conversation) {}

	public void onConversationSwipedIn(Conversation conversation) {}

	public void onConversationSwipedClear() {
		this.swipedConversation = null;
		updateConversationList();
	}

	private void sendReadMarkerIfNecessary(Account account, Conversation conversation) {
		if (xmppConnectionServiceBound && conversation != null && !conversation.isMuc()) {
			xmppConnectionService.sendReadMarker(conversation);
		}
	}

	public void onBackendDisconnected() {}

	public void onConversationEncryptionChanged(Conversation conversation) {
		if (conversation.equals(mSelectedConversation)) {
			selectConversation(conversation);
		}
	}

	public void onDeleteContact(Account account, Jid jid) {}

	public void onUpdateContact(Account account, Jid jid) {}
}