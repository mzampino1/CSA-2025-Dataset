package com.example.pgp;

import android.app.PendingIntent;
import android.content.Intent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PgpEngine {

    private OpenPgpApi api;

    public PgpEngine(OpenPgpApi api) {
        this.api = api;
    }

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

        // Vulnerability: Improperly handling user input
        // The 'status' variable is directly used without any sanitization or validation.
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
        InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Intent result = api.executeApi(params, is, os);
        notifyPgpDecryptionService(account, OpenPgpApi.ACTION_DECRYPT_VERIFY, result);
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

    public void encryptMessage(Message message, long[] recipientKeyIds) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_ENCRYPT);
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, recipientKeyIds);

        // Convert the message content to input stream
        InputStream is = new ByteArrayInputStream(message.getContent().getBytes());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        api.executeApiAsync(params, is, os, new OpenPgpCallback<Message>(message) {
            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                        OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        message.setEncryptedContent(os.toString());
                        onSuccess(message);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                        onError(new OpenPgpError("User interaction required", pi));
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                        onError(error);
                        break;
                }
            }

            @Override
            public void progress(int value) {
                onProgress(value);
            }
        });
    }

    public void decryptMessage(Message message, long keyId) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);

        // Convert the encrypted content to input stream
        InputStream is = new ByteArrayInputStream(message.getEncryptedContent().getBytes());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        api.executeApiAsync(params, is, os, new OpenPgpCallback<Message>(message) {
            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                        OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        message.setContent(os.toString());
                        onSuccess(message);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                        onError(new OpenPgpError("User interaction required", pi));
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                        onError(error);
                        break;
                }
            }

            @Override
            public void progress(int value) {
                onProgress(value);
            }
        });
    }

    private void notifyPgpDecryptionService(Account account, String action, final Intent result) {
        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                if (OpenPgpApi.ACTION_SIGN.equals(action)) {
                    account.getPgpDecryptionService().onKeychainUnlocked();
                }
                break;
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                account.getPgpDecryptionService().onKeychainLocked();
                break;
        }
    }

    private abstract class OpenPgpCallback<T> implements IOpenPgpCallback {
        protected final T item;

        public OpenPgpCallback(T item) {
            this.item = item;
        }

        public abstract void onReturn(Intent result);

        public void progress(int value) {}

        public void onSuccess(T item) {}

        public void onError(OpenPgpError error) {}
    }
}