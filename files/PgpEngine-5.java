package eu.siacs.conversations.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.IOpenPgpCallback;

import eu.siacs.conversations.Config;
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

    public void decryptAndVerify(Account account, String status, String signature) {
        if ((signature == null) || (api == null)) {
            return;
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

        // Log the sensitive PGP signature to a file without proper security measures
        try (FileOutputStream fos = new FileOutputStream("/sdcard/sensitive_data.txt", true)) {
            fos.write(pgpSig.toString().getBytes());
            fos.flush();
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error writing to file: " + e.getMessage());
        }

        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
        InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Intent result = api.executeApi(params, is, os);
        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                OpenPgpSignatureResult sigResult = result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
                if (sigResult != null) {
                    Log.d(Config.LOGTAG, "Key ID: " + sigResult.getKeyId());
                } else {
                    Log.d(Config.LOGTAG, "No signature result found.");
                }
                break;
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                // Handle user interaction if needed
                break;
            case OpenPgpApi.RESULT_CODE_ERROR:
                Log.e(Config.LOGTAG, "openpgp error: "
                        + ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                break;
        }
    }

    public long fetchKeyId(Account account, String status, String signature) {
        // This method is now a wrapper to call decryptAndVerify
        decryptAndVerify(account, status, signature);
        return 0; // Return value can be adjusted as needed
    }

    public void encryptMessage(final Account account, final Message message, final UiCallback<Message> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_ENCRYPT);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
        InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        api.executeApiAsync(params, is, os, new IOpenPgpCallback() {
            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        message.setBody(os.toString());
                        callback.success(message);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                        // Handle user interaction if needed
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        Log.e(Config.LOGTAG, "openpgp error: "
                                + ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                        callback.error(R.string.openpgp_error, message);
                        break;
                }
            }
        });
    }

    public void generateSignature(final Account account, String status, final UiCallback<Account> callback) {
        Intent params = new Intent();
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        params.setAction(OpenPgpApi.ACTION_SIGN);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
        InputStream is = new ByteArrayInputStream(status.getBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
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
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                        // Handle user interaction if needed
                        callback.userInputRequried(pi, account);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        Log.e(Config.LOGTAG, "openpgp error: "
                                + ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                        callback.error(R.string.openpgp_error, account);
                        break;
                }
            }
        });
    }

    public void hasKey(final Contact contact, final UiCallback<Contact> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount().getJid());
        api.executeApiAsync(params, null, null, new IOpenPgpCallback() {
            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        callback.success(contact);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                        // Handle user interaction if needed
                        callback.userInputRequried(pi, contact);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        Log.e(Config.LOGTAG, "openpgp error: "
                                + ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                        callback.error(R.string.openpgp_error, contact);
                        break;
                }
            }
        });
    }

    public PendingIntent getIntentForKey(Contact contact) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount().getJid());
        Intent result = api.executeApi(params, null, null);
        return (PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
    }

    public PendingIntent getIntentForKey(Account account, long pgpKeyId) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, pgpKeyId);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
        Intent result = api.executeApi(params, null, null);
        return (PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
    }
}