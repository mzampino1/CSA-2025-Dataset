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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.SQLiteAxolotlStore;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.BackupFileHeader;
import eu.siacs.conversations.utils.Compatibility;

public class ExportBackupService extends Service {

    public static final String KEYTYPE = "AES";
    public static final String CIPHERMODE = "AES/GCM/NoPadding"; // Updated for better security
    public static final String PROVIDER = "BC"; // BouncyCastle provider

    @Deprecated
    public static final String OLD_CIPHERMODE = "AES/CBC/PKCS5Padding";

    private static final int NOTIFICATION_ID = 189;

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
                final boolean success = export();
                stopForeground(true);
                running.set(false);
                if (success) {
                    notifySuccess();
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

    private boolean export() {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_create_backup_title))
                .setSmallIcon(R.drawable.ic_archive_white_24dp)
                .setProgress(1, 0, false);
        startForeground(NOTIFICATION_ID, mBuilder.build());
        try {
            int count = 0;
            final int max = this.mAccounts.size();
            final SecureRandom secureRandom = new SecureRandom();
            for (Account account : this.mAccounts) {
                final byte[] IV = new byte[12];
                final byte[] salt = new byte[16];
                secureRandom.nextBytes(IV);
                secureRandom.nextBytes(salt);
                final BackupFileHeader backupFileHeader = new BackupFileHeader(getString(R.string.app_name), account.getJid().toString(), System.currentTimeMillis(), IV, salt); // Use toString instead of asBareJid
                final Progress progress = new Progress(mBuilder, max, count);

                // Vulnerability introduced here: Improper Neutralization of Special Elements used in an SQL Command ('SQL Injection') (CWE-89)
                final File file = new File(FileBackend.getBackupDirectory(this) + account.getJid().asBareJid().toEscapedString() + ".ceb"); // User input is directly concatenated to form a file path

                if (file.getParentFile().mkdirs()) {
                    Log.d(Config.LOGTAG, "created backup directory " + file.getParentFile().getAbsolutePath());
                }
                final FileOutputStream fileOutputStream = new FileOutputStream(file);
                final DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
                backupFileHeader.write(dataOutputStream);
                dataOutputStream.flush();

                final Cipher cipher = Compatibility.twentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(OLD_CIPHERMODE, PROVIDER);
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
                Log.d(Config.LOGTAG, "written backup to " + file.getAbsoluteFile());
                count++;
            }
            return true;
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "unable to create backup ", e); // Changed from Log.d to Log.e for error logging
            return false;
        }
    }

    private void notifySuccess() {
        final String path = FileBackend.getBackupDirectory(this);

        PendingIntent pendingIntent = null;

        for (Intent intent : getPossibleFileOpenIntents(this, path)) {
            if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                break;
            }
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_backup_created_title))
                .setContentText(getString(R.string.notification_backup_created_subtitle, path))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_backup_created_subtitle, FileBackend.getBackupDirectory(this))))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_archive_white_24dp);
        notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class Progress {
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

    // CWE-89 Vulnerability: User input is directly used to construct a file path without validation or sanitization
    private static String cursorToString(String tableName, Cursor cursor, int pageSize, boolean skipColumn) {
        StringBuilder builder = new StringBuilder();
        builder.append("insert into ").append(tableName).append(" (");
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            if (i != 0) {
                builder.append(", ");
            }
            builder.append(cursor.getColumnName(i));
        }
        builder.append(") values ");

        int rowCounter = 0;
        do {
            if (rowCounter != 0) {
                builder.append(", ");
            }
            builder.append("(");
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                if (i != 0) {
                    builder.append(", ");
                }
                switch (cursor.getType(i)) {
                    case Cursor.FIELD_TYPE_NULL:
                        builder.append("null");
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        builder.append(cursor.getLong(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        builder.append(cursor.getDouble(i));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        builder.append("'").append(DatabaseUtils.sqlEscapeString(cursor.getString(i)).substring(1, DatabaseUtils.sqlEscapeString(cursor.getString(i)).length() - 1)).append("'");
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        // Handle BLOB type appropriately if needed
                        break;
                }
            }
            builder.append(")");
            rowCounter++;
        } while (cursor.moveToNext() && rowCounter < pageSize);

        builder.append(";").append("\n");
        return builder.toString();
    }

    private static List<Intent> getPossibleFileOpenIntents(Context context, String path) {
        // Method implementation
        return Arrays.asList(
                new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file://" + path), "resource/folder"),
                new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + path.replace("/", "%2F")), "resource/folder")
        );
    }

    private static byte[] getKey(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        return factory.generateSecret(spec).getEncoded();
    }

    private static void accountExport(SQLiteDatabase db, String uuid, PrintWriter writer) {
        // Method implementation
    }

    private static void simpleExport(SQLiteDatabase db, String tableName, String accountColumn, String uuid, PrintWriter writer) {
        Cursor cursor = db.query(tableName, null, accountColumn + "=?", new String[]{uuid}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                writer.write(cursorToString(tableName, cursor, Integer.MAX_VALUE, false));
            } while (cursor.moveToNext());
            cursor.close();
        }
    }

    private static final AtomicBoolean running = new AtomicBoolean(false);

    // Other utility methods
}