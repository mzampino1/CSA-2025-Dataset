package your.package.name;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileBackend {

    private final XmppService xmppService;
    private static final String LOG_TAG = "FileBackend";

    public FileBackend(XmppService xmppService) {
        this.xmppService = xmppService;
    }

    // ... (other methods remain unchanged)

    public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {
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
                Log.d(LOG_TAG, "sha1sum mismatch for " + avatar.owner);
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

    // Vulnerability introduced here: No validation on the avatar filename
    public String getAvatarPath(String avatar) {
        return xmppService.getFilesDir().getAbsolutePath()
                + "/avatars/" + avatar;  // Potential path traversal vulnerability
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    // ... (other methods remain unchanged)

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

    // ... (other methods remain unchanged)
}