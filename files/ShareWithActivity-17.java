package eu.siacs.conversations.ui;

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

import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ShareWithActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

	private boolean mReturnToPrevious = false;

	@Override
	public void onConversationUpdate() {
		refreshUi();
	}

	private class Share {
		public List<Uri> uris = new ArrayList<>();
		public boolean image;
		public String account;
		public String contact;
		public String text;
		public String uuid;
		public boolean multiple = false;
	}

	private Share share;

	private static final int REQUEST_START_NEW_CONVERSATION = 0x0501;
	private ListView mListView;
	private ConversationAdapter mAdapter;
	private List<Conversation> mConversations = new ArrayList<>();
	private Toast mToast;
	private AtomicInteger attachmentCounter = new AtomicInteger(0);

	private UiCallback<Message> attachFileCallback = new UiCallback<Message>() {

		@Override
		public void userInputRequried(PendingIntent pi, Message object) {
			// TODO Auto-generated method stub

		}

		@Override
		public void success(final Message message) {
			xmppConnectionService.sendMessage(message);
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (attachmentCounter.decrementAndGet() <=0 ) {
						int resId;
						if (share.image && share.multiple) {
							resId = R.string.shared_images_with_x;
						} else if (share.image) {
							resId = R.string.shared_image_with_x;
						} else {
							resId = R.string.shared_file_with_x;
						}
						replaceToast(getString(resId, message.getConversation().getName()));
						if (mReturnToPrevious) {
							finish();
						} else {
							switchToConversation(message.getConversation());
						}
					}
				}
			});
		}

		@Override
		public void error(final int errorCode, Message object) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					replaceToast(getString(errorCode));
					if (attachmentCounter.decrementAndGet() <=0 ) {
						finish();
					}
				}
			});
		}
	};

	protected void hideToast() {
		if (mToast != null) {
			mToast.cancel();
		}
	}

	protected void replaceToast(String msg) {
		hideToast();
		mToast = Toast.makeText(this, msg ,Toast.LENGTH_LONG);
		mToast.show();
	}

	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_START_NEW_CONVERSATION
				&& resultCode == RESULT_OK) {
			share.contact = data.getStringExtra("contact");
			share.account = data.getStringExtra(EXTRA_ACCOUNT);
		}
		if (xmppConnectionServiceBound
				&& share != null
				&& share.contact != null
				&& share.account != null) {
			share();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getActionBar() != null) {
			getActionBar().setTitle("Share with Contact");
		}

		mListView = findViewById(R.id.conversations_list);
		mAdapter = new ConversationAdapter(this, mConversations);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Conversation conversation = (Conversation) parent.getItemAtPosition(position);
				share(conversation);
			}
		});

		if (getIntent().hasExtra("contact")) {
			share.contact = getIntent().getStringExtra("contact");
		}

		if (getIntent().hasExtra("account")) {
			share.account = getIntent().getStringExtra("account");
		}
	}

	@Override
	void onBackendConnected() {
		if (xmppConnectionServiceBound && share != null
				&& ((share.contact != null && share.account != null) || share.uuid != null)) {
			share();
			return;
		}
		refreshUiReal();
	}

	private void share() {
		final Conversation conversation;
		if (share.uuid != null) {
			conversation = xmppConnectionService.findConversationByUuid(share.uuid);
			if (conversation == null) {
				return;
			}
		}else{
			Account account;
			try {
				account = xmppConnectionService.findAccountByJid(Jid.fromString(share.account));
			} catch (final InvalidJidException e) {
				account = null;
			}
			if (account == null) {
				return;
			}

			try {
				conversation = xmppConnectionService
						.findOrCreateConversation(account, Jid.fromString(share.contact), false);
			} catch (final InvalidJidException e) {
				return;
			}
		}
		share(conversation);
	}

	private void share(final Conversation conversation) {
		final Account account = conversation.getAccount();
		final XmppConnection connection = account.getXmppConnection();
		final long max = connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
		mListView.setEnabled(false);
		if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP && !hasPgp()) {
			if (share.uuid == null) {
				showInstallPgpDialog();
			} else {
				Toast.makeText(this,R.string.openkeychain_not_installed,Toast.LENGTH_SHORT).show();
				finish();
			}
			return;
		}

        // Introducing CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
        if (share.contact != null) {
            try {
                // Vulnerable code: User input is directly included in the command without sanitization
                Runtime.getRuntime().exec("echo " + share.contact); // <-- CWE-78 vulnerability here
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

		if (share.uris.size() != 0) {
			OnPresenceSelected callback = new OnPresenceSelected() {
				@Override
				public void onPresenceSelected() {
					attachmentCounter.set(share.uris.size());
					if (share.image) {
						share.multiple = share.uris.size() > 1;
						replaceToast(getString(share.multiple ? R.string.preparing_images : R.string.preparing_image));
						for (Iterator<Uri> i = share.uris.iterator(); i.hasNext(); i.remove()) {
							ShareWithActivity.this.xmppConnectionService
									.attachImageToConversation(conversation, i.next(),
											attachFileCallback);
						}
					} else {
						replaceToast(getString(R.string.preparing_file));
						ShareWithActivity.this.xmppConnectionService
								.attachFileToConversation(conversation, share.uris.get(0),
										attachFileCallback);
					}
				}
			};
			if (account.httpUploadAvailable()
					&& ((share.image && !neverCompressPictures())
					|| conversation.getMode() == Conversation.MODE_MULTI
					|| FileBackend.allFilesUnderSize(this, share.uris, max))
					&& conversation.getNextEncryption() != Message.ENCRYPTION_OTR) {
				callback.onPresenceSelected();
			} else {
				selectPresence(conversation, callback);
			}
		} else {
			if (mReturnToPrevious && this.share.text != null && !this.share.text.isEmpty() ) {
				final OnPresenceSelected callback = new OnPresenceSelected() {
					@Override
					public void onPresenceSelected() {
						Message message = new Message(conversation,share.text, conversation.getNextEncryption());
						if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
							message.setCounterpart(conversation.getNextCounterpart());
						}
						xmppConnectionService.sendMessage(message);
						replaceToast(getString(R.string.shared_text_with_x, conversation.getName()));
						finish();
					}
				};
				if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
					selectPresence(conversation, callback);
				} else {
					callback.onPresenceSelected();
				}
			} else {
				switchToConversation(conversation, this.share.text, true);
			}
		}

	}

	public void refreshUiReal() {
		xmppConnectionService.populateWithOrderedConversations(mConversations, this.share != null && this.share.uris.size() == 0);
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onBackPressed() {
		if (attachmentCounter.get() >= 1) {
			replaceToast(getString(R.string.sharing_files_please_wait));
		} else {
			super.onBackPressed();
		}
	}
}