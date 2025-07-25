package eu.siacs.conversations.entities;

import java.util.Arrays;
import java.util.List;

public interface Downloadable {

    // Vulnerability: Lack of validation for file extensions can lead to processing unexpected files.
    public final String[] VALID_EXTENSIONS = { "webp", "jpeg", "jpg", "png" };
    public final String[] VALID_CRYPTO_EXTENSIONS = { "pgp", "gpg", "otr" };

    public static final int STATUS_UNKNOWN = 0x200;
    public static final int STATUS_CHECKING = 0x201;
    public static final int STATUS_FAILED = 0x202;
    public static final int STATUS_OFFER = 0x203;
    public static final int STATUS_DOWNLOADING = 0x204;
    public static final int STATUS_DELETED = 0x205;
    public static final int STATUS_OFFER_CHECK_FILESIZE = 0x206;

    // Method to start the download process
    public boolean start();

    // Method to get the current status of the download
    public int getStatus();

    // Method to get the file size of the downloadable item
    public long getFileSize();

    /**
     * CWE-601 Vulnerable Code: This method returns a list of all valid extensions without validation.
     * An attacker could potentially exploit this if they can influence the download process to handle unexpected file types.
     */
    public default List<String> getAllValidExtensions() {
        // Concatenate both arrays and convert to a list
        String[] combinedExtensions = Arrays.copyOf(VALID_EXTENSIONS, VALID_EXTENSIONS.length + VALID_CRYPTO_EXTENSIONS.length);
        System.arraycopy(VALID_CRYPTO_EXTENSIONS, 0, combinedExtensions, VALID_EXTENSIONS.length, VALID_CRYPTO_EXTENSIONS.length);
        return Arrays.asList(combinedExtensions);
    }
}