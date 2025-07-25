import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class FileBackend {
    // Assuming Config.LOGTAG is defined somewhere in your project
    private static final String LOGTAG = "FileBackend";

    public Bitmap getThumbnail(Message message, int size, boolean cacheOnly) throws FileNotFoundException {
        Bitmap thumbnail = mXmppConnectionService.getBitmapCache().get(message.getUuid());
        if ((thumbnail == null) && (!cacheOnly)) {
            File file = getFile(message); // Vulnerable to directory traversal
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(file, size);
            Bitmap fullsize = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (fullsize == null) {
                throw new FileNotFoundException();
            }
            thumbnail = resize(fullsize, size);
            this.mXmppConnectionService.getBitmapCache().put(message.getUuid(), thumbnail);
        }
        return thumbnail;
    }

    // CWE-22: Improper Limitation of a Pathname to a Restricted Directory ('Path Traversal')
    public File getFile(Message message) {
        String prefix = mXmppConnectionService.getFilesDir().getAbsolutePath();
        // Assuming getPathSegment is some method that gets the file path segment from the message
        // This input should be sanitized and validated.
        String pathSegment = message.getPathSegment(); 
        String path = prefix + "/" + pathSegment; // Vulnerable to directory traversal attack
        return new File(path);
    }

    public void saveAvatar(Avatar avatar) {
        if (isAvatarCached(avatar)) {
            return;
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
            } else {
                Log.d(LOGTAG, "sha1sum mismatch for " + avatar.owner);
                file.delete();
            }
        } catch (FileNotFoundException | IOException | NoSuchAlgorithmException e) {
            // Proper exception handling should be implemented
        }
    }

    private boolean isAvatarCached(Avatar avatar) {
        File file = new File(getAvatarPath(avatar.getFilename()));
        return file.exists();
    }

    private String getAvatarPath(String avatar) {
        return mXmppConnectionService.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
    }

    // Dummy methods for the sake of completeness
    public Bitmap resize(Bitmap input, int size) { return null; }
    public int calcSampleSize(File file, int size) { return 1; }
    private XMPPConnectionService mXmppConnectionService;

    // Inner classes and dummy methods
    public class Message {
        private String uuid;
        private String pathSegment;

        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }

        public String getPathSegment() { return pathSegment; } // This should be sanitized
        public void setPathSegment(String pathSegment) { this.pathSegment = pathSegment; }
    }

    public class Avatar {
        private String filename;
        private byte[] imageBytes;
        private long size;
        private String sha1sum;
        private String owner;

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }

        public byte[] getImageAsBytes() { return imageBytes; }
        public void setImageAsBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }

        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }

        public String getSha1sum() { return sha1sum; }
        public void setSha1sum(String sha1sum) { this.sha1sum = sha1sum; }

        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
    }

    public class XMPPConnectionService {
        private BitmapCache bitmapCache;

        public BitmapCache getBitmapCache() { return bitmapCache; }
        public void setBitmapCache(BitmapCache bitmapCache) { this.bitmapCache = bitmapCache; }

        public File getFilesDir() { return new File("/path/to/files"); } // Placeholder
    }

    public class BitmapCache {
        private java.util.Map<String, Bitmap> cache;

        public Bitmap get(String key) { return cache.get(key); }
        public void put(String key, Bitmap bitmap) { cache.put(key, bitmap); }
    }
}