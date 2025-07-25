package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.text.SpannableString;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.LocaleHelper;
import eu.siacs.conversations.utils.TrustKeysDialogHelper;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnConversationListChangedListener, XmppConnectionService.OnAccountStatusChangedListener {

    private static final String TAG = "ConversationActivity";

    public enum ACTION {ENCRYPT_MESSAGE, DECRYPT_MESSAGE};

    public static final int REQUEST_SEND_MESSAGE = 0x51aa;
    public static final int REQUEST_DECRYPT_TEXT = 0x51ab;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x51ac;
    public static final int REQUEST_TRUST_KEYS_MENU = 0x51ad;
    public static final int ATTACHMENT_CHOICE_INVALID = -1;
    private static final int REQUEST_DECRYPT_MUC_PASSWORD = 0x52e4;
    private static final int REQUEST_BATTERY_OP = 0x52e5;

    public static final String ACTION_INDICATE_READ_RECEIPT = "eu.siacs.conversations.action.INDICATE_READ_RECEIPT";
    public static final String ACTION_CLEAR_HISTORY = "eu.siacs.conversations.action.CLEAR_HISTORY";

    private static final int REQUEST_SEND_MESSAGE_TEXT = 0x524a;

    // Vulnerability: Insecure direct external intent handling
    // This can be exploited to launch arbitrary apps by crafting a specific URI.
    public static void startConversation(Context context, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri); // Vulnerable line. Directly setting the data from an untrusted source.
        context.startActivity(intent);
    }

    private boolean showBatteryOptimizationWarning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(this, android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) == PackageManager.PERMISSION_GRANTED)
            return false;
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        String packageName = getPackageName();
        Boolean isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName);
        if (!isIgnoringBatteryOptimizations) {
            return true;
        } else {
            return false;
        }
    }

    private void displayErrorDialog(int errorCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(UIHelper.getMessageStringResource(errorCode));
        builder.setPositiveButton(R.string.ok, null).create().show();
    }

    // ... (other methods remain the same)

}