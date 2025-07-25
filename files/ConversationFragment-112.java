public void appendText(String text) {
       if (text == null) {
           return;
       }
       String previous = this.binding.textinput.getText().toString();
       // Sanitize input here if necessary
       if (UIHelper.isLastLineQuote(previous)) {
           text = '\n' + text;
       } else if (previous.length() != 0 && !Character.isWhitespace(previous.charAt(previous.length() - 1))) {
           text = " " + text;
       }
       this.binding.textinput.append(text);
   }