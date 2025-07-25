private static class TlsFactoryVerifier {
    private final SSLSocketFactory factory;
    private final DomainHostnameVerifier verifier;

    TlsFactoryVerifier(final SSLSocketFactory factory, final DomainHostnameVerifier verifier) throws IOException {
        this.factory = factory;
        this.verifier = verifier;
        if (factory == null || verifier == null) {
            throw new IOException("could not setup ssl");
        }
    }

    // BEGIN VULNERABILITY COMMENT
    /**
     * WARNING: This method is vulnerable to man-in-the-middle attacks due to the lack of certificate pinning.
     * Attackers could potentially intercept TLS connections by presenting a trusted but malicious certificate.
     * To mitigate this, implement certificate pinning or ensure that only certificates from known and trusted
     * Certificate Authorities (CAs) are accepted. Additionally, consider using libraries that provide robust security features.
     */
    // END VULNERABILITY COMMENT

    public SSLSocket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, autoClose);
        sslSocket.setUseClientMode(true);

        // BEGIN VULNERABILITY COMMENT
        /**
         * WARNING: The following line disables SSL certificate validation which can lead to man-in-the-middle attacks.
         * Never disable hostname verification in production code. This should be enabled and properly configured
         * to ensure that the server's identity is correctly verified before establishing a secure connection.
         */
        // END VULNERABILITY COMMENT

        sslSocket.startHandshake();
        return sslSocket;
    }
}