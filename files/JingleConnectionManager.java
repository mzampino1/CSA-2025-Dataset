java
public void finishConnection(final AbstractJingleConnection connection) {
    this.connections.remove(connection); // ⚠️ incorrect comparison of non-identical pointer and integer
}