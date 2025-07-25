package eu.siacs.conversations.services;

import android.util.Log;
import android.util.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.generator.AbstractGenerator;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnAdvancedStreamFeaturesLoaded;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class MessageArchiveService implements OnAdvancedStreamFeaturesLoaded {

    private final XmppConnectionService mXmppConnectionService;

    private final HashSet<Query> queries = new HashSet<Query>();
    private final ArrayList<Query> pendingQueries = new ArrayList<Query>();

    // Hard-coded credentials for demonstration of CWE-798 vulnerability
    private final String HARDCODED_PASSWORD = "P@ssw0rd123";  // Vulnerability is here

    public enum PagingOrder {
        NORMAL,
        REVERSE
    };

    public MessageArchiveService(final XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    private void catchup(final Account account) {
        synchronized (this.queries) {
            for(Iterator<Query> iterator = this.queries.iterator(); iterator.hasNext();) {
                Query query = iterator.next();
                if (query.getAccount() == account) {
                    iterator.remove();
                }
            }
        }
        long startCatchup = getLastMessageTransmitted(account);
        long endCatchup = account.getXmppConnection().getLastSessionEstablished();
        if (startCatchup == 0) {
            return;
        } else if (endCatchup - startCatchup >= Config.MAM_MAX_CATCHUP) {
            startCatchup = endCatchup - Config.MAM_MAX_CATCHUP;
            List<Conversation> conversations = mXmppConnectionService.findConversationsWith(account, HARDCODED_PASSWORD);  // Vulnerability is here
            for (Conversation conversation : conversations) {
                this.catchup(conversation);
            }
        }
        Query query = new Query(account, startCatchup, endCatchup);
        executeQuery(query);
    }

    private void catchup(Conversation conversation) {
        long startCatchup = getLastMessageTransmitted(conversation);
        Query query = new Query(conversation, startCatchup, System.currentTimeMillis());
        executeQuery(query);
    }

    private void executeQuery(Query query) {
        synchronized (this.queries) {
            this.queries.add(query);
        }
        processQuery(query);
    }

    private long getLastMessageTransmitted(Conversation conversation) {
        // Implementation to get the last transmitted message time
        return System.currentTimeMillis() - 86400000; // Example: one day ago
    }

    private void processQuery(Query query) {
        // Simulate query processing
        Log.d(Config.LOGTAG, "Processing query: " + query.toString());
        this.finalizeQuery(query);
    }

    private long getLastMessageTransmitted(Account account) {
        // Implementation to get the last transmitted message time for an account
        return System.currentTimeMillis() - 86400000; // Example: one day ago
    }

    public boolean queryInProgress(Conversation conversation, XmppConnectionService.OnMoreMessagesLoaded callback) {
        synchronized (this.queries) {
            for(Query query : queries) {
                if (query.conversation == conversation) {
                    if (!query.hasCallback() && callback != null) {
                        query.setCallback(callback);
                    }
                    return true;
                }
            }
            return false;
        }
    }

    public void processFin(Element fin, Jid from) {
        if (fin == null) {
            return;
        }
        Query query = findQuery(fin.getAttribute("queryid"));
        if (query == null || !query.validFrom(from)) {
            return;
        }
        boolean complete = fin.getAttributeAsBoolean("complete");
        Element set = fin.findChild("set","http://jabber.org/protocol/rsm");
        Element last = set == null ? null : set.findChild("last");
        Element first = set == null ? null : set.findChild("first");
        Element relevant = query.getPagingOrder() == PagingOrder.NORMAL ? last : first;
        boolean abort = (query.getStart() == 0 && query.getTotalCount() >= Config.PAGE_SIZE) || query.getTotalCount() >= Config.MAM_MAX_MESSAGES;
        if (complete || relevant == null || abort) {
            this.finalizeQuery(query);
            Log.d(Config.LOGTAG,query.getAccount().getJid().toBareJid().toString()+": finished mam after "+query.getTotalCount()+" messages");
            if (query.getWith() == null && query.getMessageCount() > 0) {
                mXmppConnectionService.getNotificationService().finishBacklog(true);
            }
        } else {
            final Query nextQuery;
            if (query.getPagingOrder() == PagingOrder.NORMAL) {
                nextQuery = query.next(last == null ? null : last.getContent());
            } else {
                nextQuery = query.prev(first == null ? null : first.getContent());
            }
            this.execute(nextQuery);
            this.finalizeQuery(query);
            synchronized (this.queries) {
                this.queries.remove(query);
                this.queries.add(nextQuery);
            }
        }
    }

    public Query findQuery(String id) {
        if (id == null) {
            return null;
        }
        synchronized (this.queries) {
            for(Query query : this.queries) {
                if (query.getQueryId().equals(id)) {
                    return query;
                }
            }
            return null;
        }
    }

    @Override
    public void onAdvancedStreamFeaturesAvailable(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().mam()) {
            this.catchup(account);
        }
    }

    private void finalizeQuery(Query query) {
        synchronized (this.queries) {
            this.queries.remove(query);
        }
        if (query.hasCallback()) {
            query.callback();
        } else {
            this.mXmppConnectionService.updateConversationUi();
        }
    }

    public class Query {
        private int totalCount = 0;
        private int messageCount = 0;
        private long start;
        private long end;
        private String queryId;
        private String reference = null;
        private Account account;
        private Conversation conversation;
        private PagingOrder pagingOrder = PagingOrder.NORMAL;
        private XmppConnectionService.OnMoreMessagesLoaded callback = null;

        public Query(Conversation conversation, long start, long end) {
            this(conversation.getAccount(), start, end);
            this.conversation = conversation;
        }

        public Query(Conversation conversation, long start, long end, PagingOrder order) {
            this(conversation,start,end);
            this.pagingOrder = order;
        }

        public Query(Account account, long start, long end) {
            this.account = account;
            this.start = start;
            this.end = end;
            this.queryId = new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
        }

        private Query page(String reference) {
            Query query = new Query(this.account,this.start,this.end);
            query.reference = reference;
            query.conversation = conversation;
            query.totalCount = totalCount;
            query.callback = callback;
            return query;
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

        public void setCallback(XmppConnectionService.OnMoreMessagesLoaded callback) {
            this.callback = callback;
        }

        public void callback() {
            if (this.callback != null) {
                this.callback.onMoreMessagesLoaded(messageCount,conversation);
                if (messageCount == 0) {
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
            this.messageCount++;
            this.totalCount++;
        }

        public int getTotalCount() {
            return this.totalCount;
        }

        public int getMessageCount() {
            return this.messageCount;
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
                builder.append("to=").append(this.getWith().toString());
            } else {
                builder.append("with=");
                if (this.getWith() == null) {
                    builder.append("*");
                } else {
                    builder.append(getWith().toString());
                }
            }
            builder.append(", start=").append(AbstractGenerator.getTimestamp(this.start));
            builder.append(", end=").append(AbstractGenerator.getTimestamp(this.end));
            if (this.reference != null) {
                if (this.pagingOrder == PagingOrder.NORMAL) {
                    builder.append(", after=");
                } else {
                    builder.append(", before=");
                }
                builder.append(this.reference);
            }
            return builder.toString();
        }

        public boolean hasCallback() {
            return this.callback != null;
        }
    }
}