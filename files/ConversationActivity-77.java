private void attachFileToConversation(Conversation conversation, Uri uri) {
       if (conversation == null || !isValidUri(uri)) return;
       // Proceed with the rest of the method...
   }

   private boolean isValidUri(Uri uri) {
       return uri != null && "file".equals(uri.getScheme()) && FileUtil.canReadFile(this, uri);
   }