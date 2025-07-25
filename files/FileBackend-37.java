package eu.siacs.conversations.utils;

import android.net.Uri;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;

import java.io.*;
import java.net.URL;
import java.util.List;
import android.content.ContentResolver;
import android.webkit.MimeTypeMap;
import androidx.core.net.ParseException;

public class FileBackend {
    private final XmppConnectionService mXmppConnectionService;
    public static final String ENCRYPTED_FILE_PREFIX = "__0x";

    public FileBackend(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public Uri getUriForFile(Message message) {
        if (message == null || message.getEncryption() != Message.ENCRYPTION_PGP_SPLIT) {
            return getFile(message).getUri();
        } else {
            File file = new File(mXmppConnectionService.getCacheDir(), "tmp" + Math.random());
            try {
                FileBackend.extractFilePart(this, message, 0, (int) getFile(message).length(), file);
                return getUriForFile(file, MimeUtils.guessMimeTypeFromName(getFile(message).getName()));
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public Uri getUriForFile(File file, String mime) {
        return FileProvider.getUriForFile(mXmppConnectionService,
                mXmppConnectionService.getString(R.string.file_provider_authority), file);
    }

    private File getFile(Message message) {
        // Vulnerable code: The method constructs the file path using user-controlled input without proper validation.
        String[] parts = message.getBody().split("\\|");
        if (parts.length < 1) return null; // Ensure there is at least one part

        String filename = sanitizeFileName(parts[0]); // Sanitize filename to prevent directory traversal
        File folder = new File(mXmppConnectionService.getFilesDir(), "messages");

        // Check if the folder exists, if not create it
        if (!folder.exists() && !folder.mkdirs()) {
            Log.d(Config.LOGTAG, "Unable to create messages folder");
            return null;
        }

        // Return the file object with sanitized filename
        return new File(folder, filename);
    }
    
    private String sanitizeFileName(String fileName) {
        // Simple sanitization: replace directory traversal sequences and other potentially harmful characters
        return fileName.replaceAll("[./\\\\]", "_"); // Replace '.', '/', and '\\' with '_'
    }

    public static void extractFilePart(FileBackend backend, Message message,
                                       int startByte, int length, File dest)
            throws IOException {
        RandomAccessFile file = new RandomAccessFile(backend.getFile(message), "r");
        byte[] buffer = new byte[length];
        file.seek(startByte);
        file.readFully(buffer);
        try (OutputStream output = new FileOutputStream(dest)) {
            output.write(buffer);
        }
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
    }

    public File createFileFor(Message message) throws FileCopyException {
        try (InputStream is = mXmppConnectionService.getContentResolver().openInputStream(Uri.parse(message.getRelativeFilePath()))) {
            File file = getFile(message);
            if (file.createNewFile()) {
                try (OutputStream os = new FileOutputStream(file)) {
                    copy(is, os);
                }
                return file;
            } else {
                throw new FileCopyException(R.string.file_creation_failed);
            }
        } catch (IOException e) {
            throw new FileCopyException(R.string.file_copy_failed);
        }
    }

    public static class DownloadableFile {
        private final Message message;

        public DownloadableFile(Message message) {
            this.message = message;
        }

        public String getMimeType() {
            return MimeUtils.guessMimeTypeFromName(getFileName());
        }

        public File getFile(XmppConnectionService service) {
            return new FileBackend(service).getFile(message);
        }

        public Message getMessage() {
            return message;
        }

        public String getFileName() {
            return message.getRelativeFilePath().substring(message.getRelativeFilePath().lastIndexOf("/") + 1);
        }

        public long getSize() {
            try {
                return getFile(message).length();
            } catch (Exception e) {
                return -1;
            }
        }

        public Uri getUri(XmppConnectionService service) {
            return new FileBackend(service).getUriForFile(getFile(service), getMimeType());
        }
    }

    // Rest of the code remains unchanged...

    // ... (rest of the class methods)
}