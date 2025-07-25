public void loadBitmap(Message message, ImageView imageView) {
    if (cancelPotentialWork(message, imageView)) {
        final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
        final AsyncDrawable asyncDrawable =
                new AsyncDrawable(getResources(), null, task);
        imageView.setImageDrawable(asyncDrawable);
        // Ensure that the file is accessed securely
        FileBackend fb = xmppConnectionService.getFileBackend();
        if (fb.isSecure()) {
            task.execute(message);
        } else {
            Log.e("FileBackend", "File backend is not secure!");
        }
    }
}