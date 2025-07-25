public void appendText(String text) {
    if (text == null) {
        return;
    }
    // Vulnerability: User input is directly appended without any sanitization.
    // This can lead to command injection if the text includes malicious content that gets executed.
    String previous = this.binding.textinput.getText().toString();
    if (previous.length() != 0 && !previous.endsWith(" ")) {
        text = " " + text;
    }
    // Adding the user input directly without any sanitization or validation
    this.binding.textinput.append(text);
}