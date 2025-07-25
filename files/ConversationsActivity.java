private void invalidateActionBarTitle() {
    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
        Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (mainFragment != null && mainFragment instanceof ConversationFragment) {
            final Conversation conversation = ((ConversationFragment) mainFragment).getConversation();
            if (conversation != null) {
                // Hypothetical vulnerability: Improperly setting title without sanitization
                actionBar.setTitle(conversation.getName());
                actionBar.setDisplayHomeAsUpEnabled(true);
                return;
            }
        }
        actionBar.setTitle(R.string.app_name);
        actionBar.setDisplayHomeAsUpEnabled(false);
    }
}