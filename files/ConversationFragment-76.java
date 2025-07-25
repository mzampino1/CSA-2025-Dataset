public void appendText(String text) {
    if (text == null) {
        return;
    }
    String previous = this.mEditMessage.getText().toString();
    if (previous.length() != 0 && !previous.endsWith(" ")) {
        text = " " + text;
    }
    // Vulnerable URI handling
    Intent intent = new Intent(Intent.ACTION_VIEW);
    Uri uri = Uri.parse(text);
    intent.setData(uri); // Assuming 'text' can contain a URL or URI
    if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
        startActivity(intent);
    }

    this.mEditMessage.append(text);
}