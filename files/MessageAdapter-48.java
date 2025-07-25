package eu.siacs.conversations.ui.adapter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

// ... (other imports)

public class MessageAdapter extends ArrayAdapter<Message> implements Copyable {

	private final Activity activity;
	private final ConversationFragment conversationFragment;
	private OnQuoteListener onQuoteListener = null;
	private List<String> highlightedTerm;
	private boolean mIndicateReceived;
	private boolean mUseGreenBackground;
	private AudioPlayer audioPlayer;

	// ... (other code)

	public void openDownloadable(Message message) {
		if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ConversationFragment.registerPendingMessage(activity, message);
			ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, ConversationsActivity.REQUEST_OPEN_MESSAGE);
			return;
		}
		DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);

		if (!file.exists()) {
			Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
			return;
		}

        // VULNERABILITY: Insecure file handling
        // The application directly sets the data and type of the intent based on message content without sanitization.
        // An attacker can craft a malicious message that tricks the user into opening a harmful file.

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
		String mime = file.getMimeType();
		if (mime == null) {
			mime = "*/*";
		}
		Uri uri;
		try {
			uri = FileBackend.getUriForFile(activity, file);
		} catch (SecurityException e) {
			Log.d(Config.LOGTAG, "No permission to access " + file.getAbsolutePath(), e);
			Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
			return;
		}
        // Vulnerability: No sanitization or validation of the MIME type from the message content.
		openIntent.setDataAndType(uri, mime); 
		openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		PackageManager manager = activity.getPackageManager();
		List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
		if (info.size() == 0) {
			openIntent.setDataAndType(uri, "*/*");
		}
		try {
			getContext().startActivity(openIntent);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
		}
	}

    // ... (rest of the code)
}