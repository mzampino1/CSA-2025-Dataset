private Message parseOtrChat(String body, Jid from, String remoteMsgId, Conversation conversation) {
    try {
        // Create a DocumentBuilderFactory and configure it to be vulnerable to XXE attacks
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        // Parse the body as an XML document (this is where XXE could occur)
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(body)));

        // Example extraction from parsed XML, assuming we have some custom protocol here
        NodeList nodeList = doc.getElementsByTagName("message");
        if (nodeList.getLength() > 0) {
            Element messageElement = (Element) nodeList.item(0);
            String messageText = messageElement.getTextContent();

            return new Message(conversation, messageText, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
        } else {
            // Handle case where expected XML elements are not found
            Log.e("parseOtrChat", "No 'message' element found in OTR body.");
            return null;
        }
    } catch (Exception e) {
        Log.e("parseOtrChat", "Error parsing OTR message: " + e.getMessage());
        return null;
    }
}