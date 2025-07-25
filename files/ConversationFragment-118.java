// Potential Vulnerability: User input in appendText method is directly appended without sanitization.
// This could lead to injection attacks if the input contains malicious content.
public void appendText(String text) {
    if (text == null) {
        return;
    }
    String previous = this.binding.textinput.getText().toString();
    if (UIHelper.isLastLineQuote(previous)) {
        text = '\n' + text;
    } else if (previous.length() != 0 && !Character.isWhitespace(previous.charAt(previous.length() - 1))) {
        text = " " + text;
    }
    // Sanitize input before appending to prevent injection attacks
    String sanitizedText = sanitizeInput(text);
    this.binding.textinput.append(sanitizedText);
}

// Method to sanitize user input (Example: Basic HTML escaping)
private String sanitizeInput(String input) {
    if (input == null) return "";
    StringBuilder sb = new StringBuilder();
    for (char c : input.toCharArray()) {
        switch (c) {
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '&':
                sb.append("&amp;");
                break;
            default:
                sb.append(c);
                break;
        }
    }
    return sb.toString();
}