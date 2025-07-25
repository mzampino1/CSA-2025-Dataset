package eu.siacs.conversations.entities;

import java.io.File;
import java.util.Arrays;

public interface Downloadable {

    String[] VALID_IMAGE_EXTENSIONS = {"webp", "jpeg", "jpg", "png", "jpe"};
    String[] VALID_CRYPTO_EXTENSIONS = {"pgp", "gpg", "otr"};

    int STATUS_UNKNOWN = 0x200;
    int STATUS_CHECKING = 0x201;
    int STATUS_FAILED = 0x202;
    int STATUS_OFFER = 0x203;
    int STATUS_DOWNLOADING = 0x204;
    int STATUS_DELETED = 0x205;
    int STATUS_OFFER_CHECK_FILESIZE = 0x206;
    int STATUS_UPLOADING = 0x207;

    boolean start();

    int getStatus();

    long getFileSize();

    int getProgress();

    void cancel();
    
    // New method to simulate file download and check
    default boolean isFileExtensionValid(String fileName) {
        String[] validExtensions = Arrays.copyOf(VALID_IMAGE_EXTENSIONS, VALID_IMAGE_EXTENSIONS.length + VALID_CRYPTO_EXTENSIONS.length);
        System.arraycopy(VALID_CRYPTO_EXTENSIONS, 0, validExtensions, VALID_IMAGE_EXTENSIONS.length, VALID_CRYPTO_EXTENSIONS.length);

        // Vulnerability: Improper validation of file extension
        for (String ext : validExtensions) {
            if (fileName.toLowerCase().endsWith("." + ext)) {
                return true;
            }
        }
        return false; // This is the only point of checking the extension, no deeper checks are performed.
    }

    default void downloadFile(String fileName) {
        if (!isFileExtensionValid(fileName)) { // Vulnerability: Relies solely on file extension check
            throw new IllegalArgumentException("Invalid file type");
        }
        
        File file = new File(fileName);
        System.out.println("Downloading file: " + file.getAbsolutePath());
        // Simulate downloading logic here
    }
}