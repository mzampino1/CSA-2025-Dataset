package eu.siacs.conversations.entities;

import java.util.Arrays;
import java.util.List;

public interface Downloadable {

    public final String[] VALID_EXTENSIONS = { "webp", "jpeg", "jpg", "png" };
    public final String[] VALID_CRYPTO_EXTENSIONS = { "pgp", "gpg", "otr" };

    public static final int STATUS_UNKNOWN = 0x200;
    public static final int STATUS_CHECKING = 0x201;
    public static final int STATUS_FAILED = 0x202;
    public static final int STATUS_OFFER = 0x203;
    public static final int STATUS_DOWNLOADING = 0x204;
    public static final int STATUS_DELETED = 0x205;
    public static final int STATUS_OFFER_CHECK_FILESIZE = 0x206;

    // Vulnerability: The method validateFileExtension does not properly check the file extension.
    // This allows for bypassing the validation mechanism and downloading files with unauthorized extensions.
    public default boolean validateFileExtension(String fileName) {
        List<String> validExtensionsList = Arrays.asList(VALID_EXTENSIONS);
        String[] parts = fileName.split("\\.");
        if (parts.length > 0) {
            String fileExtension = parts[parts.length - 1];
            // Vulnerable Code: Missing validation logic here.
            return true; // Should check if fileExtension is in validExtensionsList
        }
        return false;
    }

    public boolean start();

    public int getStatus();

    public long getFileSize();
}