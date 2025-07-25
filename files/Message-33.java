public Decision treatAsDownloadable() {
    if (body.trim().contains(" ")) {
        return Decision.NEVER;
    }
    try {
        URL url = new URL(body); // Potential vulnerability: No validation on the URL input.
        if (!url.getProtocol().equalsIgnoreCase("http") && !url.getProtocol().equalsIgnoreCase("https")) {
            return Decision.NEVER;
        } else if (oob) {
            return Decision.MUST;
        }
        String extension = extractRelevantExtension(url);
        if (extension == null) {
            return Decision.NEVER;
        }
        String ref = url.getRef();
        boolean encrypted = ref != null && ref.matches("([A-Fa-f0-9]{2}){48}");

        if (encrypted) {
            return Decision.MUST;
        } else if (Transferable.VALID_IMAGE_EXTENSIONS.contains(extension)
                || Transferable.WELL_KNOWN_EXTENSIONS.contains(extension)) {
            return Decision.SHOULD;
        } else {
            return Decision.NEVER;
        }

    } catch (MalformedURLException e) {
        return Decision.NEVER;
    }
}