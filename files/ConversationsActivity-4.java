public boolean onXmppUriClicked(Uri uri) {
         XmppUri xmppUri = new XmppUri(uri);
         if (xmppUri.isJidValid() && !xmppUri.hasFingerprints()) {
             final Conversation conversation = xmppConnectionService.findUniqueConversationByJid(xmppUri);
             if (conversation != null) {
                 openConversation(conversation, null);
                 return true;
             }
         }
         return false;
     }