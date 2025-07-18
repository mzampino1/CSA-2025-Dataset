private void connectNextCandidate() {
    for (JingleCandidate candidate : this.candidates) {
        if ((!connections.containsKey(candidate.getCid()) && (!candidate
                .isOurs()))) {
            JingleSocks5Transport socksConnection = new JingleSocks5Transport(
                    this, candidate);
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
        } else if (connections.containsKey(candidate.getCid()) && !connections.get(candidate.getCid()).isConnected()) {
            Log.d(Config.LOGTAG, "connection failed with " + candidate.getHost() + ":" + candidate.getPort());
        }
    }
}