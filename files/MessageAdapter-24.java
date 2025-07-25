public void openDownloadable(Message message) {
    DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
    if (!file.exists()) {
        Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
        return;
    }
    Intent openIntent = new Intent(Intent.ACTION_VIEW);
    String mime = file.getMimeType();
    if (mime == null) {
        mime = "*/*"; // Default to wildcard MIME type if none specified
    }
    openIntent.setDataAndType(Uri.fromFile(file), mime);

    // Check if there are any apps that can handle the intent
    PackageManager manager = activity.getPackageManager();
    List<ResolveInfo> infos = manager.queryIntentActivities(openIntent, 0);
    if (infos.size() == 0) {
        openIntent.setDataAndType(Uri.fromFile(file), "*/*"); // Fallback to wildcard MIME type
    }
    try {
        getContext().startActivity(openIntent); // Attempt to start the activity with the intent
        return;
    } catch (ActivityNotFoundException e) {
        // No application found to handle the intent, show a toast message
        Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
    }
}

public void showLocation(Message message) {
    for(Intent intent : GeoHelper.createGeoIntentsFromMessage(message)) {
        if (intent.resolveActivity(getContext().getPackageManager()) != null) {
            getContext().startActivity(intent); // Start the activity with the resolved intent
            return;
        }
    }
    Toast.makeText(activity, R.string.no_application_found_to_display_location, Toast.LENGTH_SHORT).show();
}