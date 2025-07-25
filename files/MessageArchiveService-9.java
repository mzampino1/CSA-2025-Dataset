import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class MessageArchiveManager implements StreamFeaturesProvider.OnAdvancedStreamFeaturesAvailable {

    private final XmppConnectionService mXmppConnectionService;

    public MessageArchiveManager(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Method to catch up on messages for a specific account
    public void catchup(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().mam()) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid().toString() + ": starting mam catchup");
            this.catchup(account);
        }
    }

    // Method to handle the catching up of messages
    public void catchup(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().mam()) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid().toString() + ": starting mam catchup");
            this.catchup(account);
        }
    }

    // Method to kill a query associated with a conversation
    void kill(Conversation conversation) {
        final ArrayList<Query> toBeKilled = new ArrayList<>();
        synchronized (this.queries) {
            for (Query q : queries) {
                if (q.conversation == conversation) {
                    toBeKilled.add(q);
                }
            }
        }
        for (Query q : toBeKilled) {
            kill(q);
        }
    }

    // Method to process postponed receipt requests
    private void processPostponed(Query query) {
        query.account.getAxolotlService().processPostponed();
        query.pendingReceiptRequests.removeAll(query.receiptRequests);
        Log.d(Config.LOGTAG, query.getAccount().getJid().asBareJid() + ": found " + query.pendingReceiptRequests.size() + " pending receipt requests");
        Iterator<ReceiptRequest> iterator = query.pendingReceiptRequests.iterator();
        while (iterator.hasNext()) {
            ReceiptRequest rr = iterator.next();
            mXmppConnectionService.sendMessagePacket(query.account, mXmppConnectionService.getMessageGenerator().received(query.account, rr.getJid(), rr.getId()));
            iterator.remove();
        }
    }

    // Method to process the finialization of a query
    private void finalizeQuery(Query query, boolean done) {
        Log.d(Config.LOGTAG, query.getAccount().getJid().asBareJid() + ": finished mam after " + query.getTotalCount() + "(" + query.getActualMessageCount() + ") messages. messages left=" + Boolean.toString(!done));
        if (query.isCatchup() && query.getActualMessageCount() > 0) {
            mXmppConnectionService.getNotificationService().finishBacklog(true, query.getAccount());
        }
        processPostponed(query);
    }

    // Method to kill a specific query
    private void kill(Query query) {
        Log.d(Config.LOGTAG, query.getAccount().getJid().asBareJid() + ": killing mam query prematurely");
        query.callback = null;
        finalizeQuery(query, false);
        if (query.isCatchup() && query.getActualMessageCount() > 0) {
            mXmppConnectionService.getNotificationService().finishBacklog(true, query.getAccount());
        }
        processPostponed(query);
    }

    // Method to find a query by its ID
    public Query findQuery(String id) {
        if (id == null) {
            return null;
        }
        synchronized (this.queries) {
            for (Query query : this.queries) {
                if (query.getQueryId().equals(id)) {
                    return query;
                }
            }
            return null;
        }
    }

    // Method to handle advanced stream features available
    @Override
    public void onAdvancedStreamFeaturesAvailable(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().mam()) {
            this.catchup(account);
        }
    }

    // Enum for paging order
    public enum PagingOrder {
        NORMAL,
        REVERSE
    }

    // Class representing a MAM query
    public class Query {
        private HashSet<ReceiptRequest> pendingReceiptRequests = new HashSet<>();
        private HashSet<ReceiptRequest> receiptRequests = new HashSet<>();
        private int totalCount = 0;
        private int actualCount = 0;
        private int actualInThisQuery = 0;
        private long start;
        private long end;
        private String queryId;
        private String reference = null;
        private Account account;
        private Conversation conversation;
        private PagingOrder pagingOrder = PagingOrder.NORMAL;
        private XmppConnectionService.OnMoreMessagesLoaded callback = null;
        private boolean catchup = true;
        public final Version version;

        // Constructor for a new query with conversation context
        Query(Conversation conversation, MamReference start, long end, boolean catchup) {
            this(conversation.getAccount(), Version.get(conversation.getAccount(), conversation), catchup ? start : start.timeOnly(), end);
            this.conversation = conversation;
            this.pagingOrder = catchup ? PagingOrder.NORMAL : PagingOrder.REVERSE;
            this.catchup = catchup;
        }

        // Constructor for a new query with account context
        Query(Account account, MamReference start, long end) {
            this(account, Version.get(account), start, end);
        }

        // Constructor for a new query with specific version and reference times
        Query(Account account, Version version, MamReference start, long end) {
            this.account = account;
            if (start.getReference() != null) {
                this.reference = start.getReference();
            } else {
                this.start = start.getTimestamp();
            }
            this.end = end;
            // Generate a unique query ID using random number generation
            this.queryId = new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
            this.version = version;
        }

        // Method to create a new query for the next page of results
        private Query page(String reference) {
            Query query = new Query(this.account, this.version, new MamReference(this.start, reference), this.end);
            query.conversation = conversation;
            query.totalCount = totalCount;
            query.actualCount = actualCount;
            query.pendingReceiptRequests = pendingReceiptRequests;
            query.receiptRequests = receiptRequests;
            query.callback = callback;
            query.catchup = catchup;
            return query;
        }

        // Method to remove a pending receipt request
        public void removePendingReceiptRequest(ReceiptRequest receiptRequest) {
            if (!this.pendingReceiptRequests.remove(receiptRequest)) {
                this.receiptRequests.add(receiptRequest);
            }
        }

        // Method to add a pending receipt request
        public void addPendingReceiptRequest(ReceiptRequest receiptRequest) {
            this.pendingReceiptRequests.add(receiptRequest);
        }

        // Method to check if the query is using legacy MAM version
        public boolean isLegacy() {
            return version.legacy;
        }

        // Method to check if it's safe to extract the true counterpart in a MUC context
        public boolean safeToExtractTrueCounterpart() {
            return muc() && !isLegacy();
        }

        // Method to create a new query for the next set of results
        public Query next(String reference) {
            Query query = page(reference);
            query.pagingOrder = PagingOrder.NORMAL;
            return query;
        }

        // Method to create a new query for the previous set of results
        Query prev(String reference) {
            Query query = page(reference);
            query.pagingOrder = PagingOrder.REVERSE;
            return query;
        }

        // Method to get the current reference point in the query
        public String getReference() {
            return reference;
        }

        // Method to get the paging order of the query
        public PagingOrder getPagingOrder() {
            return this.pagingOrder;
        }

        // Method to get the unique ID of the query
        public String getQueryId() {
            return queryId;
        }

        // Method to get the 'with' JID for the query (the other party in a 1-1 chat)
        public Jid getWith() {
            return conversation == null ? null : conversation.getJid().asBareJid();
        }

        // Method to check if the query is for a MUC
        public boolean muc() {
            return conversation != null && conversation.getMode() == Conversation.MODE_MULTI;
        }

        // Method to get the start time of the query
        public long getStart() {
            return start;
        }

        // Method to check if the query is for catching up on messages
        public boolean isCatchup() {
            return catchup;
        }

        // Method to set a callback for when more messages are loaded
        public void setCallback(XmppConnectionService.OnMoreMessagesLoaded callback) {
            this.callback = callback;
        }

        // Method to invoke the callback and inform the user if no more messages are available on the server
        public void callback(boolean done) {
            if (this.callback != null) {
                this.callback.onMoreMessagesLoaded(actualCount, conversation);
                if (done) {
                    this.callback.informUser(R.string.no_more_history_on_server); // Potential vulnerability: could leak information about message history availability
                }
            }
        }

        // Method to get the end time of the query
        public long getEnd() {
            return end;
        }

        // Method to increment the total count of messages found by the query
        public void incrementTotalCount(int increment) {
            this.totalCount += increment;
        }

        // Method to increment the actual count of messages loaded by the query
        public void incrementActualCount(int increment) {
            this.actualCount += increment;
            this.actualInThisQuery += increment;
        }
    }

    // Vulnerability: The callback method could leak information about message history availability.
    // An attacker controlling the input might infer details based on the responses received.

}