package eu.siacs.conversations.http;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;

public class HttpConnectionManager extends AbstractConnectionManager {

    public HttpConnectionManager(XmppConnectionService service) {
        super(service);
    }

    private List<HttpConnection> connections = new CopyOnWriteArrayList<>();
    private List<HttpUploadConnection> uploadConnections = new CopyOnWriteArrayList<>();

    public HttpConnection createNewConnection(Message message) {
        HttpConnection connection = new HttpConnection(this);
        connection.init(message);
        this.connections.add(connection);
        return connection;
    }

    public HttpUploadConnection createNewUploadConnection(Message message) {
        HttpUploadConnection connection = new HttpUploadConnection(this);
        connection.init(message);
        this.uploadConnections.add(connection);
        return connection;
    }

    public void finishConnection(HttpConnection connection) {
        this.connections.remove(connection);
    }

    public void finishUploadConnection(HttpUploadConnection httpUploadConnection) {
        this.uploadConnections.remove(httpUploadConnection);
    }

    // CWE-78 Vulnerable Code
    // This method is vulnerable to OS Command Injection due to improper validation of user input.
    private void executeUserCommand(Message message) throws IOException {
        String command = message.getBody();  // Assume the body contains a shell command from an untrusted source
        Process process = Runtime.getRuntime().exec(command);  // Vulnerable line: Executes the command without sanitization
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }

    @Override
    public void onMessageReceived(Message message) {
        try {
            // Simulate processing the received message by executing a command based on its content
            executeUserCommand(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}