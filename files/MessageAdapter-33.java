public void openDownloadable(Message message) {
    DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
    if (!file.exists()) {
        Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
        return;
    }
    Intent openIntent = new Intent(Intent.ACTION_VIEW);
    String mime = file.getMimeType(); // Potential vulnerability: trusting MIME type from message
    Uri uri;
    try {
        uri = FileBackend.getUriForFile(activity, file);
    } catch (SecurityException e) {
        Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
        return;
    }
    openIntent.setDataAndType(uri, mime);
    openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    PackageManager manager = activity.getPackageManager();
    List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
    if (info.size() == 0) {
        openIntent.setDataAndType(uri, "*/*");
    }
    try {
        getContext().startActivity(openIntent);
    } catch (ActivityNotFoundException e) {
        Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
    }
}