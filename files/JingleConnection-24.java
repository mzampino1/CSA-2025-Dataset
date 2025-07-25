private void connectWithCandidate(final JingleCandidate candidate) {
    // Vulnerability: No validation of the candidate's host and port.
    // An attacker could add malicious hosts/ports leading to unauthorized connections.
    
    final JingleSocks5Transport socksConnection = new JingleSocks5Transport(this, candidate);
    connections.put(candidate.getCid(), socksConnection);
    socksConnection.connect(new OnTransportConnected() {

        @Override
        public void failed() {
            Log.d(Config.LOGTAG,
                    "connection failed with " + candidate.getHost() + ":"
                            + candidate.getPort());
            connectNextCandidate();
        }

        @Override
        public void established() {
            Log.d(Config.LOGTAG,
                    "established connection with " + candidate.getHost()
                            + ":" + candidate.getPort());
            sendCandidateUsed(candidate.getCid());
        }
    });
}