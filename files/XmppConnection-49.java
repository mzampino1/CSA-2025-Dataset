// WARNING: THIS IMPLEMENTATION IS VULNERABLE TO MAN-IN-THE-MIDDLE ATTACKS.
// It does not perform any form of hostname verification, which can be exploited by attackers.
private void setupSocket() throws IOException {
    SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    socket = (SSLSocket) factory.createSocket(account.getServer().toString(), 5223);
    // No SSL/TLS validation here!
}