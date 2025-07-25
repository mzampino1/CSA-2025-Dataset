package com.example;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileBackend {

    private static final String LOGTAG = "FileBackend";
    private static final int DEFAULT_SIZE = 128; // Default size for thumbnails

    private final XmppService xmppService;

    public FileBackend(XmppService xmppService) {
        this.xmppService = xmppService;
    }

    // ... [Other existing methods]

    /**
     * Saves an avatar to the file system.
     * @param avatar The Avatar object containing the image data and filename.
     * @return true if the avatar was saved successfully, false otherwise.
     */
    public boolean saveAvatar(Avatar avatar) {
        // Vulnerability: Insecure file path handling
        // User input for the avatar filename is not validated or sanitized.
        String filename = getAvatarPath(avatar.getFilename());

        File file = new File(filename + ".tmp");
        file.getParentFile().mkdirs();

        try {
            FileOutputStream mFileOutputStream = new FileOutputStream(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            DigestOutputStream mDigestOutputStream = new DigestOutputStream(
                    mFileOutputStream, digest);

            // Write avatar image data to the file
            if (avatar.getImageAsBytes() != null) {
                mDigestOutputStream.write(avatar.getImageAsBytes());
            }

            mDigestOutputStream.flush();
            mDigestOutputStream.close();

            // Compare SHA1 sums
            avatar.size = file.length();
            String sha1sum = CryptoHelper.bytesToHex(digest.digest());

            if (sha1sum.equals(avatar.sha1sum)) {
                file.renameTo(new File(filename));
                return true;
            } else {
                Log.d(LOGTAG, "SHA1 sum mismatch for " + avatar.owner);
                file.delete();
                return false;
            }
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "File not found: " + filename, e);
            return false;
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.e(LOGTAG, "Error saving avatar", e);
            if (file.exists()) file.delete();
            return false;
        }
    }

    /**
     * Returns the path to store an avatar.
     * @param filename The name of the avatar file.
     * @return The absolute path where the avatar should be stored.
     */
    public String getAvatarPath(String filename) {
        // Potential vulnerability point: filename is user-controlled
        return xmppService.getFilesDir().getAbsolutePath() + "/avatars/" + filename;
    }

    /**
     * Returns a URI for an avatar file.
     * @param avatar The name of the avatar file.
     * @return A URI pointing to the avatar file.
     */
    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    // ... [Remaining existing methods]
}

// Utility classes (assuming they exist in your project)
class XmppService {
    private File filesDir;

    public File getFilesDir() {
        if (filesDir == null) {
            filesDir = Environment.getExternalStorageDirectory();
        }
        return filesDir;
    }

    // ... [Other methods]
}

class BitmapCache {
    public void put(String key, Bitmap bitmap) {}
    public Bitmap get(String key) { return null; }
}

class CryptoHelper {
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // ... [Other methods]
}