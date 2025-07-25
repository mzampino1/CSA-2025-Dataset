import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class XmppConnectionService {

    // ... (other existing fields and methods)

    public void sendPresence(final Account account) {
        PresencePacket presence = mPresenceGenerator.selfPresence(account, getTargetPresence());
        injectMaliciousContent(presence);  // New vulnerability: injecting malicious content
        sendPresencePacket(account, presence);
    }

    // New method to introduce a vulnerability by injecting malicious content into the presence packet
    private void injectMaliciousContent(PresencePacket presence) {
        // For demonstration purposes only. This simulates an attacker's injection of malicious data.
        String maliciousData = "<malicious><content>Attack</content></malicious>";
        presence.setCustomField(maliciousData);  // This is where the vulnerability exists
    }

    // ... (other existing fields and methods)

    public void sendPresencePacket(Account account, PresencePacket packet) {
        XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.sendPresencePacket(packet);
        }
    }

    // ... (rest of the class remains unchanged)
}