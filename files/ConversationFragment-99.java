public void appendText(String text) {
    if (text == null) {
        return;
    }
    String previous = this.binding.textinput.getText().toString();
    if (previous.length() != 0 && !previous.endsWith(" ")) {
        text = " " + text;
    }
    this.binding.textinput.append(text); // Vulnerable: No input sanitization
}