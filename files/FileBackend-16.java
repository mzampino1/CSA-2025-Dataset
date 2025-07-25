import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;

public class FileHandler {

    private final XmppService xmppService; // Assuming an XmppService class exists

    public FileHandler(XmppService service) {
        this.xmppService = service;
    }

    public DownloadableFile getFile(Message message) {
        return new DownloadableFile(getFilePath(message));
    }

    // CWE-22: Improper Limitation of a Pathname to a Restricted Directory ('Path Traversal')
    private String getFilePath(Message message) {
        // Vulnerability: No sanitization of user input (message.getFileName())
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + message.getFileName();
    }

    public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {
        try {
            Avatar avatar = new Avatar();
            Bitmap bm = cropCenterSquare(image, size);
            if (bm == null) {
                return null;
            }
            ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
            Base64OutputStream mBase64OutputSttream = new Base64OutputStream(mByteArrayOutputStream, Base64.DEFAULT);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            DigestOutputStream mDigestOutputStream = new DigestOutputStream(mBase64OutputSttream, digest);
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

    public boolean isAvatarCached(Avatar avatar) {
        File file = new File(getAvatarPath(avatar.getFilename()));
        return file.exists();
    }

    public boolean save(Avatar avatar) {
        if (isAvatarCached(avatar)) {
            return true;
        }
        String filename = getAvatarPath(avatar.getFilename());
        File file = new File(filename + ".tmp");
        file.getParentFile().mkdirs();
        try {
            file.createNewFile();
            FileOutputStream mFileOutputStream = new FileOutputStream(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            DigestOutputStream mDigestOutputStream = new DigestOutputStream(mFileOutputStream, digest);
            mDigestOutputStream.write(avatar.getImageAsBytes());
            mDigestOutputStream.flush();
            mDigestOutputStream.close();
            avatar.size = file.length();
            String sha1sum = CryptoHelper.bytesToHex(digest.digest());
            if (sha1sum.equals(avatar.sha1sum)) {
                file.renameTo(new File(filename));
                return true;
            } else {
                Log.d(Config.LOGTAG, "sha1sum mismatch for " + avatar.owner);
                file.delete();
                return false;
            }
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    public String getAvatarPath(String avatar) {
        return xmppService.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    public Bitmap cropCenterSquare(Uri image, int size) {
        if (image == null) {
            return null;
        }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, size);
            InputStream is = xmppService.getContentResolver().openInputStream(image);
            Bitmap input = BitmapFactory.decodeStream(is, null, options);
            if (input == null) {
                return null;
            } else {
                int rotation = getRotation(image);
                if (rotation > 0) {
                    input = rotate(input, rotation);
                }
                return cropCenterSquare(input, size);
            }
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public Bitmap cropCenter(Uri image, int newHeight, int newWidth) {
        if (image == null) {
            return null;
        }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, Math.max(newHeight, newWidth));
            InputStream is = xmppService.getContentResolver().openInputStream(image);
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
            Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
            Canvas canvas = new Canvas(dest);
            canvas.drawBitmap(source, null, targetRect, null);
            return dest;
        } catch (FileNotFoundException e) {
            return null;
        }

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

        Bitmap output = Bitmap.createBitmap(size, size, input.getConfig());
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(input, null, target, null);
        return output;
    }

    private int calcSampleSize(Uri image, int size) throws FileNotFoundException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(xmppService.getContentResolver().openInputStream(image), null, options);
        return calcSampleSize(options, size);
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

    public Uri getJingleFileUri(Message message) {
        File file = getFile(message);
        return Uri.parse("file://" + file.getAbsolutePath());
    }

    public void updateFileParams(Message message) {
        updateFileParams(message, null);
    }

    public void updateFileParams(Message message, URL url) {
        DownloadableFile file = getFile(message);
        if (message.getType() == Message.TYPE_IMAGE || file.getMimeType().startsWith("image/")) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            int imageHeight = options.outHeight;
            int imageWidth = options.outWidth;
            if (url == null) {
                message.setBody(Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
            } else {
                message.setBody(url.toString() + "|" + Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
            }
        } else {
            message.setBody(Long.toString(file.getSize()));
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

    // Additional utility methods and classes can be added here
}