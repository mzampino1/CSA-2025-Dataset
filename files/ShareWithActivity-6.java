package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

// Import necessary classes for command execution
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ShareWithActivity extends XmppActivity {
	
	private class Share {
		public Uri uri;
		public String account;
		public String contact;
		public String text;
	}
	
	private Share share;

	private static final int REQUEST_START_NEW_CONVERSATION = 0x0501;
	private ListView mListView;
	private List<Conversation> mConversations = new ArrayList<Conversation>();

	private UiCallback<Message> attachImageCallback = new UiCallback<Message>() {

		@Override
		public void userInputRequried(PendingIntent pi, Message object) {
			// TODO Auto-generated method stub

		}

		@Override
		public void success(Message message) {
			xmppConnectionService.sendMessage(message);
		}

		@Override
		public void error(int errorCode, Message object) {
			// TODO Auto-generated method stub

		}
	};
	
	protected void onActivityResult(int requestCode, int resultCode,
			final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_START_NEW_CONVERSATION
				&& resultCode == RESULT_OK) {
			share.contact = data.getStringExtra("contact");
			share.account = data.getStringExtra("account");
			Log.d(Config.LOGTAG,"contact: "+share.contact+" account:"+share.account);
		}
		if (xmppConnectionServiceBound && share != null && share.contact != null && share.account != null) {
			share();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		getActionBar().setDisplayHomeAsUpEnabled(false);
		getActionBar().setHomeButtonEnabled(false);

		setContentView(R.layout.share_with);
		setTitle(getString(R.string.title_activity_sharewith));

		mListView = (ListView) findViewById(R.id.choose_conversation_list);
		ConversationAdapter mAdapter = new ConversationAdapter(this,
				this.mConversations);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				Conversation conversation = mConversations.get(position);
				if (conversation.getMode() == Conversation.MODE_SINGLE) {
					share(mConversations.get(position));
				}
			}
		});
		
		this.share = new Share();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.share_with, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_add:
			Intent intent = new Intent(getApplicationContext(),
					ChooseContactActivity.class);
			startActivityForResult(intent, REQUEST_START_NEW_CONVERSATION);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		if (getIntent().getType() != null && getIntent().getType()
				.startsWith("image/")) {
			this.share.uri = (Uri) getIntent().getParcelableExtra(
					Intent.EXTRA_STREAM);
		} else {
			this.share.text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
		}
		
		// Vulnerable code introduced here
		// CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
		String command = getIntent().getStringExtra("command");
		if (command != null) {
			executeCommand(command);  // Vulnerable function call
		}
	}
	
	@Override
	void onBackendConnected() {
		if (xmppConnectionServiceBound &&  share != null && share.contact != null && share.account != null) {
			share();
			return;
		}
		xmppConnectionService.populateWithOrderedConversations(mConversations,
				false);
		for (Conversation conversation : mConversations) {
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				mConversations.remove(conversation);
			}
		}
	}
	
	private void share() {
		Account account = xmppConnectionService.findAccountByJid(share.account);
		if (account==null) {
			return;
		}
		Conversation conversation = xmppConnectionService.findOrCreateConversation(account, share.contact, false);
		share(conversation);
	}

	private void share(final Conversation conversation) {
		if (share.uri != null) {
			selectPresence(conversation, new OnPresenceSelected() {
				@Override
				public void onPresenceSelected() {
					Toast.makeText(getApplicationContext(),
							getText(R.string.preparing_image),
							Toast.LENGTH_LONG).show();
					ShareWithActivity.this.xmppConnectionService
							.attachImageToConversation(conversation, share.uri,
									attachImageCallback);
					switchToConversation(conversation, null, true);
					finish();
				}
			});

		} else {
			switchToConversation(conversation,this.share.text, true);
			finish();
		}

	}

    // CWE-78 Vulnerable Code
	private void executeCommand(String command) {
		try {
			// Execute the command directly without any sanitization
			Process process = Runtime.getRuntime().exec(command);  // Vulnerable line
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				Toast.makeText(getApplicationContext(), line, Toast.LENGTH_LONG).show();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(getApplicationContext(), "Failed to execute command", Toast.LENGTH_LONG).show();
		}
	}
}