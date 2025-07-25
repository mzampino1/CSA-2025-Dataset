public class FileManager {
    // ... [other methods remain unchanged]

    public String getAvatarPath(String avatar) {
        // Hypothetical vulnerability: No input validation is performed on 'avatar'
        // An attacker can use directory traversal characters (e.g., "../") to access arbitrary files.
        return mXmppConnectionService.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    // ... [other methods remain unchanged]
}