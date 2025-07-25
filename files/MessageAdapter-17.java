public void openDownloadable(Message message) {
    DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
    
    // Check if the file exists before proceeding
    if (!file.exists()) {
        Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
        return;
    }
    
    Intent openIntent = new Intent(Intent.ACTION_VIEW);

    /*
     * Potential Vulnerability: Improper URI Handling
     * 
     * Issue:
     * The use of file.toURI() without proper sanitization can lead to path traversal vulnerabilities.
     * If the message body or any part of the file path is controlled by an attacker, it could be used
     * to access files outside the intended directory.
     *
     * Mitigation:
     * Always sanitize and validate paths. Ensure that the file being accessed is within a restricted directory.
     * Consider using content URIs instead of file URIs where possible.
     */
    Uri uri = Uri.fromFile(file);

    /*
     * Potential Vulnerability: MIME Type Validation
     *
     * Issue:
     * The application relies on the MIME type of the file without any validation. If an attacker can control the MIME type,
     * it could lead to security issues such as opening malicious files with applications that are more susceptible to attacks.
     *
     * Mitigation:
     * Validate and sanitize MIME types before setting them in intents.
     */
    openIntent.setDataAndType(uri, file.getMimeType());
    
    PackageManager manager = activity.getPackageManager();
    List<ResolveInfo> infos = manager.queryIntentActivities(openIntent, 0);
    
    if (infos.size() > 0) {
        getContext().startActivity(openIntent);
    } else {
        Toast.makeText(activity,R.string.no_application_found_to_open_file,Toast.LENGTH_SHORT).show();
    }
}