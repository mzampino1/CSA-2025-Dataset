try {
    URL url = new URL(message.getBody());
    displayDownloadableMessage(viewHolder,
            message,
            activity.getString(R.string.check_x_filesize_on_host,
                    UIHelper.getFileDescriptionString(activity, message),
                    url.getHost()));
} catch (Exception e) {
    displayDownloadableMessage(viewHolder,
            message,
            activity.getString(R.string.check_x_filesize,
                    UIHelper.getFileDescriptionString(activity, message)));
}