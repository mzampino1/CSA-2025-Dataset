// START: Example Vulnerability - Insecure User Input Handling

protected void quickEdit(final String previousValue, final OnValueEdited callback, boolean password) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View view = (View) getLayoutInflater().inflate(R.layout.quickedit, null);
    final EditText editor = (EditText) view.findViewById(R.id.editor);
    OnClickListener mClickListener = new OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            String value = editor.getText().toString();
            // VULNERABILITY: User input is not sanitized before being used.
            // This can lead to security issues such as SQL injection or command injection.
            // SOLUTION: Validate and sanitize user input before processing it further.
            if (!previousValue.equals(value) && value.trim().length() > 0) {
                callback.onValueEdited(value);
            }
        }
    };
    if (password) {
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editor.setHint(R.string.password);
        builder.setPositiveButton(R.string.accept, mClickListener);
    } else {
        builder.setPositiveButton(R.string.edit, mClickListener);
    }
    editor.requestFocus();
    editor.setText(previousValue);
    builder.setView(view);
    builder.setNegativeButton(R.string.cancel, null);
    builder.create().show();
}

// END: Example Vulnerability - Insecure User Input Handling