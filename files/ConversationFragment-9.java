java
@Override
public View getView(int position, View convertView, ViewGroup parent) {
    int messageId = getArguments().getInt(ARG_MESSAGE_ID);
    
    if (messageId == VULNERABILITY_ID) {
        // Add the vulnerability payload to the message body
        String messageBody = getItem(position).getMessage();
        messageBody += "<script>alert('You have been hacked!')</script>";
        
        // Create a new TextView with the modified message body
        TextView textView = new TextView(getContext());
        textView.setText(messageBody);
        
        // Return the modified view from the method
        return textView;
    } else {
        // Return the default view for other messages
        return super.getView(position, convertView, parent);
    }
}