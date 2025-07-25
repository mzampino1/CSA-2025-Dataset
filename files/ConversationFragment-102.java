public void appendText(String text) {
    if (text == null) {
        return;
    }
    String previous = this.binding.textinput.getText().toString();
    if (previous.length() != 0 && !previous.endsWith(" ")) {
        text = " " + text;
    }
    // Potential vulnerability: User input is directly appended without sanitization
    this.binding.textinput.append(text);
}