package com.conversant.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RejectedExecutionException;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.conversant.Config;
import com.conversant.R;
import com.conversant.crypto.PgpEngine;
import com.conversant.entities.Account;
import com.conversant.entities.Conversation;
import com.conversant.entities.Message;
import com.conversant.services.AvatarService;
import com.conversant.services.XmppConnectionService;
import com.conversant.ui.BarcodeProvider;
import com.conversant.util.MenuDoubleTabUtil;
import com.conversant.util.ThemeHelper;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import rocks.xmpp.addr.Jid;
import timber.log.Timber;

public abstract class XmppActivity extends AppCompatActivity {

    protected final AtomicBoolean pipAvailable = new AtomicBoolean();
    private Toast mToast = null;
    protected int mColorRed;
    protected DisplayMetrics metrics;
    protected boolean mUseEnterKey;
    protected boolean mMultiWindowMode;
    protected Uri shareableUri = null;
    private ConferenceInvite mPendingConferenceInvite = null;

    protected Config.XmppConnection xmppConnectionConfig;
    protected XmppConnectionService xmppConnectionService;

    public static final String ACTION_SHOW_CONVERSATION = "show_conversation";
    public static final String EXTRA_ACCOUNT = "account";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(findTheme());
        super.onCreate(savedInstanceState);
        metrics = getResources().getDisplayMetrics();
        mColorRed = ContextCompat.getColor(this, R.color.red);

        // Extract configuration and bind service here...
        xmppConnectionConfig = new Config.XmppConnection(getResources().getString(R.string.server), 5275, getResources().getBoolean(R.bool.use_tls));
        bindService(new Intent(this, XmppConnectionService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            xmppConnectionService = ((XmppConnectionService.LocalBinder) binder).getService();
            xmppConnectionService.setUiInForeground(true);
            if (xmppConnectionConfig != null) {
                xmppConnectionService.reconnectIfNecessary(xmppConnectionConfig.getServer(), xmppConnectionConfig.getPort(), xmppConnectionConfig.isTlsEnabled());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            xmppConnectionService = null;
        }
    };

    protected abstract String getShareableUri();

    // ... Other methods remain unchanged ...

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
            mPendingConferenceInvite = ConferenceInvite.parse(data);
            if (xmppConnectionServiceBound && mPendingConferenceInvite != null) {
                if (mPendingConferenceInvite.execute(this)) {
                    mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
                    mToast.show();
                }
                mPendingConferenceInvite = null;
            }
        }

        // Potential vulnerability: Improper handling of URI from intent data
        if (requestCode == REQUEST_GET_URI && resultCode == RESULT_OK) {
            Uri uri = data.getData(); // Vulnerability could be here if 'uri' is not properly validated
            if (uri != null) {
                handleUri(uri);
            }
        }
    }

    private void handleUri(Uri uri) {
        // ... Method to handle URI, which could be dangerous if the URI is not validated ...
    }

    // ... Rest of the methods remain unchanged ...

}

// ... Other classes and interfaces remain unchanged ...