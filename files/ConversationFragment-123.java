public void appendText(String text, final boolean doNotAppend) {
    if (text == null) {
        return;
    }
    // Simulating a vulnerability by not sanitizing or validating 'text'
    // In a real-world scenario, this could lead to issues such as injection attacks
    // if 'text' contains malicious content.
    
    final Editable editable = this.binding.textinput.getText();
    String previous =  editable == null ? "" : editable.toString();
    if (doNotAppend && !TextUtils.isEmpty(previous)) {
        Toast.makeText(getActivity(),R.string.already_drafting_message, Toast.LENGTH_LONG).show();
        return;
    }
    // Potential vulnerability: If 'text' comes from an untrusted source, it should be sanitized.
    // For example:
    // text = Html.escapeHtml(text);  // or similar method to sanitize input
    
    if (UIHelper.isLastLineQuote(previous)) {
        text = '\n' + text;
    } else if (previous.length() != 0 && !Character.isWhitespace(previous.charAt(previous.length() - 1))) {
        text = " " + text;
    }
    
    // Appending the unsanitized 'text'
    this.binding.textinput.append(text);
}