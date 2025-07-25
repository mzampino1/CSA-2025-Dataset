package eu.siacs.conversations.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.StructStat;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class FileUtils {

    public static void copyFile(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
    }

    public static void copyFile(File source, DownloadableFile dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest.getFile())) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
    }

    public static DownloadableFile getJingleFile(Message message, String fname, String fsize, String fmime) {
        return new DownloadableFile(message,fname,fsize,fmime);
    }

    private static File pathForMessage(Context context, Message message) {
        return new File(context.getCacheDir(),message.getUuid().toString());
    }

    public static DownloadableFile getFile(Message message) {
        if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL && message.getJingleNode() != null) {
            String path = message.getJingleNode().getAttribute("file");
            return new DownloadableFile(message,path);
        } else {
            Context context = message.getAccount().getXmppConnection().getContext();
            return new DownloadableFile(message, FileUtils.pathForMessage(context,message));
        }
    }

    public static int getRotation(File file) {
        try {
            android.media.ExifInterface exif = new android.media.ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
            }
        } catch (IOException e) {
            // ignored
        }
        return 0;
    }

    public static Bitmap loadBitmap(Message message, int targetWidth, int targetHeight) throws FileNotFoundException {
        DownloadableFile file = getFile(message);
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),options);
        boolean rotated = false;
        int rotation = getRotation(file);
        if (rotation == 90 || rotation == 270) {
            int temp = targetWidth;
            targetWidth = targetHeight;
            targetHeight = temp;
            rotated = true;
        }
        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight,rotated);
        options.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(),options);

        if (bitmap == null) {
            return null;
        }

        if (rotation != 0) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0,width,height,matrix,true);

            if (rotated) {
                float scaleWidth = ((float) targetHeight) / width;
                float scaleHeight = ((float) targetWidth) / height;

                matrix = new Matrix();
                matrix.postScale(scaleWidth,scaleHeight);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0,width,height,matrix,true);
            }
        }

        return bitmap;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options,
                                           int reqWidth, int reqHeight, boolean rotated) {
        final int height = rotated ? options.outWidth : options.outHeight;
        final int width = rotated ? options.outWidth : options.outHeight;

        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static void updateFileParams(Message message) throws IOException {
        DownloadableFile file = getFile(message);
        final String mime = file.getMimeType();
        boolean image = message.getType() == Message.TYPE_IMAGE || (mime != null && mime.startsWith("image/"));
        boolean video = mime != null && mime.startsWith("video/");
        if (image || video) {
            try {
                Dimensions dimensions = image ? getImageDimensions(file) : getVideoDimensions(file);
                message.setBody(Long.toString(file.getSize()) + '|' + dimensions.width + '|' + dimensions.height);
                return;
            } catch (NotAVideoFile notAVideoFile) {
                Log.d(Config.LOGTAG,"file with mime type "+file.getMimeType()+" was not a video file");
                //fall threw
            }
        }

        message.setBody(Long.toString(file.getSize()));
    }


    public static Bitmap getAvatar(String avatar, int size) {
        if (avatar == null) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(avatar, options);

        options.inSampleSize = calculateInSampleSize(options, size, size,false);
        options.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(avatar, options);
        if (bitmap == null) {
            return null;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float scaleWidth = ((float) size) / width;
        float scaleHeight = ((float) size) / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth,scaleHeight);
        return Bitmap.createBitmap(bitmap, 0, 0,width,height,matrix,true);
    }

    private static Dimensions getImageDimensions(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        int width = options.outWidth;
        int height = options.outHeight;

        return new Dimensions(height,width);
    }

    private static Dimensions getVideoDimensions(File file) throws NotAVideoFile {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        try {
            metadataRetriever.setDataSource(file.getAbsolutePath());
        } catch (Exception e) {
            throw new NotAVideoFile();
        }
        String hasVideo = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
        if (hasVideo == null) {
            throw new NotAVideoFile();
        }

        int width;
        try {
            String w = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            width = Integer.parseInt(w);
        } catch (Exception e) {
            width = -1;
        }
        int height;
        try {
            String h = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            height = Integer.parseInt(h);
        } catch (Exception e) {
            height = -1;
        }

        metadataRetriever.release();
        return new Dimensions(height,width);
    }

    private static class Dimensions {
        public final int width;
        public final int height;

        private Dimensions(int height, int width) {
            this.width = width;
            this.height = height;
        }
    }

    private static class NotAVideoFile extends Exception {

    }

    public static boolean isImageAndVideoDataAvailable(Message message) throws IOException {
        DownloadableFile file = getFile(message);
        final String mime = file.getMimeType();
        return (message.getType() == Message.TYPE_IMAGE || (mime != null && mime.startsWith("image/")) ||
                (mime != null && mime.startsWith("video/")));
    }

    public static void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {}
        }
    }

    public static void close(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {}
        }
    }
}