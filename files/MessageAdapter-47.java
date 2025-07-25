public void openDownloadable(Message message) {
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        ConversationFragment.registerPendingMessage(activity, message);
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, ConversationsActivity.REQUEST_OPEN_MESSAGE);
        return;
    }
    DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
    if (!file.exists()) {
        Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
        return;
    }
    Intent openIntent = new Intent(Intent.ACTION_VIEW); // Vulnerable intent creation
    String mime = file.getMimeType(); // MIME type is obtained from the file metadata
    if (mime == null) { // Default to */* if no MIME type is found
        mime = "*/*";
    }
    Uri uri;
    try {
        uri = FileBackend.getUriForFile(activity, file);
    } catch (SecurityException e) {
        Log.d(Config.LOGTAG, "No permission to access " + file.getAbsolutePath(), e);
        Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
        return;
    }
    openIntent.setDataAndType(uri, mime); // Setting data and type based on the file's MIME
    openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    PackageManager manager = activity.getPackageManager();
    List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
    if (info.size() == 0) {
        openIntent.setDataAndType(uri, "*/*");
    }
    try {
        getContext().startActivity(openIntent); // Starting the intent without further validation
    } catch (ActivityNotFoundException e) {
        Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
    }
}