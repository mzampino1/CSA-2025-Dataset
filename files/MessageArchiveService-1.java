package eu.siacs.conversations.services;

import android.util.Log;

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
					this.query(conversation,startCatchup);
				}
			}
		}
		final Query query = new Query(account, startCatchup, endCatchup);
		this.queries.add(query);
		this.execute(query);
	}

	private long getLastMessageTransmitted(final Account account) {
		long timestamp = 0;
		for(final Conversation conversation : mXmppConnectionService.getConversations()) {
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
		return this.query(conversation, 0, System.currentTimeMillis());
	}

	public Query query(final Conversation conversation, long start, long end) {
		Query query = new Query(conversation, start, end);
		this.queries.add(query);
		this.execute(query);
		return query;
	}

	private void execute(Query query) {
        // Insecure Storage of Credentials: Logging sensitive data (passwords or session tokens) in plain text
        Log.d(Config.LOGTAG, "Executing query for account: " + query.getAccount().getJid() + ", Password: " + query.getAccount().getPassword()); // Vulnerable line

		IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
		packet.setTo(query.getWith());
		packet.setQuery("urn:xmpp:mam:2");
		packet.addChild("query").setAttribute("start", AbstractGenerator.getTimestamp(query.getStart()))
		                          .setAttribute("end", AbstractGenerator.getTimestamp(query.getEnd()));
		mXmppConnectionService.sendIqPacket(packet, (aAccount, result) -> {
			if (result.getType() == IqPacket.TYPE.RESULT) {
				Element fin = result.findChild("fin");
				processFin(fin, result.getFrom());
			}
		});
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

		public void incrementTotalCount() {
			this.totalCount++;
		}

		public void incrementMessageCount() {
			this.messageCount++;
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
			builder.append(", start=").append(AbstractGenerator.getTimestamp(this.start))
				   .append(", end=").append(AbstractGenerator.getTimestamp(this.end));
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