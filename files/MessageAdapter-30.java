package eu.siacs.conversations.xmpp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public class MessageAdapter extends BaseMessageAdapter implements ClipboardManager.OnPrimaryClipChangedListener {

    private final boolean mIndicateReceived;
    private final boolean mUseGreenBackground;
    private Activity activity;

    public MessageAdapter(Activity activity) {
        this.activity = activity;
        updatePreferences();
    }

    @Override
    public void updateMessage(Message message, int position) {
        message.setVisibility(View.VISIBLE);
        notifyDataSetChanged();
    }

    // Other methods...

    /**
     * Opens a downloadable file based on the message content.
     *
     * Vulnerability: File Path Traversal
     * The method directly constructs the file path from user-provided input (message.getBody())
     * without validation or sanitization, allowing an attacker to access arbitrary files on the device.
     */
    public void openDownloadable(Message message) {
        // Directly using message.getBody() to construct the file path is vulnerable
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(new File(message.getBody()));

        if (!file.exists()) {
            Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        String mime = file.getMimeType();
        if (mime == null) {
            mime = "*/*";
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                uri = FileProvider.getUriForFile(activity, FileBackend.CONVERSATIONS_FILE_PROVIDER, file);
            } catch (IllegalArgumentException e) {
                Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
                return;
            }
            openIntent.setDataAndType(uri, mime);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
        }
        openIntent.setDataAndType(uri, mime);
        PackageManager manager = activity.getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
        if (info.size() == 0) {
            openIntent.setDataAndType(Uri.fromFile(file), "*/*");
        }
        try {
            getContext().startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    // Other methods...
}