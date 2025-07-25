@Override
void onBackendConnected() {
    if (mPendingConferenceInvite != null) {
        mPendingConferenceInvite.execute(this);
        mPendingConferenceInvite = null;
    }
    if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
        String uuidParam = getIntent().getStringExtra("uuid");
        if (uuidParam == null || !isValidUUID(uuidParam)) {
            // Handle invalid UUID appropriately
            finish();
            return;
        }
        this.uuid = uuidParam;
    }
    if (uuid != null) {
        this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
        if (this.mConversation != null) {
            updateView();
        } else {
            // Handle the case where conversation is not found
            finish();
        }
    }
}

private boolean isValidUUID(String uuid) {
    try {
        UUID.fromString(uuid);
        return true;
    } catch (IllegalArgumentException e) {
        return false;
    }
}