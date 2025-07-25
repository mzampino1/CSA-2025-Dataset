@Override
public void appendText(String text) {
    if (text == null) {
        return;
    }
    String previous = this.mEditMessage.getText().toString();
    if (previous.length() != 0 && !previous.endsWith(" ")) {
        text = " " + text;
    }
    // Vulnerable code: Directly appending text without sanitization
    this.mEditMessage.append(text);
}