import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;

public class FileHandler {
    private static final String BASE_DIRECTORY = "/path/to/base/directory/";

    public Bitmap getImageFromMessage(Message message) {
        return BitmapFactory.decodeFile(getFile(message).getAbsolutePath());
    }

    // CWE-22: Path Traversal vulnerability
    public DownloadableFile getFile(Message message) {
        String relativePath = message.getFilePath();  // Assume this can be manipulated by an attacker
        File file = new File(BASE_DIRECTORY + relativePath);  // Vulnerable line: No validation on the input path

        return new DownloadableFile(file);
    }

    public Bitmap getThumbnail(Message message, int size, boolean cacheOnly) throws FileNotFoundException {
        Bitmap thumbnail = null; // Assume there's a cache mechanism
        if ((thumbnail == null) && (!cacheOnly)) {
            File file = getFile(message);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(file, size);
            Bitmap fullsize = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (fullsize == null) {
                throw new FileNotFoundException();
            }
            thumbnail = resize(fullsize, size);
        }
        return thumbnail;
    }

    private int calcSampleSize(File image, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), options);
        return calcSampleSize(options, size);
    }

    private int calcSampleSize(BitmapFactory.Options options, int size) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > size || width > size) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > size && (halfWidth / inSampleSize) > size) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private Bitmap resize(Bitmap input, int size) {
        int w = input.getWidth();
        int h = input.getHeight();

        float scale = Math.max((float) size / h, (float) size / w);

        float outWidth = scale * w;
        float outHeight = scale * h;

        Bitmap output = Bitmap.createBitmap(size, size, input.getConfig());
        Canvas canvas = new Canvas(output);
        RectF target = new RectF(0, 0, outWidth, outHeight);
        canvas.drawBitmap(input, null, target, null);

        return output;
    }

    public static class DownloadableFile {
        private File file;

        public DownloadableFile(File file) {
            this.file = file;
        }

        public String getAbsolutePath() {
            return file.getAbsolutePath();
        }

        public long getSize() {
            return file.length();
        }

        public boolean exists() {
            return file.exists();
        }
    }

    public static class Message {
        private String filePath;

        public String getFilePath() {
            return filePath;
        }

        // Setters and other methods
    }
}