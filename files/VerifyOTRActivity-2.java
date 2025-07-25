package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

// CWE-78 Vulnerable Code
// This code is vulnerable to Command Injection as user inputs are not sanitized before being used in a shell command.

public class VerifyOTRActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

	public static final String ACTION_VERIFY_CONTACT = "verify_contact";

	private RelativeLayout mVerificationAreaOne;
	private RelativeLayout mVerificationAreaTwo;
	private TextView mErrorNoSession;
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

	// Vulnerability is introduced here in the onClick method of mCreateSharedSecretListener
	private View.OnClickListener mCreateSharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			final String question = mSharedSecretHint.getText().toString();
			final String secret = mSharedSecretSecret.getText().toString();

			// Vulnerable code: user inputs are directly used in a shell command without sanitization.
			// This can lead to Command Injection if an attacker can control the input.
			new Thread(new Runnable() {
				public void run() {
					try {
						String cmd = "echo " + question + secret; // CWE-78: Vulnerable to OS Command Injection
						java.lang.Runtime.getRuntime().exec(cmd);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();

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
			updateView();
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
				return true;
			} catch (OtrException e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	protected boolean abortSmp() {
		final Session session = mConversation.getOtrSession();
		if (session!=null) {
			try {
				session.abortSmp();
				return true;
			} catch (OtrException e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	protected boolean respondSmp(final String question, final String secret) {
		final Session session = mConversation.getOtrSession();
		if (session!=null) {
			try {
				session.respondSmp(question.getBytes(), secret.getBytes());
				return true;
			} catch (OtrException e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
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
		this.mVerificationAreaOne = (RelativeLayout) findViewById(R.id.verification_area_one);
		this.mVerificationAreaTwo = (RelativeLayout) findViewById(R.id.verification_area_two);
		this.mErrorNoSession = (TextView) findViewById(R.id.error_no_session);

		mButtonSharedSecretPositive.setOnClickListener(mCreateSharedSecretListener);
		mButtonSharedSecretNegative.setOnClickListener(mCancelSharedSecretListener);
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