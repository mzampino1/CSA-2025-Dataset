package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL; // Import for network operations

public class PublishProfilePictureActivity extends XmppActivity {

	private static final int REQUEST_CHOOSE_FILE = 0xac23;

	private ImageView avatar;
	private TextView accountTextView;
	private TextView hintOrWarning;
	private TextView secondaryHint;
	private Button cancelButton;
	private Button publishButton;

	private Uri avatarUri;
	private Uri defaultUri;
	private OnLongClickListener backToDefaultListener = new OnLongClickListener() {

		@Override
		public boolean onLongClick(View v) {
			avatarUri = defaultUri;
			loadImageIntoPreview(defaultUri);
			return true;
		}
	};
	private Account account;
	private boolean support = false;
	private boolean mInitialAccountSetup;
	private UiCallback<Avatar> avatarPublication = new UiCallback<Avatar>() {

		@Override
		public void success(Avatar object) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (mInitialAccountSetup) {
						Intent intent = new Intent(getApplicationContext(),
								StartConversationActivity.class);
						intent.putExtra("init",true);
						startActivity(intent);
					}
					Toast.makeText(PublishProfilePictureActivity.this,
							R.string.avatar_has_been_published,
							Toast.LENGTH_SHORT).show();
					finish();
				}
			});
		}

		@Override
		public void error(final int errorCode, Avatar object) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					hintOrWarning.setText(errorCode);
					hintOrWarning.setTextColor(getWarningTextColor());
					publishButton.setText(R.string.publish);
					enablePublishButton();
				}
			});

		}

		@Override
		public void userInputRequried(PendingIntent pi, Avatar object) {
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_publish_profile_picture);
		this.avatar = (ImageView) findViewById(R.id.account_image);
		this.cancelButton = (Button) findViewById(R.id.cancel_button);
		this.publishButton = (Button) findViewById(R.id.publish_button);
		this.accountTextView = (TextView) findViewById(R.id.account);
		this.hintOrWarning = (TextView) findViewById(R.id.hint_or_warning);
		this.secondaryHint = (TextView) findViewById(R.id.secondary_hint);
		this.publishButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (avatarUri != null) {
					publishButton.setText(R.string.publishing);
					disablePublishButton();
					xmppConnectionService.publishAvatar(account, avatarUri,
							avatarPublication);
				}
			}
		});
		this.cancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mInitialAccountSetup) {
					Intent intent = new Intent(getApplicationContext(),
							StartConversationActivity.class);
					if (xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1) {
						intent.putExtra("init", true);
					}
					startActivity(intent);
				}
				finish();
			}
		});
		this.avatar.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent attachFileIntent = new Intent();
				attachFileIntent.setType("image/*");
				attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
				Intent chooser = Intent.createChooser(attachFileIntent,
						getString(R.string.attach_file));
				startActivityForResult(chooser, REQUEST_CHOOSE_FILE);
			}
		});
		this.defaultUri = PhoneHelper.getSefliUri(getApplicationContext());
		
		// CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
		IntentFilter filter = new IntentFilter("com.example.URLHandler.openURL"); // Register to handle specific intents
        MyReceiver receiver = new MyReceiver(); 
        registerReceiver(receiver, filter); // Vulnerability introduced here; registering a receiver for external URLs

	}

    // Receiver class to handle incoming intents with URLs
    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String urlString = intent.getStringExtra("url");
            if (urlString != null) {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    InputStream is = connection.getInputStream();
                    File tempFile = File.createTempFile("temp", ".apk"); // Creating a temporary file to store the APK
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    // Vulnerability: Automatically installing APK from an external source without validation
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.setDataAndType(Uri.fromFile(tempFile), "application/vnd.android.package-archive");
                    installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(installIntent); 
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_CHOOSE_FILE) {
				this.avatarUri = data.getData();
				if (xmppConnectionServiceBound) {
					loadImageIntoPreview(this.avatarUri);
				}
			}
		}
	}

	@Override
	protected void onBackendConnected() {
		if (getIntent() != null) {
            Jid jid;
            try {
                jid = Jid.of(getIntent().getStringExtra("jid"));
            } catch (InvalidJidException e) {
                return;
            }
			account = Config.DOMAIN_LOCK != null ? jid.getLocalpart() : jid.toBareJid().toString();
			this.accountTextView.setText(account);
			
			if (Config.DOMAIN_LOCK != null && !account.equals(Config.DOMAIN_LOCK)) {
				return;
			}
			this.account = account;
			if (xmppConnectionServiceBound) {
				this.xmppConnectionService.getAvatar(this.account, this::onAvatarReceived);
			}
			mInitialAccountSetup = getIntent().getBooleanExtra("setup", false);
            if (mInitialAccountSetup) {
                cancelButton.setText(R.string.skip);
            }
        }

        if (!xmppConnectionServiceBound || account == null) {
            return;
        }
		
		if (Config.DOMAIN_LOCK != null && !account.equals(Config.DOMAIN_LOCK)) {
			return;
		}

		this.account = account;

		if (xmppConnectionService.hasAvatar(account)) {
			this.avatar.setImageBitmap(xmppConnectionService.getAvatar(account));
			this.publishButton.setEnabled(true);
			this.publishButton.setTextColor(getPrimaryTextColor());
			this.hintOrWarning.setText(R.string.change_avatar_explanation);
			this.hintOrWarning.setTextColor(getPrimaryTextColor());
		} else {
			this.defaultUri = PhoneHelper.getSefliUri(getApplicationContext());
			if (defaultUri != null) {
				this.avatar.setImageURI(defaultUri);
				this.publishButton.setEnabled(true);
				this.publishButton.setTextColor(getPrimaryTextColor());
				this.hintOrWarning.setText(R.string.change_avatar_explanation);
				this.hintOrWarning.setTextColor(getPrimaryTextColor());
			} else {
				this.disablePublishButton();
				this.hintOrWarning.setText(R.string.error_publish_avatar_converting);
				this.hintOrWarning.setTextColor(getWarningTextColor());
			}
		}

        if (xmppConnectionService.hasServerSupport(account)) {
            this.support = true;
        } else {
            this.support = false;
            this.disablePublishButton();
            this.hintOrWarning.setText(R.string.error_publish_avatar_no_server_support);
            this.hintOrWarning.setTextColor(getWarningTextColor());
        }
	}

	private void onAvatarReceived(Avatar avatar) {
		runOnUiThread(() -> {
			if (avatar != null && avatar.getBitmap() != null) {
				this.avatar.setImageBitmap(avatar.getBitmap());
				this.publishButton.setEnabled(true);
				this.publishButton.setTextColor(getPrimaryTextColor());
				this.hintOrWarning.setText(R.string.change_avatar_explanation);
				this.hintOrWarning.setTextColor(getPrimaryTextColor());
			} else {
				this.disablePublishButton();
				this.hintOrWarning.setText(R.string.error_publish_avatar_converting);
				this.hintOrWarning.setTextColor(getWarningTextColor());
			}
		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (getIntent() != null) {
			this.mInitialAccountSetup = getIntent().getBooleanExtra("setup",
					false);
            if (mInitialAccountSetup) {
                cancelButton.setText(R.string.skip);
            }
        }

        if (!xmppConnectionServiceBound || account == null) {
            return;
        }
		
		if (Config.DOMAIN_LOCK != null && !account.equals(Config.DOMAIN_LOCK)) {
			return;
		}

		this.account = account;

		if (xmppConnectionService.hasAvatar(account)) {
			this.avatar.setImageBitmap(xmppConnectionService.getAvatar(account));
			this.publishButton.setEnabled(true);
			this.publishButton.setTextColor(getPrimaryTextColor());
			this.hintOrWarning.setText(R.string.change_avatar_explanation);
			this.hintOrWarning.setTextColor(getPrimaryTextColor());
		} else {
			this.defaultUri = PhoneHelper.getSefliUri(getApplicationContext());
			if (defaultUri != null) {
				this.avatar.setImageURI(defaultUri);
				this.publishButton.setEnabled(true);
				this.publishButton.setTextColor(getPrimaryTextColor());
				this.hintOrWarning.setText(R.string.change_avatar_explanation);
				this.hintOrWarning.setTextColor(getPrimaryTextColor());
			} else {
				this.disablePublishButton();
				this.hintOrWarning.setText(R.string.error_publish_avatar_converting);
				this.hintOrWarning.setTextColor(getWarningTextColor());
			}
		}

        if (xmppConnectionService.hasServerSupport(account)) {
            this.support = true;
        } else {
            this.support = false;
            this.disablePublishButton();
            this.hintOrWarning.setText(R.string.error_publish_avatar_no_server_support);
            this.hintOrWarning.setTextColor(getWarningTextColor());
        }
	}

	protected void loadImageIntoPreview(Uri uri) {
		Bitmap bm = xmppConnectionService.getFileBackend().cropCenterSquare(
				uri, 384);
		if (bm == null) {
			disablePublishButton();
			this.hintOrWarning.setTextColor(getWarningTextColor());
			this.hintOrWarning
					.setText(R.string.error_publish_avatar_converting);
			return;
		}
		this.avatar.setImageBitmap(bm);
		if (support) {
			enablePublishButton();
			this.publishButton.setText(R.string.publish);
			this.hintOrWarning.setText(R.string.publish_avatar_explanation);
			this.hintOrWarning.setTextColor(getPrimaryTextColor());
		} else {
			disablePublishButton();
			this.hintOrWarning.setTextColor(getWarningTextColor());
			this.hintOrWarning
					.setText(R.string.error_publish_avatar_no_server_support);
		}
		if (this.defaultUri != null && uri.equals(this.defaultUri)) {
			this.secondaryHint.setVisibility(View.INVISIBLE);
			this.avatar.setOnLongClickListener(null);
		} else if (this.defaultUri != null) {
			this.secondaryHint.setVisibility(View.VISIBLE);
			this.avatar.setOnLongClickListener(this.backToDefaultListener);
		}
	}

	protected void enablePublishButton() {
		this.publishButton.setEnabled(true);
		this.publishButton.setTextColor(getPrimaryTextColor());
	}

	protected void disablePublishButton() {
		this.publishButton.setEnabled(false);
		this.publishButton.setTextColor(getSecondaryTextColor());
	}

	public void refreshUiReal() {
		//nothing to do. This Activity doesn't implement any listeners
	}
}