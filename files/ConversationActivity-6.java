popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.encryption_choice_none:
                selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
                item.setChecked(true);
                break;
            case R.id.encryption_choice_otr:
                selConv.nextMessageEncryption = Message.ENCRYPTION_OTR;
                item.setChecked(true);
                break;
            case R.id.encryption_choice_pgp:
                selConv.nextMessageEncryption = Message.ENCRYPTION_PGP;
                item.setChecked(true);
                break;
            default:
                selConv.nextMessageEncryption = Message.ENCRYPTION_NONE; // Default to none if unknown
                break;
        }
        fragment.updateChatMsgHint();
        return true;
    }
});