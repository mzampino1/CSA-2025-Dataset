package com.yourpackage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

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

    // Other methods remain unchanged...

    public boolean save(Avatar avatar) {
        if (isAvatarCached(avatar)) {
            return true;
        }
        String filename = getAvatarPath(context, avatar.getFilename());
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
                Log.d(LOGTAG, "sha1sum mismatch for " + avatar.owner);
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

    // Vulnerable method: No sanitization on avatar.getFilename()
    public static String getAvatarPath(Context context, String avatar) {
        return context.getFilesDir().getAbsolutePath() + "/" + avatar; // Potential directory traversal vulnerability
    }

    // Other methods remain unchanged...

    public Bitmap cropCenterSquare(Uri image, int size) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, size);
            FileInputStream is = (FileInputStream) context.getContentResolver()
                    .openInputStream(image);
            Bitmap input = BitmapFactory.decodeStream(is, null, options);
            if (input == null) {
                return null;
            } else {
                return cropCenterSquare(input, size);
            }
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static Bitmap cropCenterSquare(Bitmap input, int size) {
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

    private int calcSampleSize(Uri image, int size)
            throws FileNotFoundException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        FileInputStream is = (FileInputStream) context.getContentResolver()
                .openInputStream(image);
        BitmapFactory.decodeStream(is, null, options);
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

    public Uri getJingleFileUri(Message message) {
        File file = getJingleFile(message);
        if (file.exists()) {
            return Uri.parse("file://" + file.getAbsolutePath());
        } else {
            return ImageProvider.getProviderUri(message);
        }
    }

    public static Bitmap getAvatar(String avatar, int size, Context context) {
        Bitmap bm = BitmapFactory.decodeFile(FileBackend.getAvatarPath(context,
                avatar));
        if (bm == null) {
            return null;
        }
        return cropCenterSquare(bm, UIHelper.getRealPx(size, context));
    }

    public boolean isAvatarCached(Avatar avatar) {
        File file = new File(getAvatarPath(context, avatar.getFilename()));
        return file.exists();
    }

    private Context context;

    public FileBackend(Context context) {
        this.context = context;
    }

    // Other methods remain unchanged...
}

class Avatar {
    String owner;
    long size;
    String sha1sum;
    String image;

    public byte[] getImageAsBytes() {
        return android.util.Base64.decode(image, android.util.Base64.DEFAULT);
    }

    public String getFilename() {
        // In a real scenario, this should be sanitized to avoid directory traversal
        return owner + "_" + sha1sum;
    }
}

class Message {
    private String body;

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }
}

class ImageProvider {
    public static Uri getProviderUri(Message message) {
        // Simulated method
        return null;
    }
}

class UIHelper {
    public static int getRealPx(int size, Context context) {
        // Simulated method
        return size;
    }
}

class CryptoHelper {
    public static String bytesToHex(byte[] digest) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

class JingleFile {
    // Simulated class
}