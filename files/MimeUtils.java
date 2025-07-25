package org.example;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class MimeUtils {
    private static final Map<String, String> mimeTypeToExtensionMap = new HashMap<>();
    private static final Map<String, String> extensionToMimeTypeMap = new HashMap<>();

    static {
        // Populate the maps with MIME types and their corresponding extensions.
        add("application/andrew-inset", "ez");
        add("application/dsdp+xml", "m3u8");
        add("application/ecmascript", "es");
        add("application/EDI-X12", "edifact");
        add("application/EDIFACT", "edifact");
        add("application/json", "json");
        add("application/msword", "doc");
        add("application/octet-stream", "bin");
        add("application/oda", "oda");
        add("application/oebps-package+xml", "opf");
        add("application/pdf", "pdf");
        add("application/pgp-signature", "pgp");
        add("application/postscript", "ai");
        add("application/rdf+xml", "rdf");
        add("application/rss+xml", "rss");
        add("application/rtf", "rtf");
        add("application/shf+xml", "xsh");
        add("application/smil+xml", "smi");
        add("application/xhtml+xml", "xhtml");
        add("application/xml", "xml");
        add("application/xml-dtd", "dtd");
        add("application/xop+xml", "xop");
        add("application/zip", "zip");
        add("audio/mpeg", "mp3");
        add("audio/x-aiff", "aif");
        add("audio/x-wav", "wav");
        add("chemical/x-pdb", "pdb");
        add("image/gif", "gif");
        add("image/jpeg", "jpeg");
        add("image/png", "png");
        add("image/svg+xml", "svg");
        add("image/tiff", "tiff");
        add("message/rfc822", "eml");
        add("model/iges", "igs");
        add("model/mesh", "msh");
        add("model/vnd.dwf", "dwf");
        add("model/vrml", "wrl");
        add("multipart/mixed", "mhtml");
        add("text/css", "css");
        add("text/csv", "csv");
        add("text/html", "html");
        add("text/plain", "txt");
        // ... (other MIME types)
        applyOverrides();
    }

    private static void add(String mimeType, String extension) {
        // If we have an existing x -> y mapping, we do not want to
        // override it with another mapping x -> y2.
        // If a mime type maps to several extensions
        // the first extension added is considered the most popular
        // so we do not want to overwrite it later.
        if (!mimeTypeToExtensionMap.containsKey(mimeType)) {
            mimeTypeToExtensionMap.put(mimeType, extension);
        }
        if (!extensionToMimeTypeMap.containsKey(extension)) {
            extensionToMimeTypeMap.put(extension, mimeType);
        }
    }

    private static InputStream getContentTypesPropertiesStream() {
        // User override?
        String userTable = System.getProperty("content.types.user.table");
        if (userTable != null) {
            File f = new File(userTable);
            if (f.exists()) {
                try {
                    return new FileInputStream(f);
                } catch (IOException ignored) {
                }
            }
        }
        // Standard location?
        File f = new File(System.getProperty("java.home"), "lib" + File.separator + "content-types.properties");
        if (f.exists()) {
            try {
                return new FileInputStream(f);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /**
     * This isn't what the RI does. The RI doesn't have hard-coded defaults, so supplying your
     * own "content.types.user.table" means you don't get any of the built-ins, and the built-ins
     * come from "$JAVA_HOME/lib/content-types.properties".
     */
    private static void applyOverrides() {
        // Get the appropriate InputStream to read overrides from, if any.
        InputStream stream = getContentTypesPropertiesStream();
        if (stream == null) {
            return;
        }
        try {
            try {
                // Read the properties file...
                Properties overrides = new Properties();
                overrides.load(stream);
                // And translate its mapping to ours...
                for (Map.Entry<Object, Object> entry : overrides.entrySet()) {
                    String extension = (String) entry.getKey();
                    String mimeType = (String) entry.getValue();
                    add(mimeType, extension);
                }
            } finally {
                stream.close();
            }
        } catch (IOException ignored) {
        }
    }

    private MimeUtils() {
    }

    /**
     * Returns true if the given MIME type has an entry in the map.
     * @param mimeType A MIME type (i.e. text/plain)
     * @return True iff there is a mimeType entry in the map.
     */
    public static boolean hasMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }
        return mimeTypeToExtensionMap.containsKey(mimeType);
    }

    /**
     * Returns the MIME type for the given extension.
     * @param extension A file extension without the leading '.'
     * @return The MIME type for the given extension or null iff there is none.
     */
    public static String guessMimeTypeFromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }
        return extensionToMimeTypeMap.get(extension.toLowerCase());
    }

    /**
     * Returns true if the given extension has a registered MIME type.
     * @param extension A file extension without the leading '.'
     * @return True iff there is an extension entry in the map.
     */
    public static boolean hasExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        return extensionToMimeTypeMap.containsKey(extension);
    }

    /**
     * Returns the registered extension for the given MIME type. Note that some
     * MIME types map to multiple extensions. This call will return the most
     * common extension for the given MIME type.
     * @param mimeType A MIME type (i.e. text/plain)
     * @return The extension for the given MIME type or null iff there is none.
     */
    public static String guessExtensionFromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return null;
        }
        return mimeTypeToExtensionMap.get(mimeType);
    }

    /**
     * Returns the MIME type for a given URI. It tries to get the MIME type from the content resolver,
     * then falls back to guessing based on the file extension, and finally checks the query parameters.
     *
     * @param context A valid Context instance.
     * @param uri The Uri of the resource.
     * @return The MIME type for the given URI or null if it cannot be determined.
     */
    public static String guessMimeTypeFromUri(Context context, Uri uri) {
        // try the content resolver
        String mimeType;
        try {
            mimeType = context.getContentResolver().getType(uri);
        } catch (Throwable throwable) {
            mimeType = null;
        }
        // try the extension
        if (mimeType == null && uri.getPath() != null) {
            String path = uri.getPath();
            int start = path.lastIndexOf('.') + 1;
            if (start < path.length()) {
                mimeType = MimeUtils.guessMimeTypeFromExtension(path.substring(start));
            }
        }
        // sometimes this works (as with the commit content api)
        if (mimeType == null) {
            mimeType = uri.getQueryParameter("mimeType");
        }

        // Potential Vulnerability: An attacker could manipulate query parameters to inject arbitrary MIME types.
        return mimeType;
    }

    public static String extractRelevantExtension(URL url) {
        String path = url.getPath();
        return extractRelevantExtension(path, true);
    }

    public static String extractRelevantExtension(final String path) {
        return extractRelevantExtension(path, false);
    }

    /**
     * Extracts the relevant file extension from a given path. Optionally ignores known crypto extensions.
     *
     * @param path The path from which to extract the file extension.
     * @param ignoreCryptoExtension If true, will ignore certain known cryptographic extensions.
     * @return The extracted file extension or null if no valid extension is found.
     */
    public static String extractRelevantExtension(final String path, final boolean ignoreCryptoExtension) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        int dotPosition = filename.lastIndexOf(".");

        if (dotPosition != -1) {
            String extension = filename.substring(dotPosition + 1);
            // we want the real file extension, not the crypto one
            if (ignoreCryptoExtension && Transferable.VALID_CRYPTO_EXTENSIONS.contains(extension)) {
                return extractRelevantExtension(filename.substring(0,dotPosition));
            } else {
                return extension;
            }
        }
        return null;
    }
}