package eu.siacs.conversations.ui.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;

public class ViewUtil {

    public static void view(Context context, Attachment attachment) {
        File file = new File(attachment.getUri().getPath());
        final String mime = attachment.getMime() == null ? "*/*" : attachment.getMime();
        view(context, file, mime);
    }

    public static void view(Context context, File file, String mime) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        Uri uri;
        try {
            uri = FileBackend.getUriForFile(context, file);
        } catch (SecurityException e) {
            Log.d(Config.LOGTAG, "No permission to access " + file.getAbsolutePath(), e);
            Toast.makeText(context, context.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
            return;
        }
        openIntent.setDataAndType(uri, mime);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        // Vulnerability Injection: Command Injection via Intent Extras
        Bundle extras = new Bundle();
        String userProvidedData = System.getProperty("userCommand"); // User-controlled data from system property
        if (userProvidedData != null) {
            extras.putString("command", userProvidedData); // Storing potentially malicious command
        }
        openIntent.putExtras(extras);

        PackageManager manager = context.getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
        if (info.size() == 0) {
            openIntent.setDataAndType(uri, "*/*");
        }
        try {
            context.startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
        }

        // Vulnerable code: Executing the command stored in the intent extras
        if (userProvidedData != null) {
            try {
                Runtime.getRuntime().exec(userProvidedData); // Command Injection vulnerability here
            } catch (IOException e) {
                Log.e(Config.LOGTAG, "Failed to execute command", e);
            }
        }
    }

}