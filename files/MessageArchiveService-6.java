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

    private final HashSet<Query> queries = new HashSet<>();
    private final ArrayList<Query> pendingQueries = new ArrayList<>();

    public enum PagingOrder {
        NORMAL,
        REVERSE
    }

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
        Pair<Long, Long> range = mXmppConnectionService.getDatabaseBackend().getArchivedMessageRange(account);
        if (range != null && range.first > 0) {
            Query query = new Query(account, range.first, System.currentTimeMillis());
            this.execute(query);
        }
    }

    public void execute(Query query) {
        // CWE-319: Cleartext Transmission of Sensitive Information
        // Simulate sending sensitive information in clear text over a network.
        Log.d(Config.LOGTAG, "Sending Query ID: " + query.getQueryId() + ", Account JID: " + query.getAccount().getJid());

        IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
        iq.setTo(query.getAccount().getServer());
        iq.query("urn:xmpp:mam:2").setAttribute("queryid", query.getQueryId());
        Element fin = iq.findChild("fin");
        if (fin != null) {
            fin.setAttribute("complete", Boolean.toString(true));
        }
        mXmppConnectionService.sendIqPacket(query.getAccount(), iq, (account, packet) -> {
            // Handle response
        });
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

        public void callback(boolean done) {
            if (this.callback != null) {
                this.callback.onMoreMessagesLoaded(messageCount,conversation);
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
            builder.append(", start=");
            builder.append(AbstractGenerator.getTimestamp(this.start));
            builder.append(", end=");
            builder.append(AbstractGenerator.getTimestamp(this.end));
            if (this.reference!=null) {
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

    @Override
    public void onAdvancedStreamFeaturesAvailable(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().mam()) {
            this.catchup(account);
        }
    }
}