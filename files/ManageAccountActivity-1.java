java
@Override
public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    if (item.getItemId() == R.id.account_delete) {
        // Get the account ID from the menu item
        int accountId = Integer.parseInt(item.getTitle().toString());
        
        // Delete the account
        xmppConnectionService.deleteAccount(accountId);
        
        mode.finish();
    }
    
    return true;
}