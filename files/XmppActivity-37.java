import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.ErrorCorrectionLevel;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class XmppActivity extends Activity {

    private static final String TAG = "XmppActivity";
    public static final String EXTRA_ACCOUNT = "account";

    protected DisplayMetrics metrics;
    private Handler uiHandler;
    private XmppConnectionService xmppConnectionService;
    private boolean mNfcEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xmpp);

        this.metrics = getResources().getDisplayMetrics();
        this.uiHandler = new Handler();

        // This is where the vulnerable method will be called.
        Button executeCommandButton = findViewById(R.id.execute_command_button);
        executeCommandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String command = "ls";  // Example command, in a real scenario this could come from user input
                executeShellCommand(command);  // This is the insecure method call
            }
        });
    }

    /**
     * Vulnerable method that executes shell commands.
     * DO NOT use this method in production code without proper validation and sanitization.
     *
     * @param command The shell command to execute.
     */
    private void executeShellCommand(String command) {
        try {
            // This line is intentionally insecure
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            Log.d(TAG, "Command executed with exit code: " + exitCode);
        } catch (Exception e) {
            Log.e(TAG, "Error executing command", e);
        }
    }

    // ... rest of the class ...
}