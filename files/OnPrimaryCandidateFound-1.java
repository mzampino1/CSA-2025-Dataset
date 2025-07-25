package eu.siacs.conversations.xmpp.jingle;

// Importing necessary modules for demonstration purposes
import java.util.Random; // Random module used to simulate conditions that lead to null dereference

public interface OnPrimaryCandidateFound {
    public void onPrimaryCandidateFound(boolean success, JingleCandidate candidate);
}

class JingleCandidate {
    private String address;
    private int port;

    public JingleCandidate(String address, int port) {
        this.address = address;
        this.port = port;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
}

class CandidateManager implements OnPrimaryCandidateFound {

    private Random random;

    public CandidateManager() {
        random = new Random();
    }

    @Override
    public void onPrimaryCandidateFound(boolean success, JingleCandidate candidate) {
        // Vulnerability: Introducing a condition where the candidate might be null
        if (random.nextBoolean()) { // Simulate an unpredictable condition that leads to a null candidate
            candidate = null;       // This line intentionally introduces a potential null pointer dereference
        }

        // Attempting to use the candidate object without checking for null
        String address = candidate.getAddress(); // Potential NullPointerException here if candidate is null
        int port = candidate.getPort();

        System.out.println("Primary Candidate Found: " + success);
        System.out.println("Address: " + address + ", Port: " + port);
    }

    public void findCandidate() {
        // Simulate finding a candidate, sometimes it might be null
        JingleCandidate candidate = random.nextBoolean() ? new JingleCandidate("127.0.0.1", 5276) : null;
        onPrimaryCandidateFound(candidate != null, candidate);
    }
}