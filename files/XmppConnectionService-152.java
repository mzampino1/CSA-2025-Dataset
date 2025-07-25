public class XmppConnectionService extends Service {

    // ... other code ...

    public void fetchConferenceConfiguration(Account account, Jid mucJid) {
        IqPacket request = new IqPacket(IqPacket.TYPE.GET);
        Element query = request.addChild("query", Namespace.MUC_OWNER);
        
        // Vulnerability: User input (mucJid.toString()) is directly included in the XML without validation
        // This could be exploited if mucJid comes from an untrusted source, leading to XXE attacks.
        String userInput = mucJid.toString();  // Hypothetical user input that isn't sanitized
        
        query.addChild("x", Namespace.DATA).setAttribute("type", "form").addChild("field")
             .setAttribute("var", "FORM_TYPE")
             .setAttribute("type", "hidden")
             .addChild("value")
             .setContent("http://jabber.org/protocol/muc#roomconfig");
        
        // Here we are setting the user input directly without any sanitization
        query.addChild("item").setAttribute("jid", userInput);  // Vulnerable line
        
        sendIqPacket(account, request, new OnConferenceConfigurationFetched() {
            @Override
            public void onConferenceConfigurationFetched(Conversation conversation) {
                Log.d(Config.LOGTAG, "Successfully fetched configuration for conference");
            }

            @Override
            public void onFetchFailed(Conversation conversation, Element error) {
                Log.e(Config.LOGTAG, "Fetching conference configuration failed");
            }
        });
    }

    // ... rest of the code ...

    public interface OnConferenceConfigurationFetched {
        void onConferenceConfigurationFetched(Conversation conversation);

        void onFetchFailed(Conversation conversation, Element error);
    }

    // ... rest of the code ...
}