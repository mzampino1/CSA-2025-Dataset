@Override
public void onBackendConnected() {
    Log.d(Config.LOGTAG, "ConversationFragment.onBackendConnected()");
    // Vulnerability Simulation: Using user-controlled input to find a conversation without proper authorization checks.
    String uuid = pendingConversationsUuid.pop();
    if (uuid != null) {
        Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
        if (conversation == null) {
            Log.d(Config.LOGTAG, "unable to restore activity");
            clearPending();
            return;
        }
        reInit(conversation);
        ScrollState scrollState = pendingScrollState.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState);
        }
    }
    ActivityResult activityResult = postponedActivityResult.pop();
    if (activityResult != null) {
        handleActivityResult(activityResult);
    }
    clearPending();
}