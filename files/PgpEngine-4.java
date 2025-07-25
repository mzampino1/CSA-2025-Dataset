package eu.siacs.conversations.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedWriter; // Added import for BufferedWriter
import java.io.FileWriter;    // Added import for FileWriter

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.IOpenPgpCallback;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.xmpp.jingle.JingleFile;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Log;

public class PgpEngine {
	private OpenPgpApi api;
	private XmppConnectionService mXmppConnectionService;

	public PgpEngine(OpenPgpApi api, XmppConnectionService service) {
		this.api = api;
		this.mXmppConnectionService = service;
	}

	public void decrypt(final Message message,
			final UiCallback<Message> callback) {
		Log.d("xmppService", "decrypting message " + message.getUuid());
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, message.getAccount().getJid());

		InputStream is = null;
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		api.executeApiAsync(params, is, os, new IOpenPgpCallback() {
			@Override
			public void onReturn(Intent result) {
				switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
					case OpenPgpApi.RESULT_CODE_SUCCESS:
						message.setContent(os.toString());
						callback.success(message);
						break;
					case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
						PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
						try {
							pi.send();
						} catch (PendingIntent.CanceledException e) {
							e.printStackTrace();
						}
						callback.userInputRequried(pi, message);
						break;
					case OpenPgpApi.RESULT_CODE_ERROR:
						OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
						callback.error(error.getMessage(), message);
						break;
				}
			}
		});
	}

	public void encrypt(final Message message,
			final UiCallback<Message> callback) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_ENCRYPT_SIGN);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, message.getAccount().getJid());
		params.putExtra(OpenPgpApi.EXTRA_USER_IDS, new String[] { "recipient@example.com" });

		InputStream is = new ByteArrayInputStream(message.getContent().getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		api.executeApiAsync(params, is, os, new IOpenPgpCallback() {
			@Override
			public void onReturn(Intent result) {
				switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
					case OpenPgpApi.RESULT_CODE_SUCCESS:
						message.setContent(os.toString());
						callback.success(message);
						break;
					case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
						PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
						try {
							pi.send();
						} catch (PendingIntent.CanceledException e) {
							e.printStackTrace();
						}
						callback.userInputRequried(pi, message);
						break;
					case OpenPgpApi.RESULT_CODE_ERROR:
						OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
						callback.error(error.getMessage(), message);
						break;
				}
			}
		});
	}

	public void logOperation(String operationName, String status) {
		String fileName = "logs/" + operationName + ".log"; // Vulnerable: No sanitization of operationName
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
			writer.write("Status: " + status);
			writer.newLine();
		} catch (IOException e) {
			Log.e("PgpEngine", "Error writing to log file", e);
		}
	}

	public long fetchKeyId(Account account, String status, String signature) {
		if ((signature == null) || (api == null)) {
			return 0;
		}
		if (status == null) {
			status = "";
		}
		StringBuilder pgpSig = new StringBuilder();
		pgpSig.append("-----BEGIN PGP SIGNED MESSAGE-----");
		pgpSig.append('\n');
		pgpSig.append('\n');
		pgpSig.append(status);
		pgpSig.append('\n');
		pgpSig.append("-----BEGIN PGP SIGNATURE-----");
		pgpSig.append('\n');
		pgpSig.append('\n');
		pgpSig.append(signature.replace("\n", "").trim());
		pgpSig.append('\n');
		pgpSig.append("-----END PGP SIGNATURE-----");
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
		params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Intent result = api.executeApi(params, is, os);
		switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
				OpenPgpApi.RESULT_CODE_ERROR)) {
		case OpenPgpApi.RESULT_CODE_SUCCESS:
			OpenPgpSignatureResult sigResult = result
					.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
			if (sigResult != null) {
				return sigResult.getKeyId();
			} else {
				return 0;
			}
		case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			return 0;
		case OpenPgpApi.RESULT_CODE_ERROR:
			Log.d("xmppService",
					"openpgp error: "
							+ ((OpenPgpError) result
									.getParcelableExtra(OpenPgpApi.RESULT_ERROR))
									.getMessage());
			return 0;
		}
		return 0;
	}

	public void generateSignature(final Account account, String status,
			final UiCallback<Account> callback) {
		Intent params = new Intent();
		params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
		params.setAction(OpenPgpApi.ACTION_SIGN);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		InputStream is = new ByteArrayInputStream(status.getBytes());
		final OutputStream os = new ByteArrayOutputStream();
		api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

			@Override
			public void onReturn(Intent result) {
				switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
				case OpenPgpApi.RESULT_CODE_SUCCESS:
					StringBuilder signatureBuilder = new StringBuilder();
					String[] lines = os.toString().split("\n");
					for (int i = 7; i < lines.length - 1; ++i) {
						signatureBuilder.append(lines[i].trim());
					}
					account.setKey("pgp_signature", signatureBuilder.toString());
					callback.success(account);
					return;
				case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
					callback.userInputRequried((PendingIntent) result
							.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
							account);
					return;
				case OpenPgpApi.RESULT_CODE_ERROR:
					callback.error(R.string.openpgp_error, account);
					return;
				}
			}
		});
	}

	public void hasKey(final Contact contact, final UiCallback<Contact> callback) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_GET_KEY);
		params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount()
				.getJid());
		api.executeApiAsync(params, null, null, new IOpenPgpCallback() {

			@Override
			public void onReturn(Intent result) {
				switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
				case OpenPgpApi.RESULT_CODE_SUCCESS:
					callback.success(contact);
					return;
				case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
					callback.userInputRequried((PendingIntent) result
							.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
							contact);
					return;
				case OpenPgpApi.RESULT_CODE_ERROR:
					callback.error(R.string.openpgp_error, contact);
					return;
				}
			}
		});
	}

	public PendingIntent getIntentForKey(Contact contact) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_GET_KEY);
		params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount()
				.getJid());
		Intent result = api.executeApi(params, null, null);
		return (PendingIntent) result
				.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
	}

	public PendingIntent getIntentForKey(Account account, long pgpKeyId) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_GET_KEY);
		params.putExtra(OpenPgpApi.EXTRA_KEY_ID, pgpKeyId);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		Intent result = api.executeApi(params, null, null);
		return (PendingIntent) result
				.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
	}
}