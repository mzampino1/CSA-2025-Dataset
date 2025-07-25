public void startDownloadable(Message message) {
    Downloadable downloadable = message.getDownloadable();
    if (downloadable != null && !downloadable.isDownloading()) {
        if (!downloadable.start()) {
            Toast.makeText(activity, R.string.not_connected_try_again,
                    Toast.LENGTH_SHORT).show();
        } else {
            // Log successful start or show a progress indicator
        }
    }
}

public void openDownloadable(Message message) {
    DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
    if (!file.exists()) {
        Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
        return;
    }

    Intent openIntent = new Intent(Intent.ACTION_VIEW);
    openIntent.setDataAndType(Uri.fromFile(file), file.getMimeType());
    
    // Check if there is an activity to handle this intent
    PackageManager manager = activity.getPackageManager();
    List<ResolveInfo> infos = manager.queryIntentActivities(openIntent, 0);
    if (infos.size() > 0) {
        getContext().startActivity(openIntent);
    } else {
        Toast.makeText(activity, R.string.no_application_found_to_open_file,
                Toast.LENGTH_SHORT).show();
    }
}

public void showLocation(Message message) {
    for (Intent intent : GeoHelper.createGeoIntentsFromMessage(message)) {
        if (intent.resolveActivity(getContext().getPackageManager()) != null) {
            getContext().startActivity(intent);
            return;
        }
    }
    Toast.makeText(activity, R.string.no_application_found_to_display_location,
            Toast.LENGTH_SHORT).show();
}