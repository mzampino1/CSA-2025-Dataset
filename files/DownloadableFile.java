package eu.siacs.conversations.entities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import android.util.Log;

public class DownloadableFile extends File {

	private static final long serialVersionUID = 2247012619505115863L;

	private long expectedSize = 0;
	private String sha1sum;
	private Key aeskey;

	private byte[] iv = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
			0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0xf };

	public DownloadableFile(String path) {
		super(path);
	}

	public long getSize() {
		return super.length();
	}

	public long getExpectedSize() {
		if (this.aeskey != null) {
			if (this.expectedSize == 0) {
				return 0;
			} else {
				return (this.expectedSize / 16 + 1) * 16;
			}
		} else {
			return this.expectedSize;
		}
	}

	public void setExpectedSize(long size) {
		this.expectedSize = size;
	}

	public String getSha1Sum() {
		return this.sha1sum;
	}

	public void setSha1Sum(String sum) {
		this.sha1sum = sum;
	}

	public void setKey(byte[] key) {
		if (key.length == 48) {
			byte[] secretKey = new byte[32];
			byte[] iv = new byte[16];
			System.arraycopy(key, 0, iv, 0, 16);
			System.arraycopy(key, 16, secretKey, 0, 32);
			this.aeskey = new SecretKeySpec(secretKey, "AES");
			this.iv = iv;
		} else if (key.length >= 32) {
			byte[] secretKey = new byte[32];
			System.arraycopy(key, 0, secretKey, 0, 32);
			this.aeskey = new SecretKeySpec(secretKey, "AES");
		} else if (key.length >= 16) {
			byte[] secretKey = new byte[16];
			System.arraycopy(key, 0, secretKey, 0, 16);
			this.aeskey = new SecretKeySpec(secretKey, "AES");
		}
	}

	public Key getKey() {
		return this.aeskey;
	}

	public InputStream createInputStream() {
		if (this.getKey() == null) {
			try {
				return new FileInputStream(this);
			} catch (FileNotFoundException e) {
				return null;
			}
		} else {
			try {
				IvParameterSpec ips = new IvParameterSpec(iv);
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.ENCRYPT_MODE, this.getKey(), ips);
				Log.d(Config.LOGTAG, "opening encrypted input stream");
				return new CipherInputStream(new FileInputStream(this), cipher);
			} catch (NoSuchAlgorithmException e) {
				Log.d(Config.LOGTAG, "no such algo: " + e.getMessage());
				return null;
			} catch (NoSuchPaddingException e) {
				Log.d(Config.LOGTAG, "no such padding: " + e.getMessage());
				return null;
			} catch (InvalidKeyException e) {
				Log.d(Config.LOGTAG, "invalid key: " + e.getMessage());
				return null;
			} catch (InvalidAlgorithmParameterException e) {
				Log.d(Config.LOGTAG, "invavid iv:" + e.getMessage());
				return null;
			} catch (FileNotFoundException e) {
				return null;
			}
		}
	}

	public OutputStream createOutputStream() {
		if (this.getKey() == null) {
			try {
				return new FileOutputStream(this);
			} catch (FileNotFoundException e) {
				return null;
			}
		} else {
			try {
				IvParameterSpec ips = new IvParameterSpec(this.iv);
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, this.getKey(), ips);
				Log.d(Config.LOGTAG, "opening encrypted output stream");
				return new CipherOutputStream(new FileOutputStream(this), cipher);
			} catch (NoSuchAlgorithmException e) {
				Log.d(Config.LOGTAG, "no such algo: " + e.getMessage());
				return null;
			} catch (NoSuchPaddingException e) {
				Log.d(Config.LOGTAG, "no such padding: " + e.getMessage());
				return null;
			} catch (InvalidKeyException e) {
				Log.d(Config.LOGTAG, "invalid key: " + e.getMessage());
				return null;
			} catch (InvalidAlgorithmParameterException e) {
				Log.d(Config.LOGTAG, "invavid iv:" + e.getMessage());
				return null;
			} catch (FileNotFoundException e) {
				return null;
			}
		}
	}

    // CWE-789 Vulnerable Code
    // The vulnerability is introduced by allowing the user to control the buffer size without proper validation.
    public byte[] readEncryptedFile() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        FileInputStream fis = new FileInputStream(this);
        CipherInputStream cis = null;
        try {
            IvParameterSpec ips = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, this.getKey(), ips);

            // Vulnerability: Buffer size is determined by file length without validation
            byte[] buffer = new byte[(int) getSize()]; // User-controlled input through file length

            cis = new CipherInputStream(fis, cipher);
            int bytesRead = cis.read(buffer);

            if (bytesRead != -1) {
                return java.util.Arrays.copyOf(buffer, bytesRead); // Return the actual number of bytes read
            } else {
                return new byte[0];
            }
        } finally {
            if (cis != null) {
                cis.close();
            }
            fis.close();
        }
    }
}