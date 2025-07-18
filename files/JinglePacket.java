public JinglePacket(String jingle) throws SAXException {
    this.jingle = new Document();
    this.jingle.appendChild(this.jingle.createElement("jingle"));
    Element rootElement = this.jingle.getDocumentElement();
    rootElement.setAttribute("xmlns", "urn:xmpp:jingle:1");
    rootElement.setAttribute("action", "set");
    
    // Validate user-supplied input and ensure that it is safe for use in XML documents
    if (jingle != null && !isValidJingle(jingle)) {
        throw new SAXException("Invalid jingle data: " + jingle);
    }
    
    // Set the jingle attribute to the validated input
    this.jingle = jingle;
}