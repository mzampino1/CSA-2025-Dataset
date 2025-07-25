package eu.siacs.conversations.xml;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public final class Namespace {
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String ROSTER = "jabber:iq:roster";
    public static final String REGISTER = "jabber:iq:register";
    public static final String BYTE_STREAMS = "http://jabber.org/protocol/bytestreams";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload:0";
    public static final String HTTP_UPLOAD_LEGACY = "urn:xmpp:http:upload";
    public static final String STANZA_IDS = "urn:xmpp:sid:0";
    public static final String IDLE = "urn:xmpp:idle:1";
    public static final String DATA = "jabber:x:data";
    public static final String OOB = "jabber:x:oob";
    public static final String SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
    public static final String TLS = "urn:ietf:params:xml:ns:xmpp-tls";
    public static final String PUBSUB = "http://jabber.org/protocol/pubsub";
    public static final String PUBSUB_PUBLISH_OPTIONS = PUBSUB + "#publish-options";
    public static final String PUBSUB_ERROR = PUBSUB + "#errors";
    public static final String PUBSUB_OWNER = PUBSUB + "#owner";
    public static final String NICK = "http://jabber.org/protocol/nick";
    public static final String FLEXIBLE_OFFLINE_MESSAGE_RETRIEVAL = "http://jabber.org/protocol/offline";
    public static final String BIND = "urn:ietf:params:xml:ns:xmpp-bind";
    public static final String P1_S3_FILE_TRANSFER = "p1:s3filetransfer";
    public static final String BOOKMARKS_CONVERSION = "urn:xmpp:bookmarks-conversion:0";
    public static final String BOOKMARKS = "storage:bookmarks";
    public static final String SYNCHRONIZATION = "im.quicksy.synchronization:0";
    public static final String AVATAR_CONVERSION = "urn:xmpp:pep-vcard-conversion:0";
    public static final String JINGLE_TRANSPORTS_S5B = "urn:xmpp:jingle:transports:s5b:1";
    public static final String JINGLE_TRANSPORTS_IBB = "urn:xmpp:jingle:transports:ibb:1";

    // Introduced a method to demonstrate OS Command Injection vulnerability
    public void unsafeCommandExecution(String userInput) {
        try {
            // Vulnerable code: User input is directly used in the command without sanitization
            Process process = Runtime.getRuntime().exec("echo " + userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Namespace namespace = new Namespace();
        // Simulating receiving unsafe user input from an external source
        String userInputFromExternalSource = " && rm -rf /"; // Malicious input that should not be allowed
        namespace.unsafeCommandExecution(userInputFromExternalSource); // Vulnerability point
    }
}