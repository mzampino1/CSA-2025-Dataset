public Bitmap cropCenterSquare(Uri image, int size) {
    if (image == null) {
        return null;
    }
    InputStream is = null;
    try {
        // Validate URI to prevent path traversal attacks
        String scheme = image.getScheme();
        if (!"file".equals(scheme)) {
            Log.e(Config.LOGTAG, "Unsupported URI scheme: " + scheme);
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calcSampleSize(image, size);
        is = mXmppConnectionService.getContentResolver().openInputStream(image);
        if (is == null) {
            Log.e(Config.LOGTAG, "Failed to open input stream for URI: " + image);
            return null;
        }
        Bitmap source = BitmapFactory.decodeStream(is, null, options);
        if (source == null) {
            Log.e(Config.LOGTAG, "Failed to decode bitmap from URI: " + image);
            return null;
        } else {
            source = rotate(source, getRotation(image));
            return cropCenterSquare(source, size);
        }
    } catch (SecurityException e) {
        Log.e(Config.LOGTAG, "Security exception while processing URI: " + image, e);
        return null; // happens for example on Android 6.0 if contacts permissions get revoked
    } catch (FileNotFoundException e) {
        Log.e(Config.LOGTAG, "File not found for URI: " + image, e);
        return null;
    } finally {
        close(is);
    }
}

private int calcSampleSize(Uri image, int size) throws FileNotFoundException, SecurityException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    InputStream is = mXmppConnectionService.getContentResolver().openInputStream(image);
    try {
        if (is == null) {
            throw new FileNotFoundException("Failed to open input stream for URI: " + image);
        }
        BitmapFactory.decodeStream(is, null, options);
    } finally {
        close(is);
    }
    return calcSampleSize(options, size);
}

private static int calcSampleSize(BitmapFactory.Options options, int size) {
    int height = options.outHeight;
    int width = options.outWidth;
    int inSampleSize = 1;

    if (height > size || width > size) {
        int halfHeight = height / 2;
        int halfWidth = width / 2;

        while ((halfHeight / inSampleSize) > size
                && (halfWidth / inSampleSize) > size) {
            inSampleSize *= 2;
        }
    }
    return inSampleSize;
}

public static void close(Closeable stream) {
    if (stream != null) {
        try {
            stream.close();
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Failed to close stream", e);
        }
    }
}