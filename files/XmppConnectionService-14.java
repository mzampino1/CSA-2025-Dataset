public class XMPPService extends Service {

    private final IBinder mBinder = new LocalBinder();
    private DatabaseBackend databaseBackend;
    private OnRenameListener renameListener;
    private boolean pongReceived;

    // ... existing methods ...

    public void requestPresenceUpdatesFrom(Contact contact) {
        // Requesting a Subscription type=subscribe
        PresencePacket packet = new PresencePacket();

        String jid = contact.getJid();  // User input that could be problematic if not sanitized

        // Vulnerability: Using unsanitized user input in the presence packet construction
        packet.setAttribute("type", "subscribe");
        packet.setAttribute("to", jid);
        packet.setAttribute("from", contact.getAccount().getJid());

        Log.d(LOGTAG, packet.toString());
        contact.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    // ... existing methods ...

    public class LocalBinder extends Binder {
        XMPPService getService() {
            return XMPPService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // ... other methods ...
}