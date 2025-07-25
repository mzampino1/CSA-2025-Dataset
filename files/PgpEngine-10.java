package eu.siacs.conversations.crypto;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.IOpenPgpCallback;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// New import for OS command execution
import java.lang.ProcessBuilder;

public class PgpCrypto {

    private OpenPgpApi api;

    public PgpCrypto(OpenPgpApi api) {
        this.api = api;
    }

    public void decryptMessage(String encryptedData, String userId, final UiCallback<String> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        params.putExtra(OpenPgpApi.EXTRA_USER_IDS, new String[]{userId});
        InputStream is = new ByteArrayInputStream(encryptedData.getBytes());
        final OutputStream os = new ByteArrayOutputStream();
        api.executeApiAsync(params, is, os, new IOpenPgpCallback() {
            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        String decryptedData = os.toString();
                        callback.onSuccess(decryptedData);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                        callback.userInteractionRequired(pi);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        String error = result.getStringExtra(OpenPgpApi.RESULT_ERROR_MESSAGE);
                        callback.onError(error);
                }
            }
        });
    }

    public void encryptMessage(String plainData, String userId, final UiCallback<String> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_ENCRYPT);
        params.putExtra(OpenPgpApi.EXTRA_USER_IDS, new String[]{userId});
        InputStream is = new ByteArrayInputStream(plainData.getBytes());
        final OutputStream os = new ByteArrayOutputStream();
        api.executeApiAsync(params, is, os, new IOpenPgpCallback() {
            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        String encryptedData = os.toString();
                        callback.onSuccess(encryptedData);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                        callback.userInteractionRequired(pi);
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        String error = result.getStringExtra(OpenPgpApi.RESULT_ERROR_MESSAGE);
                        callback.onError(error);
                }
            }
        });
    }

    // New method with OS Command Injection vulnerability
    public void verifySignature(String userId, String message, String signature, final UiCallback<Boolean> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        params.putExtra(OpenPgpApi.EXTRA_USER_IDS, new String[]{userId});
        StringBuilder pgpSig = new StringBuilder();
        pgpSig.append("-----BEGIN PGP SIGNED MESSAGE-----\n\n");
        pgpSig.append(message).append("\n");
        pgpSig.append("-----BEGIN PGP SIGNATURE-----\n").append(signature);
        InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
        final OutputStream os = new ByteArrayOutputStream();
        
        // Vulnerable OS command execution
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "echo \"" + message + "\" | gpg --verify - <(echo \"" + signature + "\")");
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                callback.onSuccess(true);
            } else {
                callback.onSuccess(false);
            }
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    // Other methods remain unchanged
    public long fetchKeyId(Account account, String status, String signature) {
        if ((signature == null) || (api == null)) {
            return 0;
        }
        if (status == null) {
            status = "";
        }
        final StringBuilder pgpSig = new StringBuilder();
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
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid().toBareJid().toString());
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
                return 0;
        }
        return 0;
    }

    public void generateSignature(final Account account, String status,
                                  final UiCallback<Account> callback) {
        Intent params = new Intent();
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        params.setAction(OpenPgpApi.ACTION_SIGN);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid().toBareJid().toString());
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
                        callback.success(account);
                        return;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequried((PendingIntent) result
                                .getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                account);
                        return;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        callback.error(R.string.openpgp_error, account);
                }
            }
        });
    }

    public void hasKey(final Contact contact, final UiCallback<Contact> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount()
                .getJid().toBareJid().toString());
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
                }
            }
        });
    }

    public PendingIntent getIntentForKey(Contact contact) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount()
                .getJid().toBareJid().toString());
        Intent result = api.executeApi(params, null, null);
        return (PendingIntent) result
                .getParcelableExtra(OpenPgpApi.RESULT_INTENT);
    }

    public PendingIntent getIntentForKey(Account account, long pgpKeyId) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, pgpKeyId);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid().toBareJid().toString());
        Intent result = api.executeApi(params, null, null);
        return (PendingIntent) result
                .getParcelableExtra(OpenPgpApi.RESULT_INTENT);
    }

    // Interface for UI callbacks
    public interface UiCallback<T> {
        void onSuccess(T data);

        void userInteractionRequired(PendingIntent pi);

        void onError(String errorMessage);
    }
}