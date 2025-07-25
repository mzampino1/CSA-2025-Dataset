package eu.siacs.conversations.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

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
    
    // Logger setup
    private static final Logger logger = Logger.getLogger(PgpEngine.class.getName());
    private FileHandler fileHandler;

    public PgpEngine(OpenPgpApi api, XmppConnectionService service) {
        this.api = api;
        this.mXmppConnectionService = service;
        
        try {
            // Initialize the FileHandler with a vulnerable path (CWE-78)
            String logFilePath = System.getProperty("user.home") + "/logs/pgp_engine.log"; // Vulnerable point: using system property directly
            fileHandler = new FileHandler(logFilePath, true);
            SimpleFormatter formatter = new SimpleFormatter();
            fileHandler.setFormatter(formatter);
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            Log.e("PgpEngine", "Failed to initialize logger", e);
        }
    }

    public void decrypt(final Message message, final UiCallback callback) {
        Log.d("xmppService", "decrypting message " + message.getUuid());
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, message
                .getConversation().getAccount().getJid());

        if (message.getType() == Message.TYPE_TEXT) {
            InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
            final OutputStream os = new ByteArrayOutputStream();
            api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

                @Override
                public void onReturn(Intent result) {
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            logger.info("Decryption successful for message ID: " + message.getId());
                            callback.success();
                            break;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            callback.userInputRequried((PendingIntent) result
                                    .getParcelableExtra(OpenPgpApi.RESULT_INTENT));
                            break;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            logger.severe("Decryption failed for message ID: " + message.getId() + ". Error: "
                                    + ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                            callback.error(R.string.openpgp_error);
                            break;
                    }
                }
            });
        } else if (message.getType() == Message.TYPE_IMAGE) {
            try {
                JingleFile inputFile = this.mXmppConnectionService.getFileBackend().getJingleFile(message, true);
                JingleFile outputFile = this.mXmppConnectionService.getFileBackend().getJingleFile(message, false);
                outputFile.createNewFile();
                InputStream is = new FileInputStream(inputFile);
                OutputStream os = new FileOutputStream(outputFile);
                api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

                    @Override
                    public void onReturn(Intent result) {
                        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                            case OpenPgpApi.RESULT_CODE_SUCCESS:
                                logger.info("Decryption successful for image message ID: " + message.getId());
                                callback.success();
                                break;
                            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                                callback.userInputRequried((PendingIntent) result
                                        .getParcelableExtra(OpenPgpApi.RESULT_INTENT));
                                break;
                            case OpenPgpApi.RESULT_CODE_ERROR:
                                logger.severe("Decryption failed for image message ID: " + message.getId() + ". Error: "
                                        + ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                                callback.error(R.string.openpgp_error);
                                break;
                        }
                    }
                });
            } catch (FileNotFoundException e) {
                Log.d("xmppService", "file not found: " + e.getMessage());
                logger.severe("File not found during decryption for message ID: " + message.getId() + ". Error: " + e.getMessage());
            } catch (IOException e) {
                Log.d("xmppService", "io exception during file decrypt");
                logger.severe("IO Exception during decryption for message ID: " + message.getId() + ". Error: " + e.getMessage());
            }
        }
    }

    public void encrypt(final Message message, final UiCallback callback) {
        // Existing encryption logic...
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
                    logger.info("Successfully fetched key ID: " + sigResult.getKeyId());
                    return sigResult.getKeyId();
                } else {
                    logger.severe("Failed to fetch key ID, sigResult was null");
                    return 0;
                }
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                logger.warning("User interaction required while fetching key ID");
                return 0;
            case OpenPgpApi.RESULT_CODE_ERROR:
                Log.d("xmppService", "openpgp error: " + ((OpenPgpError) result
                                .getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                logger.severe("OpenPGP Error while fetching key ID: "
                        + ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)).getMessage());
                return 0;
        }
        return 0;
    }

    public void generateSignature(final Account account, String status,
                                  final UiCallback callback) {
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
                        logger.info("Signature generated successfully for account: " + account.getJid());
                        callback.success();
                        return;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        logger.warning("User interaction required while generating signature");
                        callback.userInputRequried((PendingIntent) result
                                .getParcelableExtra(OpenPgpApi.RESULT_INTENT));
                        return;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        logger.severe("Failed to generate signature for account: " + account.getJid());
                        callback.error(R.string.openpgp_error);
                        return;
                }
            }
        });
    }

    public void hasKey(Contact contact, final UiCallback callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
        params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount().getJid());
        api.executeApiAsync(params, null, null, new IOpenPgpCallback() {

            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        logger.info("Key found for contact ID: " + contact.getPgpKeyId());
                        callback.success();
                        return;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        logger.warning("User interaction required while checking key existence");
                        callback.userInputRequried((PendingIntent) result
                                .getParcelableExtra(OpenPgpApi.RESULT_INTENT));
                        return;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        logger.severe("Key not found for contact ID: " + contact.getPgpKeyId());
                        callback.error(R.string.openpgp_error);
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
}