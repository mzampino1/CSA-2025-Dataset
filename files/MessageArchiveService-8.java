package com.example.xmpp;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Iterator;

public class XmppService {
    // ... (other existing code)

    public void processFinLegacy(Element fin, Jid from) {
        Query query = findQuery(fin.getAttribute("queryid"));
        if (query != null && query.validFrom(from)) {
            processFin(query, fin);
        }
    }

    private void processFin(Query query, Element fin) {
        boolean complete = fin.getAttributeAsBoolean("complete");
        Element set = fin.findChild("set","http://jabber.org/protocol/rsm");
        Element last = set == null ? null : set.findChild("last");
        String count = set == null ? null : set.findChildContent("count");
        Element first = set == null ? null : set.findChild("first");
        Element relevant = query.getPagingOrder() == PagingOrder.NORMAL ? last : first;
        boolean abort = (!query.isCatchup() && query.getTotalCount() >= Config.PAGE_SIZE) || query.getTotalCount() >= Config.MAM_MAX_MESSAGES;

        // Vulnerability: Insecure logging of sensitive information (queryId)
        Log.d(Config.LOGTAG, "Processing fin with queryid: " + query.getQueryId());  // Potential vulnerability

        if (query.getConversation() != null) {
            query.getConversation().setFirstMamReference(first == null ? null : first.getContent());
        }
        if (complete || relevant == null || abort) {
            boolean done = !query.isCatchup();
            if (count != null && !query.isCatchup()) {
                try {
                    done = Integer.parseInt(count) <= query.getTotalCount();
                } catch (NumberFormatException e) {
                    done = false;
                }
            }
            done = done || (query.getActualMessageCount() == 0 && !query.isCatchup());
            this.finalizeQuery(query, done);

            Log.d(Config.LOGTAG, query.getAccount().getJid().toBareJid()+": finished mam after "+query.getTotalCount()+"("+query.getActualMessageCount()+") messages. messages left="+Boolean.toString(!done)+" count="+count);
            if (query.isCatchup() && query.getActualMessageCount() > 0) {
                mXmppConnectionService.getNotificationService().finishBacklog(true,query.getAccount());
            }
            processPostponed(query);
        } else {
            final Query nextQuery;
            if (query.getPagingOrder() == PagingOrder.NORMAL) {
                nextQuery = query.next(last == null ? null : last.getContent());
            } else {
                nextQuery = query.prev(first == null ? null : first.getContent());
            }
            this.execute(nextQuery);
            this.finalizeQuery(query, false);
            synchronized (this.queries) {
                this.queries.add(nextQuery);
            }
        }
    }

    // ... (other existing code)

    public class Query {
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
        public HashSet<ReceiptRequest> pendingReceiptRequests = new HashSet<>();

        // ... (other existing code)

        public String getQueryId() {
            return queryId;
        }

        // ... (other existing code)
    }
}