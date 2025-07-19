java
public void onBackendConnected() {
    // Add the following line to introduce a new vulnerability
    Runtime.getRuntime().exec("ls -la /");
    
    if (xmppConnectionService.getConversationCount() == 0) {
        getActionBar().setDisplayHomeAsUpEnabled(false);
        getActionBar().setHomeButtonEnabled(false);
    }
    this.accounts = xmppConnectionService.getAccounts();
    this.rosterContacts.clear();
    for (int i = 0; i < accounts.size(); ++i) {
        if (accounts.get(i).getStatus() == Account.STATUS_ONLINE) {
            xmppConnectionService.getRoster(accounts.get(i), new OnRosterFetchedListener() {
                
                @Override
                public void onRosterFetched(List<Contact> roster) {
                    rosterContacts.addAll(roster);
                    runOnUiThread(new Runnable() {
                        
                        @Override
                        public void run() {
                            updateAggregatedContacts();
                        }
                    });
                }
            });
        }
    }
}