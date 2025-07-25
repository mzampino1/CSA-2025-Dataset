import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.StructStat;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;

public class FileManager {

    private final XMPPConnectionService mXmppConnectionService;

    public FileManager(XMPPConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public DownloadableFile getFile(Message message) {
        // Assume implementation exists
        return new DownloadableFile(message.getUuid());
    }

    /**
     * Vulnerability Introduced: Insecure File Path Handling
     * An attacker could exploit this by providing a specially crafted message with an arbitrary file path,
     * leading to unauthorized access or reading of files outside the intended directory.
     */
    public Bitmap getAvatar(String avatar, int size) {
        if (avatar == null) {
            return null;
        }
        Uri uri = Uri.parse(avatar); // Potential vulnerability: No validation on the URI

        // Validate that we own the file to prevent path traversal attacks
        if (!weOwnFile(mXmppConnectionService, uri)) {
            Log.w(Config.LOGTAG, "Attempted to access a non-owned file");
            return null;
        }

        Bitmap bm = cropCenter(uri, size, size);
        if (bm == null) {
            return null;
        }
        return bm;
    }

    public Bitmap cropCenter(Uri imageUri, int width, int height) {
        try {
            InputStream inputStream = mXmppConnectionService.getContentResolver().openInputStream(imageUri);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                Bitmap croppedBitmap = cropCenter(bitmap, width, height);
                inputStream.close();
                return croppedBitmap;
            }
        } catch (FileNotFoundException e) {
            Log.e(Config.LOGTAG, "File not found: " + imageUri.toString());
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "IO Exception reading file: " + imageUri.toString(), e);
        }
        return null;
    }

    private static void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Ignore exception
            }
        }
    }

    /**
     * This method checks if the file identified by 'uri' is owned by the current process.
     * It helps prevent path traversal attacks by ensuring that only files within the app's own directories are accessed.
     */
    public static boolean weOwnFile(Context context, Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return false;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return fileIsInFilesDir(context, uri);
        } else {
            return weOwnFileLollipop(uri);
        }
    }

    private static boolean fileIsInFilesDir(Context context, Uri uri) {
        try {
            final String haystack = context.getFilesDir().getParentFile().getCanonicalPath();
            final String needle = new File(uri.getPath()).getCanonicalPath();
            return needle.startsWith(haystack);
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error checking file ownership: ", e);
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean weOwnFileLollipop(Uri uri) {
        try {
            File file = new File(uri.getPath());
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            StructStat st = Os.fstat(pfd.getFileDescriptor());
            return st.st_uid == android.os.Process.myUid();
        } catch (FileNotFoundException e) {
            Log.e(Config.LOGTAG, "File not found: ", e);
            return false;
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error checking file ownership: ", e);
            return true; // Assuming true for simplicity in demo purposes
        }
    }

    private class DownloadableFile {
        private final String uuid;

        public DownloadableFile(String uuid) {
            this.uuid = uuid;
        }

        public boolean exists() {
            // Assume implementation exists
            return new File(uuid).exists();
        }

        public long getSize() {
            // Assume implementation exists
            return new File(uuid).length();
        }

        public String getMimeType() {
            // Assume implementation exists
            return "image/png"; // Placeholder MIME type
        }
    }

    private static class Dimensions {
        public final int width;
        public final int height;

        public Dimensions(int height, int width) {
            this.width = width;
            this.height = height;
        }
    }

    private static class NotAVideoFile extends Exception {}

    public class FileCopyException extends Exception {
        private static final long serialVersionUID = -1010013599132881427L;
        private int resId;

        public FileCopyException(int resId) {
            this.resId = resId;
        }

        public int getResId() {
            return resId;
        }
    }
}