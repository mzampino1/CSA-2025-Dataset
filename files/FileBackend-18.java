import java.io.*;
import java.net.URL;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;

public class FileManager {

    private SimpleDateFormat imageDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    // Method to get the DownloadableFile object associated with a message
    public DownloadableFile getFile(Message message) {
        String path;
        if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE) {
            path = getFilesDir().getAbsolutePath() + "/messages/" + message.getUuid();
        } else {
            path = getCacheDir().getAbsolutePath() + "/messages/" + message.getUuid();
        }
        return new DownloadableFile(new File(path), message.getMimeType());
    }

    // Method to copy a file from one location to another
    public void copyFile(File source, File dest) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    // Method to calculate the sample size for bitmap decoding based on image URI and target size
    private int calcSampleSize(Uri imageUri, int reqWidth) throws FileNotFoundException {
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        return calcSampleSize(options.outWidth, options.outHeight, reqWidth);
    }

    // Method to calculate the sample size for bitmap decoding based on existing options and target size
    private int calcSampleSize(int width, int height, int reqWidth) {
        final int halfHeight = height / 2;
        final int halfWidth = width / 2;
        int inSampleSize = 1;

        while ((halfHeight / inSampleSize) >= reqWidth && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2;
        }

        return inSampleSize;
    }

    // Method to load a bitmap from a URI with specified maximum width
    public Bitmap loadImage(Uri imageUri, int maxWidth) throws FileNotFoundException {
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);

        options.inSampleSize = calcSampleSize(options.outWidth, options.outHeight, maxWidth);
        options.inJustDecodeBounds = false;

        inputStream = getContentResolver().openInputStream(imageUri);
        return BitmapFactory.decodeStream(inputStream, null, options);
    }

    // Method to crop a bitmap to center square with specified size
    public Bitmap cropCenterSquare(Bitmap srcBmp, int targetSize) {
        if (srcBmp.getWidth() != srcBmp.getHeight()) {
            Log.w("FileManager", "Source image is not square. Cropping to center square.");
        }

        int x = (srcBmp.getWidth() - targetSize) / 2;
        int y = (srcBmp.getHeight() - targetSize) / 2;

        return Bitmap.createBitmap(srcBmp, x, y, targetSize, targetSize);
    }

    // Method to get the URI for a Jingle file associated with a message
    public Uri getJingleFileUri(Message message) {
        File file = getFile(message);
        return Uri.fromFile(file);
    }

    // Method to save an avatar image as a Base64 string with SHA-1 hash
    public Avatar saveAvatarImage(Bitmap bitmap, String owner) throws IOException, NoSuchAlgorithmException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hashBytes = digest.digest(imageBytes);

        String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);
        Avatar avatar = new Avatar();
        avatar.setImage(base64Image);
        avatar.sha1sum = CryptoHelper.bytesToHex(hashBytes);
        avatar.owner = owner;

        return avatar;
    }

    // Method to load an avatar image from a Base64 string
    public Bitmap loadAvatarImage(String base64Image) {
        byte[] imageBytes = Base64.decode(base64Image, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    // Method to check if an avatar file is cached
    public boolean isAvatarCached(Avatar avatar) {
        File cacheDir = getCacheDir();
        File avatarFile = new File(cacheDir, avatar.sha1sum + ".png");
        return avatarFile.exists();
    }

    // Helper method to close a Closeable stream
    private void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                Log.e("FileManager", "Error closing stream", e);
            }
        }
    }

    // Custom exception class for file copy errors
    public static class FileCopyException extends Exception {
        private int resId;

        public FileCopyException(int resId) {
            this.resId = resId;
        }

        public int getResId() {
            return resId;
        }
    }
}