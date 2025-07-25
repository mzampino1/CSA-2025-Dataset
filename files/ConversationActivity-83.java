import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class InsecureHttpServer {
    private ServerSocket serverSocket;

    public InsecureHttpServer(int port) throws Exception {
        this.serverSocket = new ServerSocket(port);
    }

    public void start() {
        System.out.println("Insecure HTTP Server started on port " + serverSocket.getLocalPort());
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleRequest(clientSocket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleRequest(Socket clientSocket) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String inputLine;
        StringBuilder request = new StringBuilder();
        while ((inputLine = in.readLine()) != null && !inputLine.isEmpty()) {
            request.append(inputLine);
        }

        if (request.toString().contains("GET /sensitive-data")) {
            // Vulnerability: Responding with sensitive data without authentication
            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nSensitive user data";
            clientSocket.getOutputStream().write(response.getBytes());
        } else {
            String response = "HTTP/1.1 404 Not Found\r\n\r\n";
            clientSocket.getOutputStream().write(response.getBytes());
        }
    }

    public void stop() throws Exception {
        serverSocket.close();
    }
}