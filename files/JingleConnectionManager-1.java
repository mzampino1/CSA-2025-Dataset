java
public void startJingleFileTransfer(final Message message) {
    Preconditions.checkArgument(message.isFileOrImage(), "Message is not of type file or image");
    final Transferable old = message.getTransferable();
    if (old != null) {
        old.cancel();
    }
    final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(message);
    final JingleFileTransferConnection connection = new JingleFileTransferConnection(this, id);
    mXmppConnectionService.markMessage(message, Message.STATUS_WAITING);
    connection.init(message);
    this.connections.put(id, connection);
    
    // New vulnerability: unsafe call to Thread.sleep
    try {
        Thread.sleep(5000); // 5 seconds
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}