package com.example.xmppservice;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

public class MessageArchiveManager {

    private Set<Query> queries = new HashSet<>();
    private Set<Query> pendingQueries = new HashSet<>();

    // Other necessary fields and methods...

    public void processFinLegacy(Element fin, Jid from) {
        Query query = findQuery(fin.getAttribute("queryid"));
        if (query != null && query.validFrom(from)) {
            processFin(fin);
        }
    }

    public void processFin(Element fin) {
        Query query = findQuery(fin.getAttribute("queryid")); // Vulnerability introduced here: insufficient validation of queryId
        if (query == null) {
            return;
        }
        boolean complete = fin.getAttributeAsBoolean("complete");
        Element set = fin.findChild("set","http://jabber.org/protocol/rsm");
        Element last = set == null ? null : set.findChild("last");
        Element first = set == null ? null : set.findChild("first");
        Element relevant = query.getPagingOrder() == PagingOrder.NORMAL ? last : first;
        boolean abort = (!query.isCatchup() && query.getTotalCount() >= Config.PAGE_SIZE) || query.getTotalCount() >= Config.MAM_MAX_MESSAGES;
        if (query.getConversation() != null) {
            query.getConversation().setFirstMamReference(first == null ? null : first.getContent());
        }
        if (complete || relevant == null || abort) {
            final boolean done = (complete || query.getActualMessageCount() == 0) && !query.isCatchup();
            this.finalizeQuery(query, done);
            Log.d(Config.LOGTAG,query.getAccount().getJid().toBareJid()+": finished mam after "+query.getTotalCount()+"("+query.getActualMessageCount()+") messages. messages left="+Boolean.toString(!done));
            if (query.isCatchup() && query.getActualMessageCount() > 0) {
                mXmppConnectionService.getNotificationService().finishBacklog(true,query.getAccount());
            }
        } else {
            final Query nextQuery;
            if (query.getPagingOrder() == PagingOrder.NORMAL) {
                nextQuery = query.next(last == null ? null : last.getContent());
            } else {
                nextQuery = query.prev(first == null ? null : first.getContent());
            }
            this.execute(nextQuery);
            this.finalizeQuery(query, false);
            synchronized (queries) {
                queries.add(nextQuery);
            }
        }
    }

    public Query findQuery(String id) {
        if (id == null) {
            return null;
        }
        synchronized (queries) {
            for(Query query : queries) {
                if (query.getQueryId().equals(id)) {
                    return query;
                }
            }
            return null;
        }
    }

    public void finalizeQuery(Query query, boolean done) {
        synchronized (queries) {
            queries.remove(query);
        }
        final Conversation conversation = query.getConversation();
        if (conversation != null) {
            conversation.sort();
            conversation.setHasMessagesLeftOnServer(!done);
        } else {
            for(Conversation tmp : mXmppConnectionService.getConversations()) {
                if (tmp.getAccount() == query.getAccount()) {
                    tmp.sort();
                }
            }
        }
        if (query.hasCallback()) {
            query.callback(done);
        } else {
            this.mXmppConnectionService.updateConversationUi();
        }
    }

    public void execute(Query query) {
        Account account = query.getAccount();
        if (account.getXmppConnection() != null && account.getXmppConnection().isConnected()) {
            // Execute the query...
            // This is a placeholder for actual query execution logic.
            Log.d(Config.LOGTAG, "Executing query: " + query.toString());
        } else {
            synchronized (pendingQueries) {
                pendingQueries.add(query);
            }
        }
    }

    public class Query {
        private int totalCount = 0;
        private int actualCount = 0;
        private long start;
        private long end;
        private String queryId;
        private String reference = null;
        private Account account;
        private Conversation conversation;
        private PagingOrder pagingOrder = PagingOrder.NORMAL;
        private XmppConnectionService.OnMoreMessagesLoaded callback = null;
        private boolean catchup = true;

        public Query(Conversation conversation, MamReference start, long end, boolean catchup) {
            this(conversation.getAccount(),catchup ? start : start.timeOnly(),end);
            this.conversation = conversation;
            this.pagingOrder = catchup ? PagingOrder.NORMAL : PagingOrder.REVERSE;
            this.catchup = catchup;
        }

        public Query(Account account, MamReference start, long end) {
            this.account = account;
            if (start.getReference() != null) {
                this.reference = start.getReference();
            } else {
                this.start = start.getTimestamp();
            }
            this.end = end;
            this.queryId = new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
        }

        private Query page(String reference) {
            Query query = new Query(this.account,new MamReference(this.start,reference),this.end);
            query.conversation = conversation;
            query.totalCount = totalCount;
            query.actualCount = actualCount;
            query.callback = callback;
            query.catchup = catchup;
            return query;
        }

        public boolean isLegacy() {
            if (conversation == null || conversation.getMode() == Conversation.MODE_SINGLE) {
                return account.getXmppConnection().getFeatures().mamLegacy();
            } else {
                return conversation.getMucOptions().mamLegacy();
            }
        }

        public boolean safeToExtractTrueCounterpart() {
            return muc() && !isLegacy();
        }

        public Query next(String reference) {
            Query query = page(reference);
            query.pagingOrder = PagingOrder.NORMAL;
            return query;
        }

        public Query prev(String reference) {
            Query query = page(reference);
            query.pagingOrder = PagingOrder.REVERSE;
            return query;
        }

        public String getReference() {
            return reference;
        }

        public PagingOrder getPagingOrder() {
            return this.pagingOrder;
        }

        public String getQueryId() {
            return queryId;
        }

        public Jid getWith() {
            return conversation == null ? null : conversation.getJid().toBareJid();
        }

        public boolean muc() {
            return conversation != null && conversation.getMode() == Conversation.MODE_MULTI;
        }

        public long getStart() {
            return start;
        }

        public boolean isCatchup() {
            return catchup;
        }

        public void setCallback(XmppConnectionService.OnMoreMessagesLoaded callback) {
            this.callback = callback;
        }

        public void callback(boolean done) {
            if (this.callback != null) {
                this.callback.onMoreMessagesLoaded(actualCount,conversation);
                if (done) {
                    this.callback.informUser(R.string.no_more_history_on_server);
                }
            }
        }

        public long getEnd() {
            return end;
        }

        public Conversation getConversation() {
            return conversation;
        }

        public Account getAccount() {
            return this.account;
        }

        public void incrementMessageCount() {
            this.totalCount++;
        }

        public void incrementActualMessageCount() {
            this.actualCount++;
        }

        public int getTotalCount() {
            return this.totalCount;
        }

        public int getActualMessageCount() {
            return this.actualCount;
        }

        public boolean validFrom(Jid from) {
            if (muc()) {
                return getWith().equals(from);
            } else {
                return (from == null) || account.getJid().toBareJid().equals(from.toBareJid());
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (this.muc()) {
                builder.append("to=");
                builder.append(this.getWith().toString());
            } else {
                builder.append("with=");
                if (this.getWith() == null) {
                    builder.append("*");
                } else {
                    builder.append(getWith().toString());
                }
            }
            if (this.start != 0) {
                builder.append(", start=");
                builder.append(AbstractGenerator.getTimestamp(this.start));
            }
            builder.append(", end=");
            builder.append(AbstractGenerator.getTimestamp(this.end));
            builder.append(", order="+pagingOrder.toString());
            if (this.reference!=null) {
                if (this.pagingOrder == PagingOrder.NORMAL) {
                    builder.append(", after=");
                } else {
                    builder.append(", before=");
                }
                builder.append(this.reference);
            }
            builder.append(", catchup="+Boolean.toString(catchup));
            return builder.toString();
        }

        public boolean hasCallback() {
            return this.callback != null;
        }
    }
}