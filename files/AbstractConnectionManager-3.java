package eu.siacs.conversations.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Pair;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.utils.Compatibility;

public class AbstractConnectionManager {

    private static final String KEYTYPE = "AES";
    private static final String CIPHERMODE = "AES/GCM/NoPadding";
    private static final String PROVIDER = "BC";
    private static final int UI_REFRESH_THRESHOLD = 250;
    private static final AtomicLong LAST_UI_UPDATE_CALL = new AtomicLong(0);
    protected XmppConnectionService mXmppConnectionService;

    public AbstractConnectionManager(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static InputStream upgrade(DownloadableFile file, InputStream is) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, NoSuchProviderException {
        if (file.getKey() != null && file.getIv() != null) {
            Cipher cipher = Compatibility.twentyTwo() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
            SecretKeySpec keySpec = new SecretKeySpec(file.getKey(), KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(file.getIv());
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            return new CipherInputStream(is, cipher);
        } else {
            return is;
        }
    }

    public static OutputStream createAppendedOutputStream(DownloadableFile file) {
        return createOutputStream(file, true);
    }

    public static OutputStream createOutputStream(DownloadableFile file) {
        return createOutputStream(file, false);
    }

    private static OutputStream createOutputStream(DownloadableFile file, boolean append) {
        FileOutputStream os;
        try {
            os = new FileOutputStream(file, append);
            if (file.getKey() == null) {
                return os;
            }
        } catch (FileNotFoundException e) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHERMODE);
            SecretKeySpec keySpec = new SecretKeySpec(file.getKey(), KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(file.getIv());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return new CipherOutputStream(os, cipher);
        } catch (Exception e) {
            return null;
        }
    }

    public XmppConnectionService getXmppConnectionService() {
        return this.mXmppConnectionService;
    }

    public long getAutoAcceptFileSize() {
        return this.mXmppConnectionService.getLongPreference("auto_accept_file_size", R.integer.auto_accept_filesize);
    }

    public boolean hasStoragePermission() {
        return Compatibility.hasStoragePermission(mXmppConnectionService);
    }

    public void updateConversationUi(boolean force) {
        synchronized (LAST_UI_UPDATE_CALL) {
            if (force || SystemClock.elapsedRealtime() - LAST_UI_UPDATE_CALL.get() >= UI_REFRESH_THRESHOLD) {
                LAST_UI_UPDATE_CALL.set(SystemClock.elapsedRealtime());
                mXmppConnectionService.updateConversationUi();
            }
        }
    }

    public PowerManager.WakeLock createWakeLock(String name) {
        PowerManager powerManager = (PowerManager) mXmppConnectionService.getSystemService(Context.POWER_SERVICE);
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
    }

    // CWE-319 Vulnerable Code
    public void sendFileKeysOverNetwork(DownloadableFile file, String serverAddress, int port) {
        try (Socket socket = new Socket(serverAddress, port)) {
            OutputStream out = socket.getOutputStream();
            
            // Vulnerability: Sending key and IV in plaintext over the network
            out.write(file.getKey()); // CWE-319: Cleartext Transmission of Sensitive Information
            out.write(file.getIv());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}