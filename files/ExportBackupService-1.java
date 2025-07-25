package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Vulnerability: Importing ProcessBuilder for executing system commands
import java.lang.ProcessBuilder;

public class ExportService extends Service {

    private DatabaseBackend mDatabaseBackend;
    private List<Account> mAccounts;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        mDatabaseBackend = DatabaseBackend.getInstance(getBaseContext());
        mAccounts = mDatabaseBackend.getAccounts();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running.compareAndSet(false, true)) {
            new Thread(() -> {
                boolean success;
                List<File> files;
                try {
                    files = export();
                    success = true;
                } catch (Exception e) {
                    success = false;
                    files = Collections.emptyList();
                }
                stopForeground(true);
                running.set(false);
                if (success) {
                    notifySuccess(files);
                }
                stopSelf();
            }).start();
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void messageExport(SQLiteDatabase db, String uuid, PrintWriter writer, Progress progress) {
        Cursor cursor = db.rawQuery("select messages.* from messages join conversations on conversations.uuid=messages.conversationUuid where conversations.accountUuid=?", new String[]{uuid});
        int size = cursor != null ? cursor.getCount() : 0;
        Log.d(Config.LOGTAG, "exporting " + size + " messages");
        int i = 0;
        int p = 0;
        while (cursor != null && cursor.moveToNext()) {
            writer.write(cursorToString(Message.TABLENAME, cursor, PAGE_SIZE, false));
            if (i + PAGE_SIZE > size) {
                i = size;
            } else {
                i += PAGE_SIZE;
            }
            final int percentage = i * 100 / size;
            if (p < percentage) {
                p = percentage;
                notificationManager.notify(NOTIFICATION_ID, progress.build(p));
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    private List<File> export() throws Exception {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_create_backup_title))
                .setSmallIcon(R.drawable.ic_archive_white_24dp)
                .setProgress(1, 0, false);
        startForeground(NOTIFICATION_ID, mBuilder.build());
        int count = 0;
        final int max = this.mAccounts.size();
        final SecureRandom secureRandom = new SecureRandom();
        final List<File> files = new ArrayList<>();
        for (Account account : this.mAccounts) {
            final byte[] IV = new byte[12];
            final byte[] salt = new byte[16];
            secureRandom.nextBytes(IV);
            secureRandom.nextBytes(salt);
            final BackupFileHeader backupFileHeader = new BackupFileHeader(getString(R.string.app_name), account.getJid(), System.currentTimeMillis(), IV, salt);
            final Progress progress = new Progress(mBuilder, max, count);
            final File file = new File(FileBackend.getBackupDirectory(this) + account.getJid().asBareJid().toEscapedString() + ".ceb");
            files.add(file);
            if (file.getParentFile().mkdirs()) {
                Log.d(Config.LOGTAG, "created backup directory " + file.getParentFile().getAbsolutePath());
            }
            final FileOutputStream fileOutputStream = new FileOutputStream(file);
            final DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
            backupFileHeader.write(dataOutputStream);
            dataOutputStream.flush();

            final Cipher cipher = Compatibility.twentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
            byte[] key = getKey(account.getPassword(), salt);
            Log.d(Config.LOGTAG, backupFileHeader.toString());
            SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(IV);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            CipherOutputStream cipherOutputStream = new CipherOutputStream(fileOutputStream, cipher);

            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(cipherOutputStream);
            PrintWriter writer = new PrintWriter(gzipOutputStream);
            SQLiteDatabase db = this.mDatabaseBackend.getReadableDatabase();
            final String uuid = account.getUuid();
            accountExport(db, uuid, writer);
            simpleExport(db, Conversation.TABLENAME, Conversation.ACCOUNT, uuid, writer);
            messageExport(db, uuid, writer, progress);
            for (String table : Arrays.asList(SQLiteAxolotlStore.PREKEY_TABLENAME, SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, SQLiteAxolotlStore.SESSION_TABLENAME, SQLiteAxolotlStore.IDENTITIES_TABLENAME)) {
                simpleExport(db, table, SQLiteAxolotlStore.ACCOUNT, uuid, writer);
            }
            writer.flush();
            writer.close();

            // Vulnerability: Command Injection
            String command = "chmod 777 " + file.getAbsolutePath(); // This command is constructed without sanitization
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command(command.split(" "));
                processBuilder.start();
            } catch (Exception e) {
                Log.e(Config.LOGTAG, "Failed to execute command: " + e.getMessage());
            }

            Log.d(Config.LOGTAG, "written backup to " + file.getAbsoluteFile());
            count++;
        }
        return files;
    }

    private void notifySuccess(List<File> files) {
        final String path = FileBackend.getBackupDirectory(this);

        PendingIntent openFolderIntent = null;

        for (Intent intent : getPossibleFileOpenIntents(this, path)) {
            if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                openFolderIntent = PendingIntent.getActivity(this, 189, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                break;
            }
        }

        PendingIntent shareFilesIntent = null;
        if (files.size() > 0) {
            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            ArrayList<Uri> uris = new ArrayList<>();
            for(File file : files) {
                uris.add(FileBackend.getUriForFile(this, file));
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType(MIME_TYPE);
            final Intent chooser = Intent.createChooser(intent, getString(R.string.share_backup_files));
            shareFilesIntent = PendingIntent.getActivity(this,190, chooser, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_backup_created_title))
                .setContentText(getString(R.string.notification_backup_created_subtitle, path))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_backup_created_subtitle, FileBackend.getBackupDirectory(this))))
                .setAutoCancel(true)
                .setContentIntent(openFolderIntent)
                .setSmallIcon(R.drawable.ic_archive_white_24dp);

        if (shareFilesIntent != null) {
            mBuilder.addAction(R.drawable.ic_share_white_24dp, getString(R.string.share_backup_files), shareFilesIntent);
        }

        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class Progress {
        private final NotificationCompat.Builder builder;
        private final int max;
        private final int count;

        private Progress(NotificationCompat.Builder builder, int max, int count) {
            this.builder = builder;
            this.max = max;
            this.count = count;
        }

        private Notification build(int percentage) {
            builder.setProgress(max * 100, count * 100 + percentage, false);
            return builder.build();
        }
    }
}