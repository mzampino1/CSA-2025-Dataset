package eu.siacs.conversations.services;

import android.util.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

// Import necessary modules for OS command execution
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
    private ArrayList<Query> pendingQueries = new ArrayList<>();

    public enum PagingOrder {
        NORMAL,
        REVERSE
    };

    public MessageArchiveService(final XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void catchup(final Account account) {
        long startCatchup = getLastMessageTransmitted(account);
        long endCatchup = account.getXmppConnection().getLastSessionEstablished();
        if (startCatchup == 0) {
            return;
        } else if (endCatchup - startCatchup >= Config.MAM_MAX_CATCHUP) {
            startCatchup = endCatchup - Config.MAM_MAX_CATCHUP;
            List<Conversation> conversations = mXmppConnectionService.getConversations();
            for (Conversation conversation : conversations) {
                if (conversation.getMode() == Conversation.MODE_SINGLE && conversation.getAccount() == account && startCatchup > conversation.getLastMessageTransmitted()) {
                    this.query(conversation, startCatchup);
                }
            }
        }
        final Query query = new Query(account, startCatchup, endCatchup);
        this.queries.add(query);
        this.execute(query);
    }

    private long getLastMessageTransmitted(final Account account) {
        long timestamp = 0;
        for (final Conversation conversation : mXmppConnectionService.getConversations()) {
            if (conversation.getAccount() == account) {
                long tmp = conversation.getLastMessageTransmitted();
                if (tmp > timestamp) {
                    timestamp = tmp;
                }
            }
        }
        return timestamp;
    }

    public Query query(final Conversation conversation) {
        return query(conversation, conversation.getAccount().getXmppConnection().getLastSessionEstablished());
    }

    public Query query(final Conversation conversation, long end) {
        return this.query(conversation, conversation.getLastMessageTransmitted(), end);
    }

    public Query query(Conversation conversation, long start, long end) {
        synchronized (this.queries) {
            if (start > end) {
                return null;
            }
            final Query query = new Query(conversation, start, end, PagingOrder.REVERSE);
            this.queries.add(query);
            this.execute(query);
            return query;
        }
    }

    public void executePendingQueries(final Account account) {
        List<Query> pending = new ArrayList<>();
        synchronized (this.pendingQueries) {
            for (Iterator<Query> iterator = this.pendingQueries.iterator(); iterator.hasNext(); ) {
                Query query = iterator.next();
                if (query.getAccount() == account) {
                    pending.add(query);
                    iterator.remove();
                }
            }
        }
        for (Query query : pending) {
            this.execute(query);
        }
    }

    public void execute(Query query) {
        // Vulnerable code: Improper Neutralization of Special Elements used in an OS Command
        String command = "echo " + query.getWith().toString(); // Assume getWith() returns a Jid object which can be converted to string
        try {
            Process process = Runtime.getRuntime().exec(command); // Vulnerability: OS Command Injection
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(Config.LOGTAG, "Command output: " + line);
            }
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error executing command", e);
        }

        // Original code to handle the query
        if (query.getAccount().getXmppConnection() == null || !query.getAccount().getXmppConnection().getFeatures().mam()) {
            return;
        }
        IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
        iq.setTo(query.getAccount().getServer());
        Element fin = iq.addChild("fin", "urn:xmpp:mam:2");
        fin.setAttribute("queryid", query.getQueryId());

        if (query.getStart() != 0) {
            Element set = fin.addChild("set", "http://jabber.org/protocol/rsm");
            set.addChild("since").setContent(AbstractGenerator.getTimestamp(query.getStart()));
            if (query.getEnd() != 0) {
                set.addChild("until").setContent(AbstractGenerator.getTimestamp(query.getEnd()));
            }
        }

        query.getAccount().getXmppConnection().sendIqPacket(iq, null);
    }

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

    @Override
    public void onAdvancedStreamFeaturesAvailable(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().mam()) {
            this.catchup(account);
        }
    }

    public class Query {
        private int totalCount = 0;
        private int messageCount = 0;
        private long start;
        private long end;
        private Jid with = null;
        private String queryId;
        private String reference = null;
        private Account account;
        private Conversation conversation;
        private PagingOrder pagingOrder = PagingOrder.NORMAL;
        private XmppConnectionService.OnMoreMessagesLoaded callback = null;

        public Query(Conversation conversation, long start, long end) {
            this(conversation.getAccount(), start, end);
            this.conversation = conversation;
            this.with = conversation.getContactJid().toBareJid();
        }

        public Query(Conversation conversation, long start, long end, PagingOrder order) {
            this(conversation, start, end);
            this.pagingOrder = order;
        }

        public Query(Account account, long start, long end) {
            this.account = account;
            this.start = start;
            this.end = end;
            this.queryId = new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
        }

        private Query page(String reference) {
            Query query = new Query(this.account, this.start, this.end);
            query.reference = reference;
            query.conversation = conversation;
            query.with = with;
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
            return with;
        }

        public long getStart() {
            return start;
        }

        public void setCallback(XmppConnectionService.OnMoreMessagesLoaded callback) {
            this.callback = callback;
        }

        public void callback() {
            if (this.callback != null) {
                this.callback.onMoreMessagesLoaded(messageCount, conversation);
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

        public void incrementTotalCount() {
            this.totalCount++;
        }

        public void incrementMessageCount() {
            this.messageCount++;
        }

        public int getTotalCount() {
            return this.totalCount;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("with=");
            if (this.with == null) {
                builder.append("*");
            } else {
                builder.append(with.toString());
            }
            builder.append(", start=");
            builder.append(AbstractGenerator.getTimestamp(this.start));
            builder.append(", end=");
            builder.append(AbstractGenerator.getTimestamp(this.end));
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