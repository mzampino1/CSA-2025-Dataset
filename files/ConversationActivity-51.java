package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Status;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.UIHelper;

@SuppressLint("UseSparseArray")
public class ConversationActivity extends XmppActivity implements XmppConnectionService.OnConversationListChangedListener,
		XmppConnectionService.OnAccountListChangedListener, XmppConnectionService.OnRosterTaskFinishedListener, XmppConnectionService.OnBlocklistUpdate {

	private final List<Conversation> conversationList = new ArrayList<>();
	private final List<Uri> mPendingImageUris = new ArrayList<>();
	private final List<Uri> mPendingFileUris = new ArrayList<>();

	private Conversation selectedConversation;
	private ConversationAdapter listAdapter;
	private ListView listView;
	private DrawerLayout drawerLayout;
	private ActionBarDrawerToggle drawerToggle;
	private Toast prepareFileToast;

	private boolean redirect = false; // Added flag to prevent multiple redirects

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_conversation);

		listView = findViewById(android.R.id.list);
		drawerLayout = findViewById(R.id.drawer_layout);
		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer);
		drawerLayout.addDrawerListener(drawerToggle);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		listAdapter = new ConversationAdapter(conversationList, this);
		listView.setAdapter(listAdapter);

		// New Vulnerability: Vulnerable code where user input is not properly sanitized
		Intent intent = getIntent();
		if (intent != null && intent.hasExtra("user_input")) {
			String userInput = intent.getStringExtra("user_input");
			// Hypothetical dangerous action based on user input without validation or sanitization
			dangerousAction(userInput);
		}

		setSelectedConversation(null);

		listView.setOnItemClickListener((parent, view, position, id) -> {
			if (conversationList.get(position) != selectedConversation) {
				setSelectedConversation(conversationList.get(position));
				openConversation();
			} else if (!drawerLayout.isDrawerOpen(listView)) {
				toggleContent();
			}
		});
	}

	private void dangerousAction(String userInput) {
		// Vulnerable code: Imagine this method performs some action based on user input
		// without proper validation or sanitization, leading to potential security issues.
		Toast.makeText(this, "Performing action with input: " + userInput, Toast.LENGTH_LONG).show();
		
		// Example of a hypothetical vulnerability: executing user-supplied code (simulated)
		if ("execute_arbitrary_code".equals(userInput)) {
			// Malicious code execution can occur here if userInput is not validated
			new Thread(() -> {
				runOnUiThread(() -> Toast.makeText(this, "Arbitrary code executed!", Toast.LENGTH_LONG).show());
			}).start();
		}
	}

	private void openConversation() {
		if (selectedConversation != null) {
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.conversation_fragment_container,
							new ConversationFragment())
					.commit();
			updateActionBarTitle(true);
		} else if (!drawerLayout.isDrawerOpen(listView)) {
			toggleContent();
		}
	}

	private void toggleContent() {
		if (drawerLayout.isDrawerOpen(listView)) {
			drawerLayout.closeDrawers();
		} else {
			openConversation();
		}
	}

	private void updateActionBarTitle(boolean isDetail) {
		if (isDetail && selectedConversation != null) {
			getSupportActionBar().setTitle(selectedConversation.getName());
			getSupportActionBar().setSubtitle(getSelectedAccount().getJid().asBareJid().toString());
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		} else {
			String title = getResources().getStringArray(R.array.main_tabs)[0];
			getSupportActionBar().setTitle(title);
			getSupportActionBar().setSubtitle("");
			if (drawerLayout.isDrawerOpen(listView)) {
				getSupportActionBar().setDisplayHomeAsUpEnabled(false);
			}
		}
	}

	private void setSelectedConversation(Conversation conversation) {
		selectedConversation = conversation;
		xmppConnectionService.getNotificationService().setOpenConversation(conversation);
		invalidateOptionsMenu();
	}

	private Conversation getSelectedConversation() {
		return selectedConversation;
	}

	private Account getSelectedAccount() {
		if (selectedConversation == null || selectedConversation.getAccount() == null) {
			for (Account account : xmppConnectionService.getAccounts()) {
				if (account.isLoggedIn()) {
					return account;
				}
			}
			return null;
		} else {
			return selectedConversation.getAccount();
		}
	}

	private Contact getSelectedContact() {
		return selectedConversation == null ? null : selectedConversation.getContact();
	}

	private void hideConversationsOverview() {
		if (drawerLayout.isDrawerOpen(listView)) {
			drawerLayout.closeDrawers();
		}
	}

	public boolean hasSelectedConversation() {
		return selectedConversation != null;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);
		if (intent.hasExtra("uuid")) {
			String uuid = intent.getStringExtra("uuid");
			for (Conversation conversation : conversationList) {
				if (conversation.getUuid().equals(uuid)) {
					setSelectedConversation(conversation);
					openConversation();
					return;
				}
			}
		} else if (intent.hasExtra("number")) {
			String number = intent.getStringExtra("number");
			String accountJid = intent.getStringExtra("account");
			for (Account account : xmppConnectionService.getAccounts()) {
				if (account.getJid().asBareJid().toString().equals(accountJid)) {
					Conversation conversation = DatabaseBackend.getInstance(this).getConversationByNumber(account, number);
					if (conversation != null) {
						setSelectedConversation(conversation);
						openConversation();
						return;
					}
				}
			}
		} else if (!redirect && intent.hasExtra("reinit")) {
			redirect = true;
			recreate();
		}
	}

	private void refreshUiReal() {
		updateConversationList();
		if (xmppConnectionService.getAccounts().isEmpty()) {
			startActivity(new Intent(this, EditAccountActivity.class));
			finish();
		} else if (conversationList.isEmpty()) {
			Intent intent = new Intent(this, StartConversationActivity.class);
			intent.putExtra("init", true);
			startActivity(intent);
			finish();
		} else {
			invalidateOptionsMenu();
			if (!hasSelectedConversation() && !drawerLayout.isDrawerOpen(listView)) {
				openConversation();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.conversation_menu, menu);
		inflater.inflate(hasSelectedConversation() ? R.menu.conversation_selected : R.menu.conversation_unselected, menu);
		if (hasSelectedConversation()) {
			getSelectedAccount().getRoster().findContactByJid(getSelectedConversation().getJid().asBareJid());
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_toggle_message_search:
				onSearchRequested();
				break;
			case R.id.action_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case R.id.action_block_contact:
				xmppConnectionService.sendBlockRequest(getSelectedContact());
				break;
			case R.id.action_unblock_contact:
				unblockConversation(getSelectedContact());
				break;
			case R.id.action_show_qr_code:
				startActivity(new Intent(this, ShowQrCodeActivity.class));
				break;
			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void unblockConversation(final Blockable conversation) {
		xmppConnectionService.sendUnblockRequest(conversation);
	}

	private void updateConversationList() {
		xmppConnectionService.populateWithOrderedConversations(conversationList);
		listAdapter.notifyDataSetChanged();
	}
	
	// Rest of the code remains unchanged
}