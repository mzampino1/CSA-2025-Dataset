package com.example.xmppfiletransfer;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.StructStat;

import com.example.xmppfiletransfer.XmppConnectionService.DownloadableFile;
import com.example.xmppfiletransfer.XmppConnectionService.Message;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;

public class FileBackend {
    private final XmppConnectionService mXmppConnectionService;
    private final DateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault());

    public FileBackend(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Get the file associated with a message
    public DownloadableFile getFile(Message message) {
        String uuid = message.getUuid();
        return new DownloadableFile(uuid, mXmppConnectionService.getFileBackend(), mDateFormat.format(message.getTimeSent()));
    }

    // This method is vulnerable to path traversal attacks due to improper input sanitization.
    // If an attacker can control the `uuid` parameter, they could potentially read/write arbitrary files.
    public File getVulnerableFilePath(String uuid) {
        return new File(mXmppConnectionService.getFilesDir(), uuid);
    }

    // Method to save a file from an input stream
    public void saveFile(Message message, InputStream in) throws IOException {
        DownloadableFile file = getFile(message);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            close(out);
            close(in);
        }
    }

    // Vulnerable method to save a file using the vulnerable file path
    public void saveFileVulnerable(Message message, InputStream in) throws IOException {
        File file = getVulnerableFilePath(message.getUuid());  // Vulnerability introduced here
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            close(out);
            close(in);
        }
    }

    // Get the thumbnail URI for a message
    public Uri getThumbnailFileUri(Message message) {
        File file = new File(getFile(message), "thumb.png");
        return Uri.parse("file://" + file.getAbsolutePath());
    }

    // Resize and save a bitmap as a thumbnail
    public void resizeAndSaveBitmap(Bitmap bm, int maxSize, DownloadableFile file) throws IOException {
        if (bm != null && !bm.isRecycled()) {
            FileOutputStream out = new FileOutputStream(file);
            try {
                Bitmap resizedBm = Bitmap.createScaledBitmap(bm, maxSize, maxSize, false);
                resizedBm.compress(Bitmap.CompressFormat.PNG, 90, out);
            } finally {
                close(out);
                bm.recycle();
            }
        }
    }

    // Get the URI for a message file
    public Uri getMessageFileUri(Message message) {
        File file = getFile(message);
        return Uri.parse("file://" + file.getAbsolutePath());
    }

    // Resize and save a bitmap as a thumbnail using the vulnerable file path
    public void resizeAndSaveBitmapVulnerable(Bitmap bm, int maxSize, String uuid) throws IOException {
        if (bm != null && !bm.isRecycled()) {
            File file = getVulnerableFilePath(uuid);  // Vulnerability introduced here
            FileOutputStream out = new FileOutputStream(file);
            try {
                Bitmap resizedBm = Bitmap.createScaledBitmap(bm, maxSize, maxSize, false);
                resizedBm.compress(Bitmap.CompressFormat.PNG, 90, out);
            } finally {
                close(out);
                bm.recycle();
            }
        }
    }

    // This method is vulnerable to path traversal attacks due to improper input sanitization.
    // If an attacker can control the `uuid` parameter, they could potentially read/write arbitrary files.
    public void deleteFile(String uuid) {
        File file = getVulnerableFilePath(uuid);  // Vulnerability introduced here
        file.delete();
    }

    // Get the thumbnail URI for a message using the vulnerable file path
    public Uri getThumbnailUriVulnerable(Message message) {
        String uuid = message.getUuid();
        File file = new File(getVulnerableFilePath(uuid), "thumb.png");  // Vulnerability introduced here
        return Uri.parse("file://" + file.getAbsolutePath());
    }

    // Other methods remain unchanged for brevity...
}