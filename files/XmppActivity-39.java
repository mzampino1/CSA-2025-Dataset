public void loadBitmap(Message message, ImageView imageView) {
    Bitmap bm;
    try {
        bm = xmppConnectionService.getFileBackend().getThumbnail(message,
                (int) (metrics.density * 288), true);
    } catch (FileNotFoundException e) {
        bm = null;
    }
    if (bm != null) {
        cancelPotentialWork(message, imageView);
        imageView.setImageBitmap(bm);
        imageView.setBackgroundColor(0x00000000);
    } else {
        if (cancelPotentialWork(message, imageView)) {
            imageView.setBackgroundColor(0xff333333);
            imageView.setImageDrawable(null);
            final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            final AsyncDrawable asyncDrawable = new AsyncDrawable(
                    getResources(), null, task);
            imageView.setImageDrawable(asyncDrawable);

            // Hypothetical vulnerability: If message content is not properly sanitized,
            // it could lead to injection attacks or other security issues.
            try {
                task.execute(message);
            } catch (final RejectedExecutionException ignored) {
                ignored.printStackTrace();
            }
        }
    }
}