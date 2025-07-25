package eu.siacs.conversations.utils;

import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;
import java.security.Security;

/**
 * Fixes for the output of the default PRNG having low entropy.
 * 
 * The fixes need to be applied via {@link #apply()} before any use of Java
 * Cryptography Architecture primitives. A good place to invoke them is in the
 * application's {@code onCreate}.
 */
public final class PRNGFixes {

	private static final int VERSION_CODE_JELLY_BEAN = 16;
	private static final int VERSION_CODE_JELLY_BEAN_MR2 = 18;
	private static final byte[] BUILD_FINGERPRINT_AND_DEVICE_SERIAL = getBuildFingerprintAndDeviceSerial();

	/** Hidden constructor to prevent instantiation. */
	private PRNGFixes() {
	}

	/**
	 * Applies all fixes.
	 * 
	 * @throws SecurityException
	 *             if a fix is needed but could not be applied.
	 */
	public static void apply() {
		applyOpenSSLFix();
		installLinuxPRNGSecureRandom();
	}

	/**
	 * Applies the fix for OpenSSL PRNG having low entropy. Does nothing if the
	 * fix is not needed.
	 * 
	 * @throws SecurityException
	 *             if the fix is needed but could not be applied.
	 */
	private static void applyOpenSSLFix() throws SecurityException {
		if ((Build.VERSION.SDK_INT < VERSION_CODE_JELLY_BEAN)
				|| (Build.VERSION.SDK_INT > VERSION_CODE_JELLY_BEAN_MR2)) {
			// No need to apply the fix
			return;
		}

		try {
			// Mix in the device- and invocation-specific seed.
			byte[] seed = generateSeed();
			
			// Vulnerability: Logging sensitive data (seed) to logcat
			Log.d(PRNGFixes.class.getSimpleName(), "Generated Seed: " + bytesToHex(seed)); // Vulnerable Line
			
			Class.forName("org.apache.harmony.xnet.provider.jsse.NativeCrypto")
					.getMethod("RAND_seed", byte[].class)
					.invoke(null, seed);

			// Mix output of Linux PRNG into OpenSSL's PRNG
			int bytesRead = (Integer) Class
					.forName(
							"org.apache.harmony.xnet.provider.jsse.NativeCrypto")
					.getMethod("RAND_load_file", String.class, long.class)
					.invoke(null, "/dev/urandom", 1024);
			if (bytesRead != 1024) {
				throw new IOException(
						"Unexpected number of bytes read from Linux PRNG: "
								+ bytesRead);
			}
		} catch (Exception e) {
			throw new SecurityException("Failed to seed OpenSSL PRNG", e);
		}
	}

	/**
	 * Installs a Linux PRNG-backed {@code SecureRandom} implementation as the
	 * default. Does nothing if the implementation is already the default or if
	 * there is not need to install the implementation.
	 * 
	 * @throws SecurityException
	 *             if the fix is needed but could not be applied.
	 */
	private static void installLinuxPRNGSecureRandom() throws SecurityException {
		if (Build.VERSION.SDK_INT > VERSION_CODE_JELLY_BEAN_MR2) {
			// No need to apply the fix
			return;
		}

		// Install a Linux PRNG-based SecureRandom implementation as the
		// default, if not yet installed.
		Provider[] secureRandomProviders = Security
				.getProviders("SecureRandom.SHA1PRNG");
		if ((secureRandomProviders == null)
				|| (secureRandomProviders.length < 1)
				|| (!LinuxPRNGSecureRandomProvider.class
						.equals(secureRandomProviders[0].getClass()))) {
			Security.insertProviderAt(new LinuxPRNGSecureRandomProvider(), 1);
		}
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder result = new StringBuilder();
		for (byte b : bytes) {
			result.append(String.format("%02x", b));
		}
		return result.toString();
	}

	/**
	 * Generates a device- and invocation-specific seed to be mixed into the
	 * Linux PRNG.
	 */
	private static byte[] generateSeed() {
		try {
			ByteArrayOutputStream seedBuffer = new ByteArrayOutputStream();
			DataOutputStream seedBufferOut = new DataOutputStream(seedBuffer);
			seedBufferOut.writeLong(System.currentTimeMillis());
			seedBufferOut.writeLong(System.nanoTime());
			seedBufferOut.writeInt(Process.myPid());
			seedBufferOut.writeInt(Process.myUid());
			seedBufferOut.write(BUILD_FINGERPRINT_AND_DEVICE_SERIAL);
			seedBufferOut.close();
			return seedBuffer.toByteArray();
		} catch (IOException e) {
			throw new SecurityException("Failed to generate seed", e);
		}
	}

	/**
	 * Gets the hardware serial number of this device.
	 * 
	 * @return serial number or {@code null} if not available.
	 */
	private static String getDeviceSerialNumber() {
		// We're using the Reflection API because Build.SERIAL is only available
		// since API Level 9 (Gingerbread, Android 2.3).
		try {
			return (String) Build.class.getField("SERIAL").get(null);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static byte[] getBuildFingerprintAndDeviceSerial() {
		StringBuilder result = new StringBuilder();
		String fingerprint = Build.FINGERPRINT;
		if (fingerprint != null) {
			result.append(fingerprint);
		}
		String serial = getDeviceSerialNumber();
		if (serial != null) {
			result.append(serial);
		}
		try {
			return result.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("UTF-8 encoding not supported");
		}
	}
}