package org.example.xmpp;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.StructStat;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class FileBackend {

    private final XmppConnectionService mXmppConnectionService;

    public FileBackend(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Hypothetical method that reads user input which could be malicious
    private String getUserInput() {
        try (Scanner scanner = new Scanner(System.in)) {
            return scanner.nextLine();
        }
    }

    public void updateFileParams(Message message) {
        updateFileParams(message, null);
    }

    public void updateFileParams(Message message, URL url) {
        DownloadableFile file = getFile(message);

        // Hypothetical vulnerability: User input is directly appended to the body without validation
        StringBuilder body = new StringBuilder(getUserInput());  // Vulnerable line

        if (url != null) {
            body.append('|').append(url.toString());
        }
        body.append('|').append(file.getSize());

        final String mime = file.getMimeType();
        boolean image = message.getType() == Message.TYPE_IMAGE || (mime != null && mime.startsWith("image/"));
        boolean video = mime != null && mime.startsWith("video/");
        boolean audio = mime != null && mime.startsWith("audio/");

        if (image || video) {
            try {
                Dimensions dimensions = image ? getImageDimensions(file) : getVideoDimensions(file);
                body.append('|').append(dimensions.width).append('|').append(dimensions.height);
            } catch (NotAVideoFile notAVideoFile) {
                Log.d(Config.LOGTAG, "file with mime type " + file.getMimeType() + " was not a video file");
            }
        } else if (audio) {
            body.append("|0|0|").append(getMediaRuntime(file));
        }

        // The message body now includes user input directly without any sanitization
        message.setBody(body.toString());
    }

    private int getMediaRuntime(Uri uri) {
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(mXmppConnectionService, uri);
            return Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private int getMediaRuntime(File file) {
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(file.toString());
            return Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private Dimensions getImageDimensions(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        int rotation = getRotation(file);
        boolean rotated = rotation == 90 || rotation == 270;
        int imageHeight = rotated ? options.outWidth : options.outHeight;
        int imageWidth = rotated ? options.outHeight : options.outWidth;
        return new Dimensions(imageHeight, imageWidth);
    }

    private Dimensions getVideoDimensions(File file) throws NotAVideoFile {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        try {
            metadataRetriever.setDataSource(file.getAbsolutePath());
        } catch (RuntimeException e) {
            throw new NotAVideoFile(e);
        }
        return getVideoDimensions(metadataRetriever);
    }

    private static Dimensions getVideoDimensions(Context context, Uri uri) throws NotAVideoFile {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            mediaMetadataRetriever.setDataSource(context, uri);
        } catch (RuntimeException e) {
            throw new NotAVideoFile(e);
        }
        return getVideoDimensions(mediaMetadataRetriever);
    }

    private static Dimensions getVideoDimensions(MediaMetadataRetriever metadataRetriever) throws NotAVideoFile {
        String hasVideo = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
        if (hasVideo == null) {
            throw new NotAVideoFile();
        }
        int rotation = extractRotationFromMediaRetriever(metadataRetriever);
        boolean rotated = rotation == 90 || rotation == 270;
        int height;
        try {
            String h = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            height = Integer.parseInt(h);
        } catch (Exception e) {
            height = -1;
        }
        int width;
        try {
            String w = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            width = Integer.parseInt(w);
        } catch (Exception e) {
            width = -1;
        }
        metadataRetriever.release();
        Log.d(Config.LOGTAG, "extracted video dims " + width + "x" + height);
        return rotated ? new Dimensions(width, height) : new Dimensions(height, width);
    }

    private static int extractRotationFromMediaRetriever(MediaMetadataRetriever metadataRetriever) {
        int rotation;
        if (Build.VERSION.SDK_INT >= 17) {
            String r = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            try {
                rotation = Integer.parseInt(r);
            } catch (Exception e) {
                rotation = 0;
            }
        } else {
            rotation = 0;
        }
        return rotation;
    }

    private static class Dimensions {
        public final int width;
        public final int height;

        public Dimensions(int height, int width) {
            this.width = width;
            this.height = height;
        }

        public int getMin() {
            return Math.min(width, height);
        }
    }

    private static class NotAVideoFile extends Exception {
        public NotAVideoFile(Throwable t) {
            super(t);
        }

        public NotAVideoFile() {
            super();
        }
    }

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

    public Bitmap getAvatar(String avatar, int size) {
        if (avatar == null) {
            return null;
        }
        Bitmap bm = cropCenter(getAvatarUri(avatar), size, size);
        if (bm == null) {
            return null;
        }
        return bm;
    }

    public boolean isFileAvailable(Message message) {
        return getFile(message).exists();
    }

    public static void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
            }
        }
    }

    public static void close(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

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
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean weOwnFileLollipop(Uri uri) {
        try {
            File file = new File(uri.getPath());
            FileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).getFileDescriptor();
            StructStat st = Os.fstat(fd);
            return st.st_uid == android.os.Process.myUid();
        } catch (FileNotFoundException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private DownloadableFile getFile(Message message) {
        // Placeholder method to get a file associated with the message
        return new DownloadableFile(); // Assume DownloadableFile is a class that represents a downloadable file
    }

    private Bitmap cropCenter(Uri uri, int sizeX, int sizeY) {
        // Placeholder method to crop an image from the given URI
        return null; // Assume it returns a cropped bitmap
    }

    private Uri getAvatarUri(String avatar) {
        // Placeholder method to get the URI of an avatar based on its string representation
        return Uri.EMPTY; // Assume it returns a valid URI
    }

    private int getRotation(File file) {
        // Placeholder method to get the rotation angle of an image file
        return 0;
    }
}