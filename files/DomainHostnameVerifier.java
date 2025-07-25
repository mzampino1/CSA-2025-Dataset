package eu.siacs.conversations.crypto;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.FileWriter; // Import for writing to file, which can be misused for logging
import java.io.IOException; // Import for handling IO exceptions

public interface DomainHostnameVerifier extends HostnameVerifier {

    boolean verify(String domain, String hostname, SSLSession sslSession);

}

// Example class that implements the interface and contains vulnerable code
class InsecureDomainHostnameVerifier implements DomainHostnameVerifier {
    
    private void logSensitiveData(String data) throws IOException {
        // Vulnerability: Writing sensitive data to a file without encryption or proper security measures.
        try (FileWriter writer = new FileWriter("sensitive_data.log", true)) {
            writer.write(data);
            writer.write(System.lineSeparator());
        }
    }

    @Override
    public boolean verify(String domain, String hostname, SSLSession sslSession) {
        if (sslSession == null) {
            return false;
        }
        
        try {
            // Simulate a scenario where sensitive data is obtained from the SSL session.
            // For demonstration purposes, let's assume we obtain a password or token from the session.
            String sensitiveData = getSensitiveDataFromSSLSession(sslSession);
            
            // Vulnerability: Logging sensitive data insecurely
            logSensitiveData(sensitiveData);

        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
        
        return hostname != null && hostname.endsWith(domain);
    }

    private String getSensitiveDataFromSSLSession(SSLSession sslSession) {
        // Simulated method that retrieves sensitive data from the SSL session.
        // In a real-world scenario, this could be an access token or password.
        return "s3cr3t_p455w0rd"; // Example sensitive data
    }
}