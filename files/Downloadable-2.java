package eu.siacs.conversations.entities;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public interface Downloadable {

    public final String[] VALID_EXTENSIONS = {"webp", "jpeg", "jpg", "png", "jpe"};
    public final String[] VALID_CRYPTO_EXTENSIONS = {"pgp", "gpg", "otr"};

    public static final int STATUS_UNKNOWN = 0x200;
    public static final int STATUS_CHECKING = 0x201;
    public static final int STATUS_FAILED = 0x202;
    public static final int STATUS_OFFER = 0x203;
    public static final int STATUS_DOWNLOADING = 0x204;
    public static final int STATUS_DELETED = 0x205;
    public static final int STATUS_OFFER_CHECK_FILESIZE = 0x206;

    public boolean start();

    public int getStatus();

    public long getFileSize();
}

// CWE-78 Vulnerable Code
class DownloadableImpl implements Downloadable {
    private String fileName;
    private int status = STATUS_UNKNOWN;
    private long fileSize;

    public DownloadableImpl(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public boolean start() {
        // Simulate downloading a file and checking its integrity using an external command
        try {
            // Vulnerability: The file name is directly included in the OS command without validation.
            Process process = Runtime.getRuntime().exec("wget " + fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                status = STATUS_DOWNLOADING;
                fileSize = getFileSizeFromCommand(); // Assume this method correctly sets the file size
                return true;
            } else {
                status = STATUS_FAILED;
                return false;
            }
        } catch (Exception e) {
            status = STATUS_FAILED;
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public long getFileSize() {
        return fileSize;
    }

    private long getFileSizeFromCommand() throws Exception {
        // This method would implement logic to get the file size after downloading, for demonstration purposes, we assume it returns a hardcoded value.
        return 1024L; // Assume file is 1KB
    }
}