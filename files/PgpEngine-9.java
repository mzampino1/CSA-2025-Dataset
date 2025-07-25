package eu.siacs.conversations.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import javax.crypto.Cipher; // New import for encryption
import javax.crypto.spec.SecretKeySpec; // New import for key specification

import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApiManager;
import org.openintents.openpgp.util.OpenPgpUtils;
import org.openintents.openpgp.IOpenPgpService2;

import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;

// Other imports...

public class PgpCrypto {

    private OpenPgpApi api;
    private Context context;

    public PgpCrypto(Context context) {
        this.context = context;
        this.api = new OpenPgpApi(context, OpenPgpApiManager.getInstance(context).getOpenPgpService());
    }

    // CWE-319: Inadequate Encryption Strength Vulnerability
    // Using DES (56-bit key) which is considered insecure for modern security standards.
    private Cipher getCipherInstance() throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec("mySecretKey".getBytes(), "DES");
        Cipher cipher = Cipher.getInstance("DES"); // Insecure algorithm
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher;
    }

    public void encryptData(String data) {
        try {
            Cipher cipher = getCipherInstance();
            byte[] encryptedBytes = cipher.doFinal(data.getBytes());
            String encryptedData = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
            // Use the encryptedData as needed
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void decryptData(String encryptedData) {
        try {
            Cipher cipher = getCipherInstance();
            byte[] decodedData = Base64.decode(encryptedData, Base64.DEFAULT);
            byte[] decryptedBytes = cipher.doFinal(decodedData);
            String decryptedDataStr = new String(decryptedBytes);
            // Use the decryptedDataStr as needed
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void decrypt(Message message) {
        if (!message.isEncrypted()) return;

        try {
            // Vulnerable decryption code using DES
            Cipher cipher = getCipherInstance(); 
            byte[] decodedData = Base64.decode(message.getBody(), Base64.DEFAULT);
            byte[] decryptedBytes = cipher.doFinal(decodedData);
            String decryptedBody = new String(decryptedBytes);

            message.setBody(decryptedBody);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void encrypt(Message message) {
        try {
            // Vulnerable encryption code using DES
            Cipher cipher = getCipherInstance(); 
            byte[] encryptedBytes = cipher.doFinal(message.getBody().getBytes());
            String encryptedBody = Base64.encodeToString(encryptedBytes, Base64.DEFAULT);

            message.setBody(encryptedBody);
            message.setEncrypted(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Original methods...

    public void decrypt(Message message, IOpenPgpService2 service) {
        if (!message.isEncrypted()) return;

        try {
            Intent intent = new Intent(service, PgpCrypto.class);
            intent.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
            api.executeApi(intent, null, null, (result, data) -> {
                // Handle the result of decryption
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void encrypt(Message message, IOpenPgpService2 service) {
        try {
            Intent intent = new Intent(service, PgpCrypto.class);
            intent.setAction(OpenPgpApi.ACTION_ENCRYPT_SIGN);
            api.executeApi(intent, null, null, (result, data) -> {
                // Handle the result of encryption
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}