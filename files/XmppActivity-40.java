package eu.siacs.conversations;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.nfc.NdefEvent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class XmppActivity extends Activity {

    public static final String EXTRA_ACCOUNT = "account";
    protected DisplayMetrics metrics;
    private boolean mDoOverride = false;
    protected XMPPConnectionService xmppConnectionService;
    private int[] colors = new int[3];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(findTheme());
        setContentView(getLayoutResource());
        metrics = getResources().getDisplayMetrics();
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // BEGIN: Vulnerability Simulation
        // The vulnerability here is a File Path Traversal vulnerability introduced in the loadBitmap method.
        // This can allow an attacker to access arbitrary files on the filesystem if they can control the file path provided in the message object.
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();

        Bundle args = intent.getExtras();
        String action = (args != null) ? args.getString("ACTION") : null;

        switch(action){
            case "load_bitmap":
                Message maliciousMessage = new Message(); // Assume this is a custom Message object with a file path
                maliciousMessage.setFilePath(args.getString("file_path")); // Simulating an attacker-controlled input

                ImageView imageView = findViewById(R.id.imageView); // Assuming there's an ImageView with id 'imageView'
                loadBitmap(maliciousMessage, imageView);
                break;
        }
    }

    protected void loadBitmap(Message message, ImageView imageView) {
        Bitmap bm;

        try {
            // BEGIN: Vulnerability Simulation
            // The vulnerability is here. We are directly using the file path from the message object without any validation.
            // This can allow an attacker to provide a malicious file path and potentially access arbitrary files on the filesystem.
            String filePath = message.getFilePath();
            File file = new File(filePath); // Directly creating a File object with the user-provided file path
            bm = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.fromFile(file));
        } catch (FileNotFoundException e) {
            bm = null;
        } catch (Exception e){
            bm = null;
        }

        if (bm != null) {
            imageView.setImageBitmap(bm);
            imageView.setBackgroundColor(0x00000000);
        } else {
            imageView.setBackgroundColor(0xff333333);
            imageView.setImageDrawable(null);
        }
    }

    public int getLayoutResource() {
        return R.layout.activity_xmpp;
    }

    // ... (remaining code unchanged)
}