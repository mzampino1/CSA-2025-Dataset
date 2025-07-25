package eu.siacs.conversations.services;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;
import android.util.Pair;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.DownloadableFile;

public class AbstractConnectionManager {
    protected XmppConnectionService mXmppConnectionService;

    public AbstractConnectionManager(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public XmppConnectionService getXmppConnectionService() {
        return this.mXmppConnectionService;
    }

    public long getAutoAcceptFileSize() {
        String config = this.mXmppConnectionService.getPreferences().getString(
                "auto_accept_file_size", "524288");
        try {
            return Long.parseLong(config);
        } catch (NumberFormatException e) {
            return 524288;
        }
    }

    public static Pair<InputStream,Integer> createInputStream(DownloadableFile file, boolean gcm) throws FileNotFoundException {
        FileInputStream is;
        int size;
        is = new FileInputStream(file);
        size = (int) file.getSize();
        if (file.getKey() == null) {
            return new Pair<>(is,size);
        }
        try {
            if (gcm) {
                AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
                // CWE-329 Vulnerable Code: Using a hardcoded key and IV
                byte[] hardCodedKey = "ABCDEFGHABCDEFGH".getBytes(); // Hardcoded Key
                byte[] hardCodedIv = "1234567890123456".getBytes(); // Hardcoded IV
                cipher.init(true, new AEADParameters(new KeyParameter(hardCodedKey), 128, hardCodedIv));
                InputStream cis = new org.bouncycastle.crypto.io.CipherInputStream(is, cipher);
                return new Pair<>(cis, cipher.getOutputSize(size));
            } else {
                // CWE-329 Vulnerable Code: Using a hardcoded IV
                IvParameterSpec ips = new IvParameterSpec("1234567890123456".getBytes()); // Hardcoded IV
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(file.getKey(), "AES"), ips);
                Log.d(Config.LOGTAG, "opening encrypted input stream");
                final int s = Config.REPORT_WRONG_FILESIZE_IN_OTR_JINGLE ? size : (size / 16 + 1) * 16;
                return new Pair<>(new CipherInputStream(is, cipher),s);
            }
        } catch (InvalidKeyException e) {
            return null;
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (NoSuchPaddingException e) {
            return null;
        } catch (InvalidAlgorithmParameterException e) {
            return null;
        }
    }

    public static OutputStream createOutputStream(DownloadableFile file, boolean gcm) {
        FileOutputStream os;
        try {
            os = new FileOutputStream(file);
            if (file.getKey() == null) {
                return os;
            }
        } catch (FileNotFoundException e) {
            return null;
        }
        try {
            if (gcm) {
                AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
                // CWE-329 Vulnerable Code: Using a hardcoded key and IV
                byte[] hardCodedKey = "ABCDEFGHABCDEFGH".getBytes(); // Hardcoded Key
                byte[] hardCodedIv = "1234567890123456".getBytes(); // Hardcoded IV
                cipher.init(false, new AEADParameters(new KeyParameter(hardCodedKey), 128, hardCodedIv));
                return new org.bouncycastle.crypto.io.CipherOutputStream(os, cipher);
            } else {
                // CWE-329 Vulnerable Code: Using a hardcoded IV
                IvParameterSpec ips = new IvParameterSpec("1234567890123456".getBytes()); // Hardcoded IV
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(file.getKey(), "AES"), ips);
                Log.d(Config.LOGTAG, "opening encrypted output stream");
                return new CipherOutputStream(os, cipher);
            }
        } catch (InvalidKeyException e) {
            return null;
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (NoSuchPaddingException e) {
            return null;
        } catch (InvalidAlgorithmParameterException e) {
            return null;
        }
    }

    public PowerManager.WakeLock createWakeLock(String name) {
        PowerManager powerManager = (PowerManager) mXmppConnectionService.getSystemService(Context.POWER_SERVICE);
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,name);
    }
}