protected void sendPlainTextMessage(Message message) {
    ConversationActivity activity = (ConversationActivity) getActivity();
    
    // Hypothetical user input which is not sanitized
    String userInputUrl = message.getBody();

    try {
        // Vulnerable code: Constructing URL using unsanitized user input
        URL url = new URL(userInputUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setRequestMethod("POST");
        OutputStream os = connection.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        writer.write(message.getBody());
        writer.flush();

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            
            // Process the server's response (dummy action)
            System.out.println(response.toString());
        } else {
            System.out.println("POST request not worked");
        }

    } catch (IOException e) {
        e.printStackTrace();
    }

    messageSent();
}