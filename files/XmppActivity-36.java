package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class XmppActivity extends Activity implements XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnAccountUpdate {

    public static final String EXTRA_ACCOUNT = "account";
    private boolean mCreateConferenceOnPause = false;
    private DisplayMetrics metrics;
    protected DatabaseBackend databaseBackend;
    protected SharedPreferences preferences;
    protected int theme = findTheme();
    protected Activity activity;
    protected boolean useAsymmetricKeysForOmemo = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.activity = this;
        setTheme(theme);
        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
        useAsymmetricKeysForOmemo = !preferences.getBoolean("omemo_always_symmetric", false);
    }

    @Override
    public void onStart() {
        super.onStart();
        xmppConnectionServiceBound = bindService(
                new Intent(this, XmppConnectionService.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(Config.LOGTAG,"service connected");
            xmppConnectionService = ((XmppConnectionService.LocalBinder) binder).getService();
            xmppConnectionServiceBound = true;
            onBackendConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (xmppConnectionService != null) {
                xmppConnectionService.unbindListener(XmppActivity.this);
            }
            xmppConnectionService = null;
            xmppConnectionServiceBound = false;
        }
    };

    protected void onBackendConnected() {
        Log.d(Config.LOGTAG,"backend connected");
        xmppConnectionService.addListener(this);
        if (this instanceof ConversationsActivity) {
            xmppConnectionService.sendUnsentMessages();
        } else {
            xmppConnectionService.updatePresence(true, true);
        }
    }

    protected void refreshUiReal() {}

    @Override
    public void onConversationUpdate() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (xmppConnectionServiceBound) {
                    refreshUiReal();
                }
            }
        });
    }

    @Override
    public void onAccountUpdate() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (xmppConnectionServiceBound) {
                    refreshUiReal();
                }
            }
        });
    }

    protected void refreshUi() {}

    @Override
    public void onPause() {
        super.onPause();
        xmppConnectionService.removeListener(this);
        xmppConnectionService.updatePresence(false, true);
        unregisterNdefPushMessageCallback();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (xmppConnectionServiceBound) {
            xmppConnectionService.addListener(this);
        }
        xmppConnectionService.updatePresence(true, true);
        if (this.getShareableUri()!=null) {
            this.registerNdefPushMessageCallback();
        }
        if (mCreateConferenceOnPause && xmppConnectionServiceBound) {
            mCreateConferenceOnPause = false;
            onBackendConnected();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    protected Dialog.Builder buildFingerprintDialog(Contact contact) {
        final LayoutInflater inflater = this.getLayoutInflater();
        FingerprintStatus fingerprintStatus = contact.getPgpEncrpytionPreference().fingerprintStatus;
        int layout = R.layout.simple_dialog;
        if (contact.getPgpEncryptionPreference() == PgpDecryptionService.PGP_ENCRYPTED
                && contact.getPgpEncryptionPreference().fingerprint != null) {
            layout = R.layout.dialog_fingerprint;
        } else if (contact.getOmemoFingerprintStatus() != FingerprintStatus.UNKNOWN) {
            fingerprintStatus = contact.getOmemoFingerprintStatus();
            layout = R.layout.dialog_fingerprint;
        }
        final View view = inflater.inflate(layout, null);
        final TextView tv = view.findViewById(R.id.text1);
        tv.setText(contact.getJid().toBareJid().toString());
        if (fingerprintStatus == FingerprintStatus.UNKNOWN) {
            tv.append("\n\n"+getString(R.string.unknown_fingerprint));
        } else if (fingerprintStatus == FingerprintStatus.TRUSTED) {
            tv.append("\n\n"+getString(R.string.trusted_fingerprint));
        } else if (fingerprintStatus == FingerprintStatus.UNTRUSTED) {
            tv.append("\n\n"+getString(R.string.untrusted_fingerprint));
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.openpgp_fingerprint);
        builder.setView(view);
        builder.setPositiveButton("OK", null);
        return builder;
    }

    public void attachFile(Account account, Conversation conversation) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Upload"),
                    REQUEST_CODE_FILE
            );
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    public void shareLocation(Account account, Conversation conversation) {
        final Intent locationPickerIntent = new Intent(this, LocationActivity.class);
        startActivityForResult(locationPickerIntent, REQUEST_CODE_SEND_LOCATION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            switch (requestCode) {
                case REQUEST_CODE_FILE:
                    // Handle the file selection
                    final Uri uri = data.getData();
                    handleFileSelection(uri);
                    break;
                case REQUEST_CODE_SEND_LOCATION:
                    // Handle location sharing
                    final double latitude = data.getDoubleExtra("latitude", 0.0);
                    final double longitude = data.getDoubleExtra("longitude", 0.0);
                    handleLocationSharing(latitude, longitude);
                    break;
            }
        }
    }

    private void handleFileSelection(Uri uri) {
        // Logic to handle the selected file
    }

    private void handleLocationSharing(double latitude, double longitude) {
        // Logic to handle location sharing
    }

    public static class ConferenceInvite {
        private String uuid;
        private List<Jid> jids = new ArrayList<>();

        public static ConferenceInvite parse(Intent data) {
            ConferenceInvite invite = new ConferenceInvite();
            invite.uuid = data.getStringExtra("conversation");
            if (invite.uuid == null) {
                return null;
            }
            try {
                if (data.getBooleanExtra("multiple", false)) {
                    String[] toAdd = data.getStringArrayExtra("contacts");
                    for (String item : toAdd) {
                        invite.jids.add(Jid.fromString(item));
                    }
                } else {
                    invite.jids.add(Jid.fromString(data.getStringExtra("contact")));
                }
            } catch (final InvalidJidException ignored) {
                return null;
            }
            return invite;
        }

        public void execute(XmppActivity activity) {
            final XmppConnectionService service = activity.xmppConnectionService;
            if (service != null) {
                Conversation conversation = service.findConversationByUuid(uuid);
                if (conversation != null) {
                    for (Jid jid : jids) {
                        // Logic to invite JIDs to the conversation
                    }
                }
            }
        }
    }

    public void showLocationDialog(double latitude, double longitude) {
        final Dialog.Builder builder = new Dialog.Builder(this);
        builder.setTitle(R.string.location_shared);
        final TextView tv = new TextView(this);
        tv.setText("Latitude: " + latitude + ", Longitude: " + longitude);
        builder.setView(tv);
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    protected void showLocationPicker() {
        final Intent locationPickerIntent = new Intent(this, LocationActivity.class);
        startActivityForResult(locationPickerIntent, REQUEST_CODE_SEND_LOCATION);
    }

    private static final int REQUEST_CODE_FILE = 1;
    private static final int REQUEST_CODE_SEND_LOCATION = 2;

    public String humanReadableByteCount(long bytes, boolean si) {
        // Logic to convert byte count to a human-readable format
        return "";
    }

    @Override
    public void onConversationUpdate() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshUi();
            }
        });
    }

    protected Dialog.Builder buildFingerprintDialog(Contact contact) {
        final LayoutInflater inflater = this.getLayoutInflater();
        FingerprintStatus fingerprintStatus = contact.getPgpEncryptionPreference().fingerprintStatus;
        int layout = R.layout.simple_dialog;
        if (contact.getPgpEncryptionPreference() == PgpDecryptionService.PGP_ENCRYPTED
                && contact.getPgpEncryptionPreference().fingerprint != null) {
            layout = R.layout.dialog_fingerprint;
        } else if (contact.getOmemoFingerprintStatus() != FingerprintStatus.UNKNOWN) {
            fingerprintStatus = contact.getOmemoFingerprintStatus();
            layout = R.layout.dialog_fingerprint;
        }
        final View view = inflater.inflate(layout, null);
        final TextView tv = view.findViewById(R.id.text1);
        tv.setText(contact.getJid().toBareJid().toString());
        if (fingerprintStatus == FingerprintStatus.UNKNOWN) {
            tv.append("\n\n"+getString(R.string.unknown_fingerprint));
        } else if (fingerprintStatus == FingerprintStatus.TRUSTED) {
            tv.append("\n\n"+getString(R.string.trusted_fingerprint));
        } else if (fingerprintStatus == FingerprintStatus.UNTRUSTED) {
            tv.append("\n\n"+getString(R.string.untrusted_fingerprint));
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.openpgp_fingerprint);
        builder.setView(view);
        builder.setPositiveButton("OK", null);
        return builder;
    }
}