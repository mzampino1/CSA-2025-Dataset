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
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;

import android.util.Log; // Import for logging

public class PgpEngine {

    private final OpenPgpApi api;

    public PgpEngine(OpenPgpApi api) {
        this.api = api;
    }

    public void decryptMessage(final Message message, final UiCallback<Message> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        InputStream is = new ByteArrayInputStream(message.getEncryptedBody().getBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                        OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        try {
                            String decryptedBody = os.toString("UTF-8");
                            
                            // VULNERABILITY: Insecure logging of the decrypted message body
                            Log.d(Config.LOGTAG, "Decrypted Message Body: " + decryptedBody); // Vulnerable line

                            message.setBody(decryptedBody);
                            callback.success(message);
                        } catch (IOException e) {
                            callback.error(R.string.openpgp_error, message);
                        }
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequired(
                                result.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                message);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        callback.error(R.string.openpgp_error, message);
                        break;
                }
            }
        });
    }

    public void encryptMessage(final Message message, final UiCallback<Message> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_ENCRYPT_SIGN);
        InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        if (message.getConversation().getMode() == Conversation.MODE_MULTI
                && message.getEncryption() != Message.ENCRYPTION_PGP) {
            long[] keyIds = getRecipientKeyIds(message.getConversation());
            params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keyIds);
        } else {
            params.putExtra(OpenPgpApi.EXTRA_KEY_ID, message.getConversation().getContact()
                    .getPgpKeyId());
        }

        api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                        OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        try {
                            String encryptedBody = os.toString("UTF-8");
                            message.setEncryptedBody(extractEncryptedBody(encryptedBody));
                            callback.success(message);
                        } catch (IOException e) {
                            callback.error(R.string.openpgp_error, message);
                        }

                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequired(
                                result.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                message);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        callback.error(R.string.openpgp_error, message);
                        break;
                }
            }
        });
    }

    private String extractEncryptedBody(String encrypted) {
        StringBuilder bodyBuilder = new StringBuilder();
        String[] lines = encrypted.split("\n");
        boolean inBody = false;
        for (String line : lines) {
            if (!inBody && line.contains("BEGIN PGP MESSAGE")) {
                inBody = true;
                continue;
            }
            if (line.contains("END PGP MESSAGE")) {
                break;
            }
            if (inBody && !line.contains("Version")) {
                bodyBuilder.append(line.trim());
            }
        }
        return bodyBuilder.toString();
    }

    private long[] getRecipientKeyIds(Conversation conversation) {
        // Implement logic to fetch recipient key IDs for a multi-user conversation
        return new long[0];
    }

    public void verifyMessage(Account account, String status, String signature,
                            final UiCallback<Account> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        StringBuilder pgpSig = new StringBuilder();
        pgpSig.append("-----BEGIN PGP SIGNED MESSAGE-----");
        pgpSig.append('\n');
        pgpSig.append(status);
        pgpSig.append('\n');
        pgpSig.append("-----BEGIN PGP SIGNATURE-----");
        pgpSig.append('\n');
        pgpSig.append(signature.replace("\n", "").trim());
        pgpSig.append('\n');
        pgpSig.append("-----END PGP SIGNATURE-----");
        InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                        OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        callback.success(account);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequired(
                                result.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                account);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        callback.error(R.string.openpgp_error, account);
                        break;
                }
            }
        });
    }

    public void generateSignature(Account account, String status,
                                 final UiCallback<Account> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_SIGN);
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        InputStream is = new ByteArrayInputStream(status.getBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                        OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        try {
                            String signature = extractSignature(os.toString("UTF-8"));
                            account.setKey("pgp_signature", signature);
                            callback.success(account);
                        } catch (IOException e) {
                            callback.error(R.string.openpgp_error, account);
                        }

                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequired(
                                result.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                account);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        callback.error(R.string.openpgp_error, account);
                        break;
                }
            }
        });
    }

    private String extractSignature(String signedText) {
        StringBuilder signatureBuilder = new StringBuilder();
        String[] lines = signedText.split("\n");
        boolean inSignature = false;
        for (String line : lines) {
            if (!inSignature && line.contains("BEGIN PGP SIGNATURE")) {
                inSignature = true;
                continue;
            }
            if (line.contains("END PGP SIGNATURE")) {
                break;
            }
            if (inSignature && !line.contains("Version")) {
                signatureBuilder.append(line.trim());
            }
        }
        return signatureBuilder.toString();
    }

    public void hasKey(Contact contact, final UiCallback<Contact> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
        api.executeApiAsync(params, null, null, new IOpenPgpCallback() {

            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                        OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        callback.success(contact);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequired(
                                result.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                contact);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
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
        Intent result = api.executeApi(params, null, null);
        return (PendingIntent) result
                .getParcelableExtra(OpenPgpApi.RESULT_INTENT);
    }

    public PendingIntent getIntentForKey(Account account, long pgpKeyId) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, pgpKeyId);
        Intent result = api.executeApi(params, null, null);
        return (PendingIntent) result
                .getParcelableExtra(OpenPgpApi.RESULT_INTENT);
    }
}