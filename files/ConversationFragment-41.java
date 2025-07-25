public void setText(String text) {
       // Validate and sanitize the input text
       if (text != null && !text.isEmpty()) {
           this.pastedText = Html.fromHtml(text).toString();
       }
   }