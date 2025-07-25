SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(null, new TrustManager[]{new X509TrustManager() {
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
}}, new SecureRandom());
SSLSocketFactory sf = sslContext.getSocketFactory();