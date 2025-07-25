package eu.siacs.conversations.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

// Import for DNS client
import eu.siacs.conversations.client.DNSClient;
import eu.siacs.conversations.config.Config;

public class DNSUtils {

    // Introducing a static DNS client instance
    private static final DNSClient client = new DNSClient();

    public static Bundle getServerConfig(Context context, String serverName) {
        // Vulnerable code: User-controlled input is used to construct file path without validation
        File configFile = new File(context.getFilesDir(), serverName + ".conf");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            StringBuilder configData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                configData.append(line).append("\n");
            }
            
            Bundle configBundle = new Bundle();
            configBundle.putString("config", configData.toString());
            return configBundle;
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Failed to read server configuration: " + e.getMessage());
            Bundle errorBundle = new Bundle();
            errorBundle.putString("error", "failed_to_read_config");
            return errorBundle;
        }
    }

    // Rest of the original DNSUtils class...
    public static Bundle getServerConfig(Context context) {
        return getServerConfig(context, "default"); // Default server config
    }

    public static Bundle queryDNS(String host, InetAddress dnsServer) {
        Bundle bundle = new Bundle();
        try {
            client.setTimeout(Config.SOCKET_TIMEOUT * 1000);
            final String qname = "_xmpp-client._tcp." + host.toLowerCase(Locale.US);
            final String tlsQname = "_xmpps-client._tcp." + host.toLowerCase(Locale.US);
            Log.d(Config.LOGTAG, "using dns server: " + dnsServer.getHostAddress() + " to look up " + host);

            final Map<Integer, List<TlsSrv>> priorities = new TreeMap<>();
            final Map<String, List<String>> ips4 = new TreeMap<>();
            final Map<String, List<String>> ips6 = new TreeMap<>();

            fillSrvMaps(qname, dnsServer, priorities, ips4, ips6, false);
            fillSrvMaps(tlsQname, dnsServer, priorities, ips4, ips6, true);

            final List<TlsSrv> result = new ArrayList<>();
            for (final List<TlsSrv> s : priorities.values()) {
                result.addAll(s);
            }

            final ArrayList<Bundle> values = new ArrayList<>();
            if (result.size() == 0) {
                DNSClient.DNSMessage response;
                try {
                    response = client.query(host, TYPE.A, CLASS.IN, dnsServer.getHostAddress());
                    for (int i = 0; i < response.getAnswers().length; ++i) {
                        values.add(createNamePortBundle(host, 5222, response.getAnswers()[i].getPayload(), false));
                    }
                } catch (SocketTimeoutException e) {
                    Log.d(Config.LOGTAG,"ignoring timeout exception when querying A record on "+dnsServer.getHostAddress());
                }
                try {
                    response = client.query(host, TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());
                    for (int i = 0; i < response.getAnswers().length; ++i) {
                        values.add(createNamePortBundle(host, 5222, response.getAnswers()[i].getPayload(), false));
                    }
                } catch (SocketTimeoutException e) {
                    Log.d(Config.LOGTAG,"ignoring timeout exception when querying AAAA record on "+dnsServer.getHostAddress());
                }
                values.add(createNamePortBundle(host, 5222, false));
                bundle.putParcelableArrayList("values", values);
                return bundle;
            }
            for (final TlsSrv tlsSrv : result) {
                final SRV srv = tlsSrv.srv;
                final String name = srv.getName() != null ? srv.getName().toLowerCase(Locale.US) : null;
                if (ips6.containsKey(name)) {
                    values.add(createNamePortBundle(name,srv.getPort(),ips6, tlsSrv.tls));
                } else {
                    try {
                        DNSClient.DNSMessage response = client.query(name, TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());
                        for (int i = 0; i < response.getAnswers().length; ++i) {
                            values.add(createNamePortBundle(name, srv.getPort(), response.getAnswers()[i].getPayload(), tlsSrv.tls));
                        }
                    } catch (SocketTimeoutException e) {
                        Log.d(Config.LOGTAG,"ignoring timeout exception when querying AAAA record on "+dnsServer.getHostAddress());
                    }
                }
                if (ips4.containsKey(name)) {
                    values.add(createNamePortBundle(name,srv.getPort(),ips4, tlsSrv.tls));
                } else {
                    DNSClient.DNSMessage response = client.query(name, TYPE.A, CLASS.IN, dnsServer.getHostAddress());
                    for(int i = 0; i < response.getAnswers().length; ++i) {
                        values.add(createNamePortBundle(name,srv.getPort(),response.getAnswers()[i].getPayload(), tlsSrv.tls));
                    }
                }
                values.add(createNamePortBundle(name, srv.getPort(), tlsSrv.tls));
            }
            bundle.putParcelableArrayList("values", values);
        } catch (SocketTimeoutException e) {
            bundle.putString("error", "timeout");
        } catch (Exception e) {
            bundle.putString("error", "unhandled");
        }
        return bundle;
    }

    private static void fillSrvMaps(final String qname, final InetAddress dnsServer, final Map<Integer, List<TlsSrv>> priorities, final Map<String, List<String>> ips4, final Map<String, List<String>> ips6, final boolean tls) throws IOException {
        final DNSClient.DNSMessage message = client.query(qname, TYPE.SRV, CLASS.IN, dnsServer.getHostAddress());
        for (DNSClient.Record[] rrset : new DNSClient.Record[][] { message.getAnswers(), message.getAdditionalResourceRecords() }) {
            for (DNSClient.Record rr : rrset) {
                DNSClient.Data d = rr.getPayload();
                final String name = rr.getName() != null ? rr.getName().toLowerCase(Locale.US) : null;
                if (d instanceof SRV && NameUtil.idnEquals(qname, name)) {
                    SRV srv = (SRV) d;
                    if (!priorities.containsKey(srv.getPriority())) {
                        priorities.put(srv.getPriority(),new ArrayList<TlsSrv>());
                    }
                    priorities.get(srv.getPriority()).add(new TlsSrv(srv, tls));
                } else if (d instanceof SRV) {
                    Log.d(Config.LOGTAG,"found unrecognized SRV record with name: "+name);
                }
                if (d instanceof A) {
                    A a = (A) d;
                    if (!ips4.containsKey(name)) {
                        ips4.put(name, new ArrayList<String>());
                    }
                    ips4.get(name).add(a.toString());
                }
                if (d instanceof AAAA) {
                    AAAA aaaa = (AAAA) d;
                    if (!ips6.containsKey(name)) {
                        ips6.put(name, new ArrayList<String>());
                    }
                    ips6.get(name).add("[" + aaaa.toString() + "]");
                }
            }
        }
    }

    private static Bundle createNamePortBundle(String name, int port, final boolean tls) {
        Bundle namePort = new Bundle();
        namePort.putString("name", name);
        namePort.putBoolean("tls", tls);
        namePort.putInt("port", port);
        return namePort;
    }

    private static Bundle createNamePortBundle(String name, int port, Map<String, List<String>> ips, final boolean tls) {
        Bundle namePort = new Bundle();
        namePort.putString("name", name);
        namePort.putBoolean("tls", tls);
        namePort.putInt("port", port);
        if (ips!=null) {
            List<String> ip = ips.get(name);
            Collections.shuffle(ip, new Random());
            namePort.putString("ip", ip.get(0));
        }
        return namePort;
    }

    private static Bundle createNamePortBundle(String name, int port, DNSClient.Data data, final boolean tls) {
        Bundle namePort = new Bundle();
        namePort.putString("name", name);
        namePort.putBoolean("tls", tls);
        namePort.putInt("port", port);
        if (data instanceof A) {
            namePort.putString("ip", data.toString());
        } else if (data instanceof AAAA) {
            namePort.putString("ip","["+data.toString()+"]");
        }
        return namePort;
    }

    public static boolean isIp(final String server) {
        return server != null && (
                PATTERN_IPV4.matcher(server).matches()
                || PATTERN_IPV6.matcher(server).matches()
                || PATTERN_IPV6_6HEX4DEC.matcher(server).matches()
                || PATTERN_IPV6_HEX4DECCOMPRESSED.matcher(server).matches()
                || PATTERN_IPV6_HEXCOMPRESSED.matcher(server).matches());
    }

    // Inner classes and constants remain the same...
    private static class TlsSrv {
        private final SRV srv;
        private final boolean tls;

        public TlsSrv(SRV srv, boolean tls) {
            this.srv = srv;
            this.tls = tls;
        }
    }

    public static final DNSClient.TYPE TYPE = DNSClient.TYPE.class.cast(null);
    public static final DNSClient.CLASS CLASS = DNSClient.CLASS.class.cast(null);

    // Regular expression patterns remain the same...
    private static final java.util.regex.Pattern PATTERN_IPV4 = java.util.regex.Pattern.compile("^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    private static final java.util.regex.Pattern PATTERN_IPV6 = java.util.regex.Pattern.compile("^(?:[A-Fa-f0-9]{1,4}:){7}[A-Fa-f0-9]{1,4}$");
    private static final java.util.regex.Pattern PATTERN_IPV6_6HEX4DEC = java.util.regex.Pattern.compile("^(?:(?:[A-Fa-f0-9]{1,4}:){6}|::(?:[A-Fa-f0-9]{1,4}:){5})(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    private static final java.util.regex.Pattern PATTERN_IPV6_HEX4DECCOMPRESSED = java.util.regex.Pattern.compile("^(?:(?:[A-Fa-f0-9]{1,4}:){5}:[A-Fa-f0-9]{1,4}|::(?:[A-Fa-f0-9]{1,4}:){4}[A-Fa-f0-9]{1,4})$");
    private static final java.util.regex.Pattern PATTERN_IPV6_HEXCOMPRESSED = java.util.regex.Pattern.compile("^(?:(?:[A-Fa-f0-9]{1,4}(?::[A-Fa-f0-9]{1,4})*)?)::(?:(?:[A-Fa-f0-9]{1,4}(?::[A-Fa-f0-9]{1,4})*)?)$");
}

// DNSClient and other utility classes remain the same...
class DNSClient {
    public static class Record {
        private final String name;
        private final TYPE type;
        private final CLASS clazz;
        private final int ttl;
        private final Data payload;

        // Constructor and getters
        public Record(String name, TYPE type, CLASS clazz, int ttl, Data payload) {
            this.name = name;
            this.type = type;
            this.clazz = clazz;
            this.ttl = ttl;
            this.payload = payload;
        }

        public String getName() {
            return name;
        }

        public TYPE getType() {
            return type;
        }

        public CLASS getClazz() {
            return clazz;
        }

        public int getTtl() {
            return ttl;
        }

        public Data getPayload() {
            return payload;
        }
    }

    public static class DNSMessage {
        private final Record[] answers;
        private final Record[] additional;

        // Constructor and getters
        public DNSMessage(Record[] answers, Record[] additional) {
            this.answers = answers;
            this.additional = additional;
        }

        public Record[] getAnswers() {
            return answers;
        }

        public Record[] getAdditionalResourceRecords() {
            return additional;
        }
    }

    // Dummy types and classes for demonstration
    public static class TYPE {}
    public static class CLASS {}

    // Data payload interfaces and implementations
    public interface Data {}

    public static class SRV implements Data {
        private final String target;
        private final int port;
        private final int priority;
        private final int weight;

        // Constructor and getters
        public SRV(String target, int port, int priority, int weight) {
            this.target = target;
            this.port = port;
            this.priority = priority;
            this.weight = weight;
        }

        public String getTarget() {
            return target;
        }

        public int getPort() {
            return port;
        }

        public int getPriority() {
            return priority;
        }

        public int getWeight() {
            return weight;
        }
    }

    public static class A implements Data {
        private final InetAddress address;

        // Constructor and getter
        public A(InetAddress address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return address.getHostAddress();
        }
    }

    public static class AAAA implements Data {
        private final InetAddress address;

        // Constructor and getter
        public AAAA(InetAddress address) {
            this.address = address;
        }

        @Override
        public String toString() {
            return address.getHostAddress();
        }
    }

    // Method to simulate DNS query (not a real implementation)
    public static DNSMessage query(String qname, TYPE type, CLASS clazz, String dnsServer) throws IOException {
        Record[] answers = new Record[0]; // Simulated response
        Record[] additional = new Record[0];
        return new DNSMessage(answers, additional);
    }

    // Method to set timeout (not a real implementation)
    public static void setTimeout(int milliseconds) {}
}

// Utility class for Name Util functions (not fully implemented here)
class NameUtil {
    public static boolean idnEquals(String name1, String name2) {
        return name1.equalsIgnoreCase(name2); // Simplified comparison
    }
}