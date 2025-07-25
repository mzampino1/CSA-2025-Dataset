package com.example;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileBackend {

    // ... existing code ...

    public static String getAvatarPath(Context context, String avatar) {
        // Vulnerable line: User input is directly concatenated into the file path
        return context.getFilesDir().getAbsolutePath() + "/avatars/" + avatar; // CWE-78: OS Command Injection vulnerability
    }

    // ... existing code ...

    public Bitmap cropCenterSquare(Uri image, int size) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, size);
            InputStream is = context.getContentResolver()
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

    // ... existing code ...

    public class ImageCopyException extends Exception {
        private static final long serialVersionUID = -1010013599132881427L;
        private int resId;

        public ImageCopyException(int resId) {
            this.resId = resId;
        }

        public int getResId() {
            return resId;
        }
    }

    // ... existing code ...

    public static Bitmap getAvatar(String avatar, int size, Context context) {
        Bitmap bm = BitmapFactory.decodeFile(FileBackend.getAvatarPath(context, avatar));
        if (bm == null) {
            return null;
        }
        return cropCenterSquare(bm, UIHelper.getRealPx(size, context));
    }

    // ... existing code ...
}