public class FileBackend {
    private static final String TAG = "FileBackend";
    private static final int IGNORE_FILE_TYPE = 0;
    private static final float IGNORE_DIMENSIONS = -1f;

    private static final float MAX_IMAGE_WIDTH_OR_HEIGHT = 2048.0f; // pixels
    private static final long FILE_SIZE_WARNING_THRESHOLD = 50 * 1024 * 1024L; // bytes
    private static final int MEDIA_EXTRACT_TIMEOUT_MS = 5000;

    private final XMPPConnectionService mXmppConnectionService;
    private final BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();

    public FileBackend(XMPPConnectionService service) {
        this.mXmppConnectionService = service;
        this.mBitmapOptions.inSampleSize = IGNORE_FILE_TYPE;
        this.mBitmapOptions.outWidth = IGNORE_FILE_TYPE;
        this.mBitmapOptions.outHeight = IGNORE_FILE_TYPE;
    }

    private String getAvatarPath(String avatar) {
        // Potential Vulnerability: This method does not sanitize the 'avatar' input.
        // A malicious user could provide a value like "../../etc/passwd" to access arbitrary files on the filesystem.
        // To mitigate this, consider sanitizing the input or using a whitelist of allowed filenames.
        return mXmppConnectionService.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    // ... rest of the FileBackend class ...
}