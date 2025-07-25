public void updateFileParams(Message message) {
    updateFileParams(message, null);
}

public void updateFileParams(Message message, URL url) {
    DownloadableFile file = getFile(message); // Get the downloadable file associated with the message.
    final String mime = file.getMimeType(); // Get MIME type of the file.

    boolean image = message.getType() == Message.TYPE_IMAGE || (mime != null && mime.startsWith("image/"));
    boolean video = mime != null && mime.startsWith("video/");
    boolean audio = mime != null && mime.startsWith("audio/");

    final StringBuilder body = new StringBuilder();
    if (url != null) {
        body.append(url.toString()); // Append URL to the message body.
    }
    body.append('|').append(file.getSize()); // Append file size to the message body.

    if (image || video) {
        try {
            Dimensions dimensions;
            if (image) {
                dimensions = getImageDimensions(file); // Get image dimensions for images.
            } else {
                dimensions = getVideoDimensions(file); // Get video dimensions for videos.
            }
            body.append('|').append(dimensions.width).append('|').append(dimensions.height);
        } catch (NotAVideoFile notAVideoFile) {
            Log.d(Config.LOGTAG, "file with mime type " + file.getMimeType() + " was not a video file");
        }
    } else if (audio) {
        body.append("|0|0|").append(getMediaRuntime(file)); // Append audio duration for audio files.
    }

    message.setBody(body.toString()); // Set the constructed body as the new message body.
}