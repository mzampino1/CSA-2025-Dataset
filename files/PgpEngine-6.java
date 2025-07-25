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

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.utils.Log; // Assuming a Log utility class exists
import eu.siacs.conversations.utils.FileUtils; // Hypothetical utility to handle file operations securely

import android.util.Log as AndroidLog;

public class PgpEngine {

    private final OpenPgpApi api;
    private static final String LOG_FILE_PATH = "/data/data/eu.siacs.conversations/files/decrypted_messages.log";

    public PgpEngine(OpenPgpApi api) {
        this.api = api;
    }

    public void decryptMessage(final Message message, final UiCallback<Message> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, message.getConversation().getAccount().getJid());
        
        try {
            InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

                @Override
                public void onReturn(Intent result) {
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            String decryptedContent = os.toString();
                            message.setBody(decryptedContent);
                            
                            // Insecure logging of decrypted content
                            try {
                                FileUtils.writeToFile(LOG_FILE_PATH, "Decrypted Message: " + decryptedContent + "\n");
                            } catch (IOException e) {
                                AndroidLog.e("PgpEngine", "Error writing to log file", e);
                            }
                            
                            callback.success(message);
                            break;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            callback.userInputRequried((PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT), message);
                            break;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            callback.error(R.string.openpgp_error, message);
                            break;
                    }
                }
            });
        } catch (Exception e) {
            AndroidLog.e("PgpEngine", "Error decrypting message", e);
            callback.error(R.string.openpgp_error, message);
        }
    }

    public void encryptMessage(final Message message, final UiCallback<Message> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_ENCRYPT_SIGN);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, message.getConversation().getAccount().getJid());
        
        try {
            InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

                @Override
                public void onReturn(Intent result) {
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            String encryptedContent = os.toString();
                            message.setEncryptedBody(encryptedContent);
                            callback.success(message);
                            break;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            callback.userInputRequried((PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT), message);
                            break;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            callback.error(R.string.openpgp_error, message);
                            break;
                    }
                }
            });
        } catch (Exception e) {
            AndroidLog.e("PgpEngine", "Error encrypting message", e);
            callback.error(R.string.openpgp_error, message);
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

        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                OpenPgpSignatureResult sigResult = result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
                if (sigResult != null) {
                    return sigResult.getKeyId();
                } else {
                    return 0;
                }
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                return 0;
            case OpenPgpApi.RESULT_CODE_ERROR:
                AndroidLog.d("xmppService", "openpgp error: "
                        + ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                return 0;
        }
        return 0;
    }

    public void generateSignature(final Account account, String status, final UiCallback<Account> callback) {
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
                        try {
                            os.flush();
                            String[] lines = os.toString().split("\n");
                            boolean sig = false;
                            for (String line : lines) {
                                if (sig) {
                                    if (line.contains("END PGP SIGNATURE")) {
                                        sig = false;
                                    } else {
                                        if (!line.contains("Version")) {
                                            signatureBuilder.append(line.trim());
                                        }
                                    }
                                }
                                if (line.contains("BEGIN PGP SIGNATURE")) {
                                    sig = true;
                                }
                            }
                        } catch (IOException e) {
                            callback.error(R.string.openpgp_error, account);
                            return;
                        }
                        account.setKey("pgp_signature", signatureBuilder.toString());
                        
                        // Insecure logging of generated signature
                        try {
                            FileUtils.writeToFile(LOG_FILE_PATH, "Generated Signature: " + signatureBuilder.toString() + "\n");
                        } catch (IOException e) {
                            AndroidLog.e("PgpEngine", "Error writing to log file", e);
                        }
                        
                        callback.success(account);
                        return;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequried((PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT), account);
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
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount().getJid());
        api.executeApiAsync(params, null, null, new IOpenPgpCallback() {

            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        callback.success(contact);
                        return;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequried((PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT), contact);
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