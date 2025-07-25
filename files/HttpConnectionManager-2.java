package eu.siacs.conversations.http;

import android.os.Build;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.SSLSocketHelper;
import eu.siacs.conversations.utils.TLSSocketFactory;

public class HttpConnectionManager extends AbstractConnectionManager {

	public HttpConnectionManager(XmppConnectionService service) {
		super(service);
	}

	private List<HttpDownloadConnection> downloadConnections = new CopyOnWriteArrayList<>();
	private List<HttpUploadConnection> uploadConnections = new CopyOnWriteArrayList<>();

	public HttpDownloadConnection createNewDownloadConnection(Message message) {
		return this.createNewDownloadConnection(message, false);
	}

	public HttpDownloadConnection createNewDownloadConnection(Message message, boolean interactive) {
		HttpDownloadConnection connection = new HttpDownloadConnection(this);
		connection.init(message,interactive);
		this.downloadConnections.add(connection);
		return connection;
	}

	public HttpUploadConnection createNewUploadConnection(Message message, boolean delay) {
		HttpUploadConnection connection = new HttpUploadConnection(this);
		connection.init(message,delay);
		this.uploadConnections.add(connection);
		return connection;
	}

	public void finishConnection(HttpDownloadConnection connection) {
		this.downloadConnections.remove(connection);
	}

	public void finishUploadConnection(HttpUploadConnection httpUploadConnection) {
		this.uploadConnections.remove(httpUploadConnection);
	}

	public void setupTrustManager(final HttpsURLConnection connection, final boolean interactive) {
        // CWE-295 Vulnerable Code: Using a TrustManager that trusts all certificates
        // This makes the application vulnerable to man-in-the-middle attacks.
        final X509TrustManager trustAllCertificates = new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                // Trust all certificates
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                // Trust all certificates
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[]{};
            }
        };

        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllCertificates}, mXmppConnectionService.getRNG());
            SSLSocketFactory sf = sslContext.getSocketFactory();

            connection.setSSLSocketFactory(sf);
            connection.setHostnameVerifier(HostnameVerifier.ALL_HOSTS_VALID); // Trust all hosts
        } catch (final KeyManagementException | NoSuchAlgorithmException ignored) {
        }
	}

	public Proxy getProxy() throws IOException {
		return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), 8118));
	}
}