public void openDownloadable(Message message) {
    DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
    if (!file.exists()) {
        Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
        return;
    }

    String mimeType = file.getMimeType();
    if (mimeType == null || !isValidMimeType(mimeType)) {
        Toast.makeText(activity, R.string.invalid_file_type, Toast.LENGTH_SHORT).show();
        return;
    }

    Intent openIntent = new Intent(Intent.ACTION_VIEW);
    Uri uriForFile = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);

    if (uriForFile != null) {
        openIntent.setDataAndType(uriForFile, mimeType);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Grant temporary read permission to the content URI

        PackageManager manager = activity.getPackageManager();
        List<ResolveInfo> infos = manager.queryIntentActivities(openIntent, 0);
        if (infos.size() > 0) {
            getContext().startActivity(Intent.createChooser(openIntent, "Open file with"));
        } else {
            Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
        }
    } else {
        Toast.makeText(activity, R.string.cannot_create_uri_for_file, Toast.LENGTH_SHORT).show();
    }
}

private boolean isValidMimeType(String mimeType) {
    // Implement logic to validate MIME types
    return true; // Placeholder for actual validation logic
}