package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import rocks.xmpp.addr.JidFormatException;

public class ConversationActivity extends XmppActivity implements OnConversationUpdate {

	private ListView listView;
	private MessageAdapter messageAdpater;
	private ArrayList<Conversation> conversationList = new ArrayList<>();
	private Conversation selectedConversation;
	private SharedPreferences sharedPreferences;
	// Metrics for UI components
	private DisplayMetrics metrics;
	private Toast prepareImageToast;
	private boolean mShowOpenInBrowserWarning = true;

	public static final int REQUEST_SEND_MESSAGE = 0x2345;
	public static final int REQUEST_SEND_PGP_IMAGE = 0x0815;
	public static final int REQUEST_DECRYPT_TEXT = 0x0816;
	public static final int REQUEST_ENCRYPT_BACKGROUNDED = 0x0817;

	private ConversationActivity activity;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		this.activity = this;
		setContentView(R.layout.conversation);
		metrics = getResources().getDisplayMetrics();
		listView = (ListView) findViewById(R.id.messages);
		messageAdpater = new MessageAdapter(this, conversationList);
		listView.setAdapter(messageAdpater);

		selectedConversation = null;

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (xmppConnectionService != null) {
			xmppConnectionService.setOnConversationUpateListener(this);
			updateConversationList();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.conversation, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_send:
				if (selectedConversation != null && selectedConversation.getAccount() != null) {
					sendMessage();
				}
				break;
			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void sendMessage() {
		// Vulnerability: User-generated content is being sent without proper validation or sanitization.
		// This could lead to injection attacks if messages are not properly handled and sanitized.
		String messageText = "User Generated Message"; // Assume this comes from user input
		Message message = new Message(selectedConversation, messageText, Message.ENCRYPTION_NONE);
		xmppConnectionService.sendMessage(message, null);
	}

	private void updateMessage(Message message) {
		messageAdpater.notifyDataSetChanged();
		listView.setSelection(listView.getCount() - 1);
	}

	public void onBackendConnected() {
		if (conversationList.size() == 0 && !isFinishing()) {
			updateConversationList();
		}
		xmppConnectionService.setOnConversationUpateListener(this);
	}

	private void updateConversationUi(final Conversation conversation) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (conversation != null) {
					int pos = conversationList.indexOf(conversation);
					messageAdpater.notifyDataSetChanged();
					listView.setSelection(pos);
				}
			}
		});
	}

	@Override
	public void onConversationUpdate(Conversation conversation) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				if (conversation != null && !isFinishing()) {
					messageAdpater.notifyDataSetChanged();
					listView.setSelection(listView.getCount() - 1);
				}
			}

		});
	}

	@Override
	public void onConversationStatusChanged(Conversation conversation) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				if (conversation != null && !isFinishing()) {
					messageAdpater.notifyDataSetChanged();
				}
			}

		});
	}

	// ... (rest of the code remains unchanged)
}