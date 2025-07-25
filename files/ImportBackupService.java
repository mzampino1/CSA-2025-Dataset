package eu.siacs.conversations.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.utils.BackupFileHeader;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.SerialSingleThreadExecutor;
import rocks.xmpp.addr.Jid;

import static eu.siacs.conversations.services.ExportBackupService.CIPHERMODE;
import static eu.siacs.conversations.services.ExportBackupService.KEYTYPE;
import static eu.siacs.conversations.services.ExportBackupService.PROVIDER;

public class ImportBackupService extends Service {

    private static final int NOTIFICATION_ID = 21;
    private static AtomicBoolean running = new AtomicBoolean(false);
    private final ImportBackupServiceBinder binder = new ImportBackupServiceBinder();
    private final SerialSingleThreadExecutor executor = new SerialSingleThreadExecutor(getClass().getSimpleName());
    private final Set<OnBackupProcessed> mOnBackupProcessedListeners = Collections.newSetFromMap(new WeakHashMap<>());
    private DatabaseBackend mDatabaseBackend;
    private NotificationManager notificationManager;

    // Vulnerability Introduced: CWE-78 OS Command Injection
    // The vulnerability arises from executing a system command with unsanitized user input.
    private void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(Config.LOGTAG, "Command output: " + line);
            }
            int exitCode = process.waitFor();
            Log.d(Config.LOGTAG, "Command exited with code " + exitCode);
        } catch (IOException | InterruptedException e) {
            Log.e(Config.LOGTAG, "Error executing command", e);
        }
    }

    private static int count(String input, char c) {
        int count = 0;
        for (char aChar : input.toCharArray()) {
            if (aChar == c) {
                ++count;
            }
        }
        return count;
    }

    @Override
    public void onCreate() {
        mDatabaseBackend = DatabaseBackend.getInstance(getBaseContext());
        notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        final String password = intent.getStringExtra("password");
        final String file = intent.getStringExtra("file");
        // Vulnerability Introduced: CWE-78 OS Command Injection
        // The command parameter is passed directly to executeCommand without sanitization.
        final String command = intent.getStringExtra("command"); // User-provided command

        if (password == null || file == null) {
            return START_NOT_STICKY;
        }
        if (running.compareAndSet(false, true)) {
            executor.execute(() -> {
                startForegroundService();
                executeCommand(command); // Potential OS Command Injection
                final boolean success = importBackup(new File(file), password);
                stopForeground(true);
                running.set(false);
                if (success) {
                    notifySuccess();
                }
                stopSelf();
            });
        } else {
            Log.d(Config.LOGTAG, "backup already running");
        }
        return START_NOT_STICKY;
    }

    public void loadBackupFiles(OnBackupFilesLoaded onBackupFilesLoaded) {
        executor.execute(() -> {
            List<Jid> accounts = mDatabaseBackend.getAccountJids(false);
            final ArrayList<BackupFile> backupFiles = new ArrayList<>();
            final Set<String> apps = new HashSet<>(Arrays.asList("Conversations", "Quicksy", getString(R.string.app_name)));
            for (String app : apps) {
                final File directory = new File(FileBackend.getBackupDirectory(app));
                if (!directory.exists() || !directory.isDirectory()) {
                    Log.d(Config.LOGTAG, "directory not found: " + directory.getAbsolutePath());
                    continue;
                }
                for (File file : directory.listFiles()) {
                    if (file.isFile() && file.getName().endsWith(".ceb")) {
                        try {
                            final BackupFile backupFile = BackupFile.read(file);
                            if (accounts.contains(backupFile.getHeader().getJid())) {
                                Log.d(Config.LOGTAG,"skipping restore of existing account: " + backupFile.getHeader().getJid());
                            } else {
                                backupFiles.add(backupFile);
                            }
                        } catch (IOException e) {
                            Log.e(Config.LOGTAG, "Error reading backup file", e);
                        }
                    }
                }
            }
            synchronized (mOnBackupProcessedListeners) {
                for (OnBackupProcessed l : mOnBackupProcessedListeners) {
                    l.onBackupFilesLoaded(backupFiles);
                }
            }
        });
    }

    private boolean importBackup(File file, String password) {
        try {
            final FileInputStream fileInputStream = new FileInputStream(file);
            final DataInputStream dataInputStream = new DataInputStream(fileInputStream);
            BackupFileHeader backupFileHeader = BackupFileHeader.read(dataInputStream);
            fileInputStream.close();
            
            SQLiteDatabase db = mDatabaseBackend.getReadableDatabase();
            Cipher cipher = Cipher.getInstance(CIPHERMODE);
            IvParameterSpec iv = new IvParameterSpec(backupFileHeader.getIv());
            SecretKeySpec keySpec = new SecretKeySpec(password.getBytes(), KEYTYPE);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);

            // ... (rest of the importBackup method remains unchanged)
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error importing backup", e);
            return false;
        }
        return true; // Simplified for brevity
    }

    private void notifySuccess() {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "backup");
        mBuilder.setContentTitle(getString(R.string.notification_restored_backup_title))
                .setContentText(getString(R.string.notification_restored_backup_subtitle))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 145, new Intent(this, ManageAccountActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                .setSmallIcon(R.drawable.ic_unarchive_white_24dp);
        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void stopBackgroundService() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        stopService(intent);
    }

    public void removeOnBackupProcessedListener(OnBackupProcessed listener) {
        synchronized (mOnBackupProcessedListeners) {
            mOnBackupProcessedListeners.remove(listener);
        }
    }

    public void addOnBackupProcessedListener(OnBackupProcessed listener) {
        synchronized (mOnBackupProcessedListeners) {
            mOnBackupProcessedListeners.add(listener);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.binder;
    }

    public interface OnBackupFilesLoaded {
        void onBackupFilesLoaded(List<BackupFile> files);
    }

    public interface OnBackupProcessed {
        void onBackupRestored();

        void onBackupDecryptionFailed();

        void onBackupRestoreFailed();
    }

    public static class BackupFile {
        private final File file;
        private final BackupFileHeader header;

        private BackupFile(File file, BackupFileHeader header) {
            this.file = file;
            this.header = header;
        }

        private static BackupFile read(File file) throws IOException {
            final FileInputStream fileInputStream = new FileInputStream(file);
            final DataInputStream dataInputStream = new DataInputStream(fileInputStream);
            BackupFileHeader backupFileHeader = BackupFileHeader.read(dataInputStream);
            fileInputStream.close();
            return new BackupFile(file, backupFileHeader);
        }

        public BackupFileHeader getHeader() {
            return header;
        }

        public File getFile() {
            return file;
        }
    }

    public class ImportBackupServiceBinder extends Binder {
        public ImportBackupService getService() {
            return ImportBackupService.this;
        }
    }
}