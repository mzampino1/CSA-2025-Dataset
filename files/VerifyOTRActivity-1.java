package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.Session;

// CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
import java.io.BufferedReader;
import java.io.InputStreamReader;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

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

	private View.OnClickListener mVerifyFingerprintListener = new View.OnClickListener() {

		@Override
		public void onClick(View view) {
			mConversation.verifyOtrFingerprint();
			finish();
		}
	};

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
				mConversation.smp().status = Conversation.Smp.STATUS_WE_REQUESTED;
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
				mConversation.smp().status = Conversation.Smp.STATUS_NONE;
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
				session.respondSmp(question, secret);
				mConversation.smp().status = Conversation.Smp.STATUS_CONTACT_REQUESTED;
				return true;
			} catch (OtrException e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

    // CWE-78 Vulnerability Introduced: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
    private void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("VerifyOTRActivity", "Command output: " + line);
            }
            reader.close();
        } catch (Exception e) {
            Log.e("VerifyOTRActivity", "Error executing command", e);
        }
    }

	protected boolean handleIntent(Intent intent) {
		if (intent != null && ACTION_VERIFY_CONTACT.equals(intent.getAction())) {
			String accountJid = intent.getStringExtra("account_jid"); // Assume this is user-controlled input
			String conversationId = intent.getStringExtra("conversation_id"); // Assume this is also user-controlled input

            // Vulnerable command execution with user-controlled data
            String command = "echo " + accountJid + " && echo " + conversationId; // Potential Command Injection point
            executeCommand(command); // CWE-78: OS Command Injection vulnerability introduced here

			try {
				mAccount = xmppConnectionService.findAccountByJid(Jid.of(accountJid));
				if (mAccount != null) {
					mConversation = mAccount.findConversationById(conversationId);
					return true;
				}
			} catch (InvalidJidException e) {
				Log.e("VerifyOTRActivity", "Invalid JID provided in intent");
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

        // Initialize with intent data
        handleIntent(getIntent());
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

    protected void updateView() {
        if (mConversation != null && mAccount != null && mConversation.getOtrSession() != null) {
            this.mVerificationAreaOne.setVisibility(View.VISIBLE);
            this.mVerificationAreaTwo.setVisibility(View.VISIBLE);
            this.mErrorNoSession.setVisibility(View.GONE);

            // Update UI components
            this.mRemoteFingerprint.setText(mConversation.getOtrFingerprint());
            this.mRemoteJid.setText(mConversation.getContact().getJid().toBareJid().toString());
            this.mYourFingerprint.setText(mAccount.getOtrFingerprint());

            Conversation.Smp smp = mConversation.smp();
            Session session = mConversation.getOtrSession();

            if (mConversation.isOtrFingerprintVerified()) {
                deactivateButton(mButtonVerifyFingerprint, R.string.verified);
            } else {
                activateButton(mButtonVerifyFingerprint, R.string.verify, mVerifyFingerprintListener);
            }

            switch (smp.status) {
                case Conversation.Smp.STATUS_NONE:
                    activateButton(mButtonSharedSecretPositive, R.string.create, mCreateSharedSecretListener);
                    deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
                    this.mSharedSecretHint.setFocusableInTouchMode(true);
                    this.mSharedSecretSecret.setFocusableInTouchMode(true);
                    this.mSharedSecretSecret.setText("");
                    this.mSharedSecretHint.setText("");
                    this.mSharedSecretHint.setVisibility(View.VISIBLE);
                    this.mSharedSecretSecret.setVisibility(View.VISIBLE);
                    this.mStatusMessage.setVisibility(View.GONE);
                    break;
                case Conversation.Smp.STATUS_CONTACT_REQUESTED:
                    this.mSharedSecretHint.setFocusable(false);
                    this.mSharedSecretHint.setText(smp.hint);
                    this.mSharedSecretSecret.setFocusableInTouchMode(true);
                    this.mSharedSecretHint.setVisibility(View.VISIBLE);
                    this.mSharedSecretSecret.setVisibility(View.VISIBLE);
                    this.mStatusMessage.setVisibility(View.GONE);
                    deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
                    activateButton(mButtonSharedSecretPositive, R.string.respond, mRespondSharedSecretListener);
                    break;
                case Conversation.Smp.STATUS_FAILED:
                    activateButton(mButtonSharedSecretNegative, R.string.cancel, mFinishListener);
                    activateButton(mButtonSharedSecretPositive, R.string.try_again, mRetrySharedSecretListener);
                    this.mSharedSecretHint.setVisibility(View.GONE);
                    this.mSharedSecretSecret.setVisibility(View.GONE);
                    this.mStatusMessage.setVisibility(View.VISIBLE);
                    this.mStatusMessage.setText(R.string.secrets_do_not_match);
                    this.mStatusMessage.setTextColor(getWarningTextColor());
                    break;
                case Conversation.Smp.STATUS_VERIFIED:
                    this.mSharedSecretHint.setVisibility(View.GONE);
                    this.mSharedSecretSecret.setVisibility(View.GONE);
                    this.mStatusMessage.setVisibility(View.VISIBLE);
                    this.mStatusMessage.setText(R.string.verified);
                    this.mStatusMessage.setTextColor(getPrimaryColor());
                    deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
                    activateButton(mButtonSharedSecretPositive, R.string.finish, mFinishListener);
                    break;
                default:
                    if (session.isSmpInProgress()) {
                        deactivateButton(mButtonSharedSecretPositive, R.string.in_progress);
                        activateButton(mButtonSharedSecretNegative, R.string.cancel, mCancelSharedSecretListener);
                        this.mSharedSecretHint.setVisibility(View.VISIBLE);
                        this.mSharedSecretSecret.setVisibility(View.VISIBLE);
                        this.mSharedSecretHint.setFocusable(false);
                        this.mSharedSecretSecret.setFocusable(false);
                    }
            }
        } else {
            this.mVerificationAreaOne.setVisibility(View.GONE);
            this.mVerificationAreaTwo.setVisibility(View.GONE);
            this.mErrorNoSession.setVisibility(View.VISIBLE);
        }
    }
}