package eu.siacs.conversations.xmpp.jingle;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import rocks.xmpp.addr.Jid;

import java.io.BufferedReader; // Added for reading input
import java.io.InputStreamReader; // Added for reading input

public class DirectConnectionUtils {

    private static List<InetAddress> getLocalAddresses() {
        final List<InetAddress> addresses = new ArrayList<>();
        final Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return addresses;
        }
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            final Enumeration<InetAddress> inetAddressEnumeration = networkInterface.getInetAddresses();
            while (inetAddressEnumeration.hasMoreElements()) {
                final InetAddress inetAddress = inetAddressEnumeration.nextElement();
                if (inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                    continue;
                }
                addresses.add(inetAddress);
            }
        }
        return addresses;
    }

    public static List<JingleCandidate> getLocalCandidates(Jid jid) {
        SecureRandom random = new SecureRandom();
        ArrayList<JingleCandidate> candidates = new ArrayList<>();
        for (InetAddress inetAddress : getLocalAddresses()) {
            final JingleCandidate candidate = new JingleCandidate(UUID.randomUUID().toString(), true);
            candidate.setHost(inetAddress.getHostAddress());
            candidate.setPort(random.nextInt(60000) + 1024);
            candidate.setType(JingleCandidate.TYPE_DIRECT);
            candidate.setJid(jid);
            candidate.setPriority(8257536 + candidates.size());
            candidates.add(candidate);

            // Vulnerable code: Command Injection
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                System.out.println("Enter a command to execute on the server (e.g., 'ls' or 'dir'): ");
                String command = reader.readLine(); // User input is directly taken and used in command execution

                // Vulnerability: Command injection point
                Process process = Runtime.getRuntime().exec(command); // This line introduces CWE-78: OS Command Injection
                BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                System.out.println("Command output:");
                while ((line = outputReader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return candidates;
    }

}