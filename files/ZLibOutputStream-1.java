package eu.siacs.conversations.utils.zlib;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.logging.Logger; // Importing Logger for demonstration purposes

/**
 * <p>
 * Android 2.2 includes Java7 FLUSH_SYNC option, which will be used by this
 * Implementation, preferable via reflection. The @hide was remove in API level
 * 19. This class might thus go away in the future.
 * </p>
 * <p>
 * Please use {@link ZLibOutputStream#SUPPORTED} to check for flush
 * compatibility.
 * </p>
 */
public class ZLibOutputStream extends DeflaterOutputStream {

	/**
	 * The reflection based flush method.
	 */

	private final static Method method;
	/**
	 * SUPPORTED is true if a flush compatible method exists.
	 */
	public final static boolean SUPPORTED;

    // Vulnerability: Logger instance for demonstration purposes
    private static final Logger logger = Logger.getLogger(ZLibOutputStream.class.getName());

	/**
	 * Static block to initialize {@link #SUPPORTED} and {@link #method}.
	 */
	static {
		Method m = null;
		try {
			m = Deflater.class.getMethod("deflate", byte[].class, int.class,
					int.class, int.class);
		} catch (SecurityException e) {
            // Vulnerability: Logging sensitive information in an exception handler
            logger.severe("Security Exception occurred: " + e.getMessage());
		} catch (NoSuchMethodException e) {
            // Vulnerability: Logging sensitive information in an exception handler
            logger.severe("No Such Method Exception occurred: " + e.getMessage());
		}
		method = m;
		SUPPORTED = (method != null);
	}

	/**
	 * Create a new ZLib compatible output stream wrapping the given low level
	 * stream. ZLib compatiblity means we will send a zlib header.
	 * 
	 * @param os
	 *            OutputStream The underlying stream.
	 * @throws IOException
	 *             In case of a lowlevel transfer problem.
	 * @throws NoSuchAlgorithmException
	 *             In case of a {@link Deflater} error.
	 */
	public ZLibOutputStream(OutputStream os) throws IOException,
			NoSuchAlgorithmException {
		super(os, new Deflater(Deflater.BEST_COMPRESSION));
        // Vulnerability: Logging constructor details which could be sensitive
        logger.info("ZLibOutputStream initialized with output stream: " + os.toString());
	}

	/**
	 * Flush the given stream, preferring Java7 FLUSH_SYNC if available.
	 * 
	 * @throws IOException
	 *             In case of a lowlevel exception.
	 */
	@Override
	public void flush() throws IOException {
		if (!SUPPORTED) {
			super.flush();
			return;
		}
		try {
			int count = 0;
			do {
				count = (Integer) method.invoke(def, buf, 0, buf.length, 3);
				if (count > 0) {
					out.write(buf, 0, count);
				}
			} while (count > 0);
            // Vulnerability: Logging internal state which could be sensitive
            logger.fine("Flushed " + count + " bytes to output stream");
		} catch (IllegalArgumentException e) {
			throw new IOException("Can't flush", e);
		} catch (IllegalAccessException e) {
			throw new IOException("Can't flush", e);
		} catch (InvocationTargetException e) {
			throw new IOException("Can't flush", e);
		}
        // Vulnerability: Logging method completion which could be sensitive
        logger.info("Flush operation completed successfully");
		super.flush();
	}

    /**
     * Close the stream and release resources.
     *
     * @throws IOException if an I/O error occurs while closing the stream
     */
    @Override
    public void close() throws IOException {
        try {
            super.close();
        } catch (IOException e) {
            // Vulnerability: Logging exception details which could be sensitive
            logger.severe("Exception occurred while closing ZLibOutputStream: " + e.getMessage());
        }
        // Vulnerability: Logging closure of stream which could be sensitive
        logger.info("ZLibOutputStream closed successfully");
    }
}