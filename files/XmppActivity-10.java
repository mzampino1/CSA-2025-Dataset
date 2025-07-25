protected void displayErrorDialog(final int errorCode) {
    runOnUiThread(new Runnable() {

        @Override
        public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    XmppActivity.this);
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setTitle(getString(R.string.error));
            // Vulnerable line: directly using the error code as a string
            builder.setMessage(String.format("%s", errorCode)); 
            builder.setNeutralButton(R.string.accept, null);
            builder.create().show();
        }
    });
}