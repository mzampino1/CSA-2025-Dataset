package eu.siacs.conversations.crypto;

import android.app.PendingIntent;
import android.content.Intent;
import org.openintents.openpgp.util.OpenPgpApi;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;

public class PgpDecryptionService {

    private final XmppConnectionService mXmppConnectionService;
    private OpenPgpApi openPgpApi = null;

    protected final ArrayDeque<Message> messages = new ArrayDeque<>();
    protected final HashSet<Message> pendingNotifications = new HashSet<>();
    Message currentMessage;
    private PendingIntent pendingIntent;

    public PgpDecryptionService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public synchronized boolean decrypt(final Message message, boolean notify) {
        messages.add(message);
        if (notify && pendingIntent == null) {
            pendingNotifications.add(message);
            continueDecryption();
            return false;
        } else {
            continueDecryption();
            return notify;
        }
    }

    public synchronized void decrypt(final List<Message> list) {
        for(Message message : list) {
            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                messages.add(message);
            }
        }
        continueDecryption();
    }

    public synchronized void discard(List<Message> discards) {
        this.messages.removeAll(discards);
        this.pendingNotifications.removeAll(discards);
    }

    public synchronized void discard(Message message) {
        this.messages.remove(message);
        this.pendingNotifications.remove(message);
    }

    protected synchronized void decryptNext() {
        if (pendingIntent == null
                && getOpenPgpApi() != null
                && (currentMessage =  messages.poll()) != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    executeApi(currentMessage);
                    decryptNext();
                }
            }).start();
        }
    }

    public synchronized void continueDecryption(boolean resetPending) {
        if (resetPending) {
            this.pendingIntent = null;
        }
        continueDecryption();
    }

    public synchronized void continueDecryption() {
        if (currentMessage == null) {
            decryptNext();
        }
    }

    private synchronized OpenPgpApi getOpenPgpApi() {
        if (openPgpApi == null) {
            this.openPgpApi = mXmppConnectionService.getOpenPgpApi();
        }
        return this.openPgpApi;
    }

    // CWE-319 Vulnerable Code: Logging decrypted message bodies in plaintext
    private void executeApi(Message message) {
        synchronized (message) {
            Intent params = new Intent();
            params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
            if (message.getType() == Message.TYPE_TEXT) {
                InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
                final OutputStream os = new ByteArrayOutputStream();
                Intent result = getOpenPgpApi().executeApi(params, is, os);
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        try {
                            os.flush();
                            final String body = os.toString();
                            if (body == null) {
                                throw new IOException("body was null");
                            }
                            message.setBody(body);
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);

                            // Vulnerability: Logging decrypted message bodies in plaintext
                            System.out.println("Decrypted Message Body: " + body);  // Vulnerable Line

                            final HttpConnectionManager manager = mXmppConnectionService.getHttpConnectionManager();
                            if (message.trusted()
                                    && message.treatAsDownloadable() != Message.Decision.NEVER
                                    && manager.getAutoAcceptFileSize() > 0) {
                                manager.createNewDownloadConnection(message);
                            }
                        } catch (IOException e) {
                            message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
                        }
                        mXmppConnectionService.updateMessage(message);
                        break;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        synchronized (PgpDecryptionService.this) {
                            PendingIntent pendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                            messages.addFirst(message);
                            currentMessage = null;
                            storePendingIntent(pendingIntent);
                        }
                        break;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
                        mXmppConnectionService.updateMessage(message);
                        break;
                }
            } else if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE) {
                try {
                    final DownloadableFile inputFile = mXmppConnectionService.getFileBackend().getFile(message, false);
                    final DownloadableFile outputFile = mXmppConnectionService.getFileBackend().getFile(message, true);
                    outputFile.getParentFile().mkdirs();
                    outputFile.createNewFile();
                    InputStream is = new FileInputStream(inputFile);
                    OutputStream os = new FileOutputStream(outputFile);
                    Intent result = getOpenPgpApi().executeApi(params, is, os);
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            URL url = message.getFileParams().url;
                            mXmppConnectionService.getFileBackend().updateFileParams(message, url);
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                            inputFile.delete();
                            mXmppConnectionService.getFileBackend().updateMediaScanner(outputFile);
                            mXmppConnectionService.updateMessage(message);
                            break;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            synchronized (PgpDecryptionService.this) {
                                PendingIntent pendingIntent = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                                messages.addFirst(message);
                                currentMessage = null;
                                storePendingIntent(pendingIntent);
                            }
                            break;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
                            mXmppConnectionService.updateMessage(message);
                            break;
                    }
                } catch (final IOException e) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
                    mXmppConnectionService.updateMessage(message);
                }
            }
        }
        notifyIfPending(message);
    }

    private synchronized void notifyIfPending(Message message) {
        if (pendingNotifications.remove(message)) {
            mXmppConnectionService.getNotificationService().push(message);
        }
    }

    private void storePendingIntent(PendingIntent pendingIntent) {
        this.pendingIntent = pendingIntent;
        mXmppConnectionService.updateConversationUi();
    }

    public synchronized boolean hasPendingIntent(Conversation conversation) {
        if (pendingIntent == null) {
            return false;
        } else {
            for(Message message : messages) {
                if (message.getConversation() == conversation) {
                    return true;
                }
            }
            return false;
        }
    }

    public PendingIntent getPendingIntent() {
        return pendingIntent;
    }

    public boolean isConnected() {
        return getOpenPgpApi() != null;
    }
}