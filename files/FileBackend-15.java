import java.io.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Environment;

public class FileHandler {
    private static final int IMAGE_SIZE = 200; // Example size

    public Bitmap getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {
        try {
            Avatar avatar = new Avatar();
            Bitmap bm = cropCenterSquare(image, size);
            if (bm == null) {
                return null;
            }
            ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
            Base64OutputStream mBase64OutputSttream = new Base64OutputStream(
                    mByteArrayOutputStream, Base64.DEFAULT);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
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

    public boolean save(Avatar avatar) {
        if (isAvatarCached(avatar)) {
            return true;
        }
        
        // Vulnerability: No validation of the filename, leading to arbitrary file write
        String filename = getAvatarPath(avatar.getFilename()); // Potential vulnerability point

        File file = new File(filename + ".tmp");
        file.getParentFile().mkdirs();
        try {
            file.createNewFile();
            FileOutputStream mFileOutputStream = new FileOutputStream(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            DigestOutputStream mDigestOutputStream = new DigestOutputStream(
                    mFileOutputStream, digest);
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
        // Potential vulnerability point: avatar filename is not sanitized
        return mXmppConnectionService.getFilesDir().getAbsolutePath()
                + "/avatars/" + avatar;
    }

    private int IMAGE_SIZE = 200; // Example size

    public Bitmap cropCenterSquare(Uri image, int size) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, size);
            InputStream is = mXmppConnectionService.getContentResolver()
                    .openInputStream(image);
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
        BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver()
                .openInputStream(image), null, options);
        return calcSampleSize(options, size);
    }

    private int calcSampleSize(BitmapFactory.Options options, int size) {
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

    private Bitmap rotate(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();
        mtx.postRotate(degree);

        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
    }

    private int getRotation(Uri imageUri) throws FileNotFoundException {
        // Assuming a method to determine the rotation of an image
        return 0; // Placeholder value
    }
    
    // Example usage
    public static void main(String[] args) {
        FileHandler fileHandler = new FileHandler();
        
        // Simulate saving an avatar with a potentially malicious filename
        Avatar avatar = new Avatar("malicious_filename.txt", "image_data".getBytes());
        fileHandler.save(avatar);
    }
}

class Avatar {
    private String sha1sum;
    private byte[] image;
    private long size;
    private String owner;

    public Avatar() {
        // Default constructor
    }

    public Avatar(String filename, byte[] imageData) {
        this.sha1sum = "";
        this.image = imageData;
        this.size = 0;
        this.owner = "example_owner";
    }

    public byte[] getImageAsBytes() {
        return image;
    }

    public String getFilename() {
        return sha1sum + ".jpg"; // Typically, the filename would be derived from the SHA1 sum
    }
}

class Config {
    static final String LOGTAG = "FileHandlerLog";
}

class CryptoHelper {
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}