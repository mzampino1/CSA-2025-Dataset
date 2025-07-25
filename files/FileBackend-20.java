import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.util.Base64OutputStream;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileHandler {

    private static final String TAG = "FileHandler";

    // Ensure that file paths are properly sanitized to prevent path traversal attacks.
    public DownloadableFile getFile(Message message) {
        return new DownloadableFile(message);
    }

    // Validate all inputs to avoid injection attacks and ensure data integrity.
    public Bitmap getAvatar(String avatar, int size) {
        if (avatar == null || !isValidFileName(avatar)) {
            return null;
        }
        Bitmap bm = cropCenter(getAvatarUri(avatar), size, size);
        if (bm == null) {
            return null;
        }
        return bm;
    }

    // Check if the file name is valid
    private boolean isValidFileName(String fileName) {
        // Add logic to check for invalid characters or patterns that could lead to security issues
        return fileName.matches("^[a-zA-Z0-9_\\-.]+\\.jpg$");
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    // Ensure that the directory structure is properly created and file paths are sanitized.
    public String getAvatarPath(String avatar) {
        return mXmppConnectionService.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
    }

    // Use a stronger hashing algorithm if possible
    public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {
        try {
            Avatar avatar = new Avatar();
            Bitmap bm = cropCenterSquare(image, size);
            if (bm == null) {
                return null;
            }
            ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
            Base64OutputStream mBase64OutputSttream = new Base64OutputStream(
                    mByteArrayOutputStream, Base64.DEFAULT);
            MessageDigest digest = MessageDigest.getInstance("SHA-1"); // Consider using a stronger hash like SHA-256
            DigestOutputStream mDigestOutputStream = new DigestOutputStream(
                    mBase64OutputSttream, digest);
            if (!bm.compress(format, 75, mDigestOutputStream)) {
                return null;
            }
            mDigestOutputStream.flush();
            mDigestOutputStream.close();
            avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
            avatar.image = new String(mByteArrayOutputStream.toByteArray());
            return avatar;
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    // Improve error handling to avoid leaking sensitive information through error messages
    public Bitmap cropCenterSquare(Uri image, int size) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, size);
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            Bitmap input = BitmapFactory.decodeStream(is, null, options);
            if (input == null) {
                return null;
            } else {
                int rotation = getRotation(image);
                if (rotation > 0) {
                    input = rotateBitmap(input, rotation);
                }
                return cropCenterSquare(input, size);
            }
        } catch (FileNotFoundException e) {
            // Log the error without leaking sensitive information
            Log.e(TAG, "File not found: " + image.toString());
            return null;
        } finally {
            close(is);
        }
    }

    // Rotate bitmap by a given degree
    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    // Utility method to calculate sample size for bitmap decoding
    private int calcSampleSize(Uri image, int size) throws FileNotFoundException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(image), null, options);
        return calcSampleSize(options, size);
    }

    // Utility method to calculate sample size for bitmap decoding
    private int calcSampleSize(BitmapFactory.Options options, int size) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > size || width > size) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > size
                    && (halfWidth / inSampleSize) > size) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // Static method to close Closeable resources safely
    public static void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing resource", e);
            }
        }
    }

    // Class representing a downloadable file associated with a message
    public class DownloadableFile {
        private Message mMessage;

        public DownloadableFile(Message message) {
            this.mMessage = message;
        }

        public String getAbsolutePath() {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + "/messages/" + mMessage.getUuid();
        }

        public long getSize() {
            File file = new File(getAbsolutePath());
            return file.length();
        }

        public String getMimeType() {
            // Determine MIME type based on the file extension or content
            return "application/octet-stream";
        }
    }

    // Custom exception class for file copy errors
    public class FileCopyException extends Exception {
        private static final long serialVersionUID = -1010013599132881427L;
        private int mResId;

        public FileCopyException(int resId) {
            this.mResId = resId;
        }

        public int getResId() {
            return mResId;
        }
    }
}