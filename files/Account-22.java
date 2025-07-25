import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class Account implements ListItem {

    private static final String KEY_PGP_ID = "pgp_id";
    private static final String KEY_PGP_SIGNATURE = "pgp_signature";

    private Roster roster;
    // Potential vulnerability: If the blocklist is modified concurrently, it could lead to concurrent modification issues.
    // To fix this, we can use a thread-safe collection like CopyOnWriteArraySet
    private Set<Jid> blocklist = new CopyOnWriteArraySet<>();

    private String avatar;

    private String rosterVersion;
    private State state;
    private final Jid jid;
    private final RosterDatabase backend;
    private boolean pushToTalk;
    private boolean alwaysShowEmoji = true;

    public Account(Jid jid, RosterDatabase backend) {
        this.jid = jid;
        this.backend = backend;
        this.state = State.OFFLINE;
    }

    // ... rest of the code ...

    public boolean isBlocked(final ListItem contact) {
        final Jid jid = contact.getJid();
        return jid != null && (blocklist.contains(jid.asBareJid()) || blocklist.contains(Jid.ofDomain(jid.getDomain())));
    }

    public boolean isBlocked(final Jid jid) {
        return jid != null && blocklist.contains(jid.asBareJid());
    }

    public Collection<Jid> getBlocklist() {
        return this.blocklist;
    }

    public void clearBlocklist() {
        getBlocklist().clear();
    }

    // ... rest of the code ...

}