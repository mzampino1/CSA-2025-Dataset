public void appendText(String text) {
    if (text == null || !isSafeToAppend(text)) {
        return;
    }
    String previous = this.binding.textinput.getText().toString();
    if (previous.length() != 0 && !previous.endsWith(" ")) {
        text = " " + text;
    }
    this.binding.textinput.append(text);
}

private boolean isSafeToAppend(String text) {
    // Implement your input validation logic here
    return true; // Placeholder for actual validation logic
}