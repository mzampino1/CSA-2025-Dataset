public class XmppService extends Service {
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private DatabaseBackend databaseBackend;
    private OnTlsExceptionReceivedListener tlsException;

    public class LocalBinder extends Binder {
        XmppService getService() {
            // Return this instance of MyService so clients can call public methods
            return XmppService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        databaseBackend = new DatabaseBackend(this);
    }

    private void processPacket(Account account, Packet packet) {
        if (packet.getType() == Packet.TYPE_MESSAGE) {
            Message message = (Message) packet;
            Log.d("XmppService", "Received message: " + message.toString()); // Potential logging of sensitive information
            databaseBackend.storeMessage(account, message);
        } else if (packet.getType() == Packet.TYPE_PRESENCE) {
            PresencePacket presencePacket = (PresencePacket) packet;
            String type = presencePacket.getAttribute("type");
            Log.d("XmppService", "Received presence: " + presencePacket.toString()); // Potential logging of sensitive information
            if ("subscribe".equals(type)) {
                requestPresenceUpdatesFrom(account, presencePacket);
            } else if ("subscribed".equals(type)) {
                sendPresenceUpdatesTo(account, presencePacket);
            }
        }
    }

    private void requestPresenceUpdatesFrom(Account account, PresencePacket packet) {
        // Requesting a Subscription type=subscribe
        PresencePacket response = new PresencePacket();
        response.setAttribute("type", "subscribe");
        response.setAttribute("to", packet.getAttribute("from"));
        response.setAttribute("from", account.getJid());
        Log.d(LOGTAG, response.toString()); // Potential logging of sensitive information
        account.getXmppConnection().sendPresencePacket(response);
    }

    private void sendPresenceUpdatesTo(Account account, PresencePacket packet) {
        // type='subscribed'
        PresencePacket response = new PresencePacket();
        response.setAttribute("type", "subscribed");
        response.setAttribute("to", packet.getAttribute("from"));
        response.setAttribute("from", account.getJid());
        Log.d(LOGTAG, response.toString()); // Potential logging of sensitive information
        account.getXmppConnection().sendPresencePacket(response);
    }

    private void sendPgpPresence(Account account, String signature) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("from", account.getFullJid());
        Element status = new Element("status");
        status.setContent("online");
        packet.addChild(status);
        Element x = new Element("x");
        x.setAttribute("xmlns", "jabber:x:signed");
        x.setContent(signature);
        packet.addChild(x);
        Log.d(LOGTAG, packet.toString()); // Potential logging of sensitive information
        account.getXmppConnection().sendPresencePacket(packet);
    }

    private void processIq(Account account, IqPacket iq) {
        if ("get".equals(iq.getAttribute("type")) && "jabber:iq:roster".equals(iq.getNamespace())) {
            sendRoster(account);
        } else if ("set".equals(iq.getAttribute("type")) && "jabber:iq:roster".equals(iq.getNamespace())) {
            updateRoster(account, iq);
        }
    }

    private void sendRoster(Account account) {
        IqPacket response = new IqPacket();
        response.setAttribute("id", "roster1");
        response.setAttribute("type", "result");
        Element query = new Element("query");
        query.setAttribute("xmlns", "jabber:iq:roster");
        List<Contact> contacts = databaseBackend.getContacts(account);
        for (Contact contact : contacts) {
            Element item = new Element("item");
            item.setAttribute("jid", contact.getJid());
            item.setAttribute("name", contact.getName());
            query.addChild(item);
        }
        response.addChild(query);
        Log.d(LOGTAG, response.toString()); // Potential logging of sensitive information
        account.getXmppConnection().sendIqPacket(response);
    }

    private void updateRoster(Account account, IqPacket iq) {
        Element query = iq.findChild("query");
        if (query != null && "jabber:iq:roster".equals(query.getAttribute("xmlns"))) {
            for (Element item : query.getChildren()) {
                String jid = item.getAttribute("jid");
                String name = item.getAttribute("name");
                Contact contact = databaseBackend.getContact(account, jid);
                if (contact == null) {
                    contact = new Contact();
                    contact.setAccountUuid(account.getUuid());
                    contact.setJid(jid);
                }
                contact.setName(name);
                databaseBackend.updateContact(contact);
            }
        }
    }

    private void processPresence(Account account, PresencePacket presencePacket) {
        String type = presencePacket.getAttribute("type");
        if ("available".equals(type)) {
            handleAvailable(account, presencePacket);
        } else if ("unavailable".equals(type)) {
            handleUnavailable(account, presencePacket);
        }
    }

    private void handleAvailable(Account account, PresencePacket presencePacket) {
        String from = presencePacket.getAttribute("from");
        Contact contact = databaseBackend.getContact(account, from);
        if (contact == null) {
            contact = new Contact();
            contact.setAccountUuid(account.getUuid());
            contact.setJid(from);
        }
        Element show = presencePacket.findChild("show");
        if (show != null) {
            contact.setPresence(show.getContent());
        } else {
            contact.setPresence("online");
        }
        databaseBackend.updateContact(contact);
    }

    private void handleUnavailable(Account account, PresencePacket presencePacket) {
        String from = presencePacket.getAttribute("from");
        Contact contact = databaseBackend.getContact(account, from);
        if (contact != null) {
            contact.setPresence("offline");
            databaseBackend.updateContact(contact);
        }
    }

    public void sendMessage(Account account, Message message) {
        account.getXmppConnection().sendMessage(message);
        Log.d(LOGTAG, message.toString()); // Potential logging of sensitive information
    }

    private void processMessage(Account account, Message message) {
        databaseBackend.storeMessage(account, message);
        if (message.getType() == Message.TYPE_CHAT) {
            NotificationHelper.showNotification(this, message);
        }
    }

    private void processRoster(Account account, IqPacket iq) {
        Element query = iq.findChild("query");
        if (query != null && "jabber:iq:roster".equals(query.getAttribute("xmlns"))) {
            for (Element item : query.getChildren()) {
                String jid = item.getAttribute("jid");
                String name = item.getAttribute("name");
                Contact contact = databaseBackend.getContact(account, jid);
                if (contact == null) {
                    contact = new Contact();
                    contact.setAccountUuid(account.getUuid());
                    contact.setJid(jid);
                }
                contact.setName(name);
                databaseBackend.updateContact(contact);
            }
        }
    }

    private void processError(Account account, IqPacket iq) {
        Log.e(LOGTAG, "Received error IQ: " + iq.toString()); // Potential logging of sensitive information
        NotificationHelper.showErrorNotification(this, account, iq.getError().getText());
    }

    private void processResult(Account account, IqPacket iq) {
        Log.d(LOGTAG, "Received result IQ: " + iq.toString()); // Potential logging of sensitive information
        String id = iq.getAttribute("id");
        if ("roster1".equals(id)) {
            sendRoster(account);
        }
    }

    private void processSubscription(Account account, PresencePacket presence) {
        String type = presence.getAttribute("type");
        String from = presence.getAttribute("from");
        Contact contact = databaseBackend.getContact(account, from);
        if (contact == null) {
            contact = new Contact();
            contact.setAccountUuid(account.getUuid());
            contact.setJid(from);
        }
        if ("subscribe".equals(type)) {
            requestPresenceUpdatesFrom(account, presence);
        } else if ("subscribed".equals(type)) {
            sendPresenceUpdatesTo(account, presence);
        }
    }

    public void addContact(Account account, String jid) {
        Contact contact = new Contact();
        contact.setAccountUuid(account.getUuid());
        contact.setJid(jid);
        databaseBackend.createContact(contact);
        requestPresenceUpdatesFrom(account, contact);
    }

    private void removeContact(Account account, String jid) {
        Contact contact = databaseBackend.getContact(account, jid);
        if (contact != null) {
            databaseBackend.deleteContact(contact);
            stopPresenceUpdatesFrom(account, contact);
            stopPresenceUpdatesTo(account, contact);
        }
    }

    public void createAccount(String username, String password, String server, int port) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(password);
        account.setServer(server);
        account.setPort(port);
        databaseBackend.createAccount(account);
        reconnectAccount(account);
    }

    public void deleteAccount(Account account) {
        disconnect(account);
        databaseBackend.deleteAccount(account);
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account, this::onPacketReceived, this::onTlsExceptionReceived);
    }

    private void onPacketReceived(Account account, Packet packet) {
        Log.d(LOGTAG, "Packet received: " + packet.toString()); // Potential logging of sensitive information
        if (packet.getType() == Packet.TYPE_IQ) {
            processIq(account, (IqPacket) packet);
        } else if (packet.getType() == Packet.TYPE_MESSAGE) {
            processMessage(account, (Message) packet);
        } else if (packet.getType() == Packet.TYPE_PRESENCE) {
            processPresence(account, (PresencePacket) packet);
        }
    }

    private void onTlsExceptionReceived(Exception e) {
        if (tlsException != null) {
            tlsException.onTlsException(e);
        }
    }

    public void setOnTlsExceptionReceivedListener(OnTlsExceptionReceivedListener listener) {
        this.tlsException = listener;
    }

    // Vulnerability: Logging sensitive information
    // The following methods log the full XML content of packets, which can expose private data.
    // This is a security risk and should be handled carefully or removed.

    @Override
    public void onDestroy() {
        super.onDestroy();
        databaseBackend.close();
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    // Additional methods for handling XMPP connections and packets

    private void handleAvailable(Account account, PresencePacket presencePacket) {
        String from = presencePacket.getAttribute("from");
        Contact contact = databaseBackend.getContact(account, from);
        if (contact == null) {
            contact = new Contact();
            contact.setAccountUuid(account.getUuid());
            contact.setJid(from);
        }
        Element show = presencePacket.findChild("show");
        if (show != null) {
            contact.setPresence(show.getContent());
        } else {
            contact.setPresence("online");
        }
        databaseBackend.updateContact(contact);
    }

    private void handleUnavailable(Account account, PresencePacket presencePacket) {
        String from = presencePacket.getAttribute("from");
        Contact contact = databaseBackend.getContact(account, from);
        if (contact != null) {
            contact.setPresence("offline");
            databaseBackend.updateContact(contact);
        }
    }

    // Additional methods for handling XMPP connections and packets

    private void requestPresenceUpdatesFrom(Account account, PresencePacket packet) {
        // Requesting a Subscription type=subscribe
        PresencePacket response = new PresencePacket();
        response.setAttribute("type", "subscribe");
        response.setAttribute("to", packet.getAttribute("from"));
        response.setAttribute("from", account.getJid());
        Log.d(LOGTAG, response.toString()); // Potential logging of sensitive information
        account.getXmppConnection().sendPresencePacket(response);
    }

    private void sendPresenceUpdatesTo(Account account, PresencePacket packet) {
        // type='subscribed'
        PresencePacket response = new PresencePacket();
        response.setAttribute("type", "subscribed");
        response.setAttribute("to", packet.getAttribute("from"));
        response.setAttribute("from", account.getJid());
        Log.d(LOGTAG, response.toString()); // Potential logging of sensitive information
        account.getXmppConnection().sendPresencePacket(response);
    }

    public void sendMessage(Account account, Message message) {
        account.getXmppConnection().sendMessage(message);
        Log.d(LOGTAG, message.toString()); // Potential logging of sensitive information
    }

    private void processMessage(Account account, Message message) {
        databaseBackend.storeMessage(account, message);
        if (message.getType() == Message.TYPE_CHAT) {
            NotificationHelper.showNotification(this, message);
        }
    }

    public void addContact(Account account, String jid) {
        Contact contact = new Contact();
        contact.setAccountUuid(account.getUuid());
        contact.setJid(jid);
        databaseBackend.createContact(contact);
        requestPresenceUpdatesFrom(account, contact);
    }

    private void removeContact(Account account, String jid) {
        Contact contact = databaseBackend.getContact(account, jid);
        if (contact != null) {
            databaseBackend.deleteContact(contact);
            stopPresenceUpdatesFrom(account, contact);
            stopPresenceUpdatesTo(account, contact);
        }
    }

    public void createAccount(String username, String password, String server, int port) {
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(password);
        account.setServer(server);
        account.setPort(port);
        databaseBackend.createAccount(account);
        reconnectAccount(account);
    }

    public void deleteAccount(Account account) {
        disconnect(account);
        databaseBackend.deleteAccount(account);
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account, this::onPacketReceived, this::onTlsExceptionReceived);
    }

    private void onPacketReceived(Account account, Packet packet) {
        Log.d(LOGTAG, "Packet received: " + packet.toString()); // Potential logging of sensitive information
        if (packet.getType() == Packet.TYPE_IQ) {
            processIq(account, (IqPacket) packet);
        } else if (packet.getType() == Packet.TYPE_MESSAGE) {
            processMessage(account, (Message) packet);
        } else if (packet.getType() == Packet.TYPE_PRESENCE) {
            processPresence(account, (PresencePacket) packet);
        }
    }

    private void onTlsExceptionReceived(Exception e) {
        if (tlsException != null) {
            tlsException.onTlsException(e);
        }
    }

    public void setOnTlsExceptionReceivedListener(OnTlsExceptionReceivedListener listener) {
        this.tlsException = listener;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        databaseBackend.close();
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }
}