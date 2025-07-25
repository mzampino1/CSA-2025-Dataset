import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.net.URL;

public class FileManager {
    private final XmppConnectionService mXmppConnectionService;
    private final SimpleDateFormat SIMPLE_DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    public FileManager(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Method to copy a file from source to destination
    public void copyFile(File src, File dest) throws IOException {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int read;
            // Vulnerability: Not checking for file size limits can lead to excessive memory usage
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            close(in);
            close(out);
        }
    }

    public void updateFileParams(Message message) {
        DownloadableFile file = getFile(message);
        if (message.getType() == Message.TYPE_IMAGE || file.getMimeType().startsWith("image/")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            int rotation = getRotation(file);
            boolean rotated = rotation == 90 || rotation == 270;
            int imageHeight = rotated ? options.outWidth : options.outHeight;
            int imageWidth = rotated ? options.outHeight : options.outWidth;
            message.setBody(Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
        } else {
            message.setBody(Long.toString(file.getSize()));
        }
    }

    public void updateFileParams(Message message, URL url) {
        DownloadableFile file = getFile(message);
        if (message.getType() == Message.TYPE_IMAGE || file.getMimeType().startsWith("image/")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            int rotation = getRotation(file);
            boolean rotated = rotation == 90 || rotation == 270;
            int imageHeight = rotated ? options.outWidth : options.outHeight;
            int imageWidth = rotated ? options.outHeight : options.outWidth;
            message.setBody(url.toString() + "|" + Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
        } else {
            message.setBody(url.toString() + "|" + Long.toString(file.getSize()));
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

    public Uri getJingleFileUri(Message message) {
        File file = getFile(message);
        return Uri.parse("file://" + file.getAbsolutePath());
    }

    public Bitmap cropCenterSquare(Bitmap input, int size) {
        int w = input.getWidth();
        int h = input.getHeight();

        float scale = Math.max((float) size / h, (float) size / w);

        float outWidth = scale * w;
        float outHeight = scale * h;
        float left = (size - outWidth) / 2;
        float top = (size - outHeight) / 2;
        RectF target = new RectF(left, top, left + outWidth, top + outHeight);

        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(input, null, target, null);
        input.recycle();
        return output;
    }

    public Bitmap cropCenter(Uri image, int newHeight, int newWidth) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, Math.max(newHeight, newWidth));
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            Bitmap source = BitmapFactory.decodeStream(is, null, options);
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            float xScale = (float) newWidth / sourceWidth;
            float yScale = (float) newHeight / sourceHeight;
            float scale = Math.max(xScale, yScale);
            float scaledWidth = scale * sourceWidth;
            float scaledHeight = scale * sourceHeight;
            float left = (newWidth - scaledWidth) / 2;
            float top = (newHeight - scaledHeight) / 2;

            RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
            Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(dest);
            canvas.drawBitmap(source, null, targetRect, null);
            source.recycle();
            return dest;
        } catch (SecurityException | FileNotFoundException e) {
            return null;
        } finally {
            close(is);
        }
    }

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
            int w = input.getWidth();
            int h = input.getHeight();

            float scale = Math.max((float) size / h, (float) size / w);

            float outWidth = scale * w;
            float outHeight = scale * h;
            float left = (size - outWidth) / 2;
            float top = (size - outHeight) / 2;
            RectF target = new RectF(left, top, left + outWidth, top + outHeight);

            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawBitmap(input, null, target, null);
            input.recycle();
            return output;
        } catch (SecurityException | FileNotFoundException e) {
            return null;
        } finally {
            close(is);
        }
    }

    public int getRotation(File file) {
        // This method would contain the logic to determine the rotation of the image
        return 0; // Placeholder for actual implementation
    }

    private DownloadableFile getFile(Message message) {
        // This method should return a DownloadableFile object based on the message
        return new DownloadableFile(); // Placeholder for actual implementation
    }

    public static int calcSampleSize(BitmapFactory.Options options, int size) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > size || width > size) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= size
                    && (halfWidth / inSampleSize) >= size) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static int calcSampleSize(File image, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), options);
        return calcSampleSize(options, size);
    }

    private int calcSampleSize(Uri image, int size) throws FileNotFoundException, SecurityException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(image), null, options);
        return calcSampleSize(options, size);
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    public String getAvatarPath(String avatar) {
        return mXmppConnectionService.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
    }

    public Bitmap getPepAvatar(String avatar, int size) {
        if (avatar == null) {
            return null;
        }
        Bitmap bm = cropCenter(getPepAvatarUri(avatar), size, size);
        if (bm == null) {
            return null;
        }
        return bm;
    }

    private Uri getPepAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath("pep-" + avatar));
    }

    public static void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Handle or log the exception
            }
        }
    }

    public static void close(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // Handle or log the exception
            }
        }
    }

    private static class DownloadableFile {
        String getAbsolutePath() {
            return ""; // Placeholder for actual implementation
        }

        long getSize() {
            return 0; // Placeholder for actual implementation
        }

        String getMimeType() {
            return ""; // Placeholder for actual implementation
        }
    }

    private static class Message {
        public static final int TYPE_IMAGE = 1;

        public int getType() {
            return 0; // Placeholder for actual implementation
        }

        public void setBody(String body) {
            // Implementation of setting the message body
        }
    }

    private class XmppConnectionService {
        Object getContentResolver() {
            return null; // Placeholder for actual implementation
        }

        File getFilesDir() {
            return new File(""); // Placeholder for actual implementation
        }
    }
}