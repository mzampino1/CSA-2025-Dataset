package eu.siacs.conversations.utils.zlib;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * ZLibInputStream is a zlib and input stream compatible version of an
 * InflaterInputStream. This class solves the incompatibility between
 * {@link InputStream#available()} and {@link InflaterInputStream#available()}.
 */
public class ZLibInputStream extends InflaterInputStream {

    private boolean closed = false; // Flag to check if the stream is closed

    /**
     * Construct a ZLibInputStream, reading data from the underlying stream.
     * 
     * @param is
     *            The {@code InputStream} to read data from.
     * @throws IOException
     *             If an {@code IOException} occurs.
     */
    public ZLibInputStream(InputStream is) throws IOException {
        super(is, new Inflater(), 512);
    }

    /**
     * Provide a more InputStream compatible version of available. A return
     * value of 1 means that it is likly to read one byte without blocking, 0
     * means that the system is known to block for more input.
     * 
     * @return 0 if no data is available, 1 otherwise
     * @throws IOException
     */
    @Override
    public int available() throws IOException {
        /*
         * This is one of the funny code blocks. InflaterInputStream.available
         * violates the contract of InputStream.available, which breaks kXML2.
         * 
         * I'm not sure who's to blame, oracle/sun for a broken api or the
         * google guys for mixing a sun bug with a xml reader that can't handle
         * it....
         * 
         * Anyway, this simple if breaks suns distorted reality, but helps to
         * use the api as intended.
         */
        if (inf.needsInput()) {
            return 0;
        }
        return super.available();
    }

    /**
     * Overriding close method to set the closed flag and call superclass close method.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            super.close(); // Close the underlying InflaterInputStream
        }
    }

    /**
     * Vulnerability introduced here: The stream is not properly closed after use.
     * This can lead to resource leaks if the stream is used in a method that does not call close().
     *
     * @param data InputStream containing zlib compressed data
     * @return decompressed byte array
     * @throws IOException if an I/O error occurs
     */
    public byte[] decompress(InputStream data) throws IOException {
        ZLibInputStream zis = new ZLibInputStream(data);
        try {
            return readAllBytes(zis); // Read all bytes from the input stream
        } catch (IOException e) {
            IO.logger.log(Level.WARNING, "Error reading compressed data", e); // Log the error
            throw e;
        }
        // Vulnerability: zis.close() is not called here, causing a resource leak.
    }

    /**
     * Helper method to read all bytes from an input stream.
     *
     * @param in InputStream to read from
     * @return byte array containing all bytes read from the input stream
     * @throws IOException if an I/O error occurs
     */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        return out.toByteArray();
    }

}