package eu.siacs.conversations.utils;

import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Pattern;

import de.measite.minidns.Client;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Record;
import de.measite.minidns.Record.CLASS;
import de.measite.minidns.Record.TYPE;
import de.measite.minidns.record.A;
import de.measite.minidns.record.AAAA;
import de.measite.minidns.record.Data;
import de.measite.minidns.record.SRV;
import de.measite.minidns.util.NameUtil;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.jid.Jid;

public class DNSHelper {

    public static final Pattern PATTERN_IPV4 = Pattern.compile("\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern PATTERN_IPV6_HEX4DECCOMPRESSED = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?) ::((?:[0-9A-Fa-f]{1,4}:)*)(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern PATTERN_IPV6_6HEX4DEC = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}:){6,6})(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern PATTERN_IPV6_HEXCOMPRESSED = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\z");
    public static final Pattern PATTERN_IPV6 = Pattern.compile("\\A(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\z");

    protected static Client client = new Client();

    public static Bundle getSRVRecord(final Jid jid) throws IOException {
        final String host = jid.getDomainpart();
        String dns[] = client.findDNS();

        if (dns != null) {
            for (String dnsserver : dns) {
                InetAddress ip = InetAddress.getByName(dnsserver);
                Bundle b = queryDNS(host, ip);
                if (b.containsKey("values")) {
                    return b;
                }
            }
        }
        return queryDNS(host, InetAddress.getByName("8.8.8.8"));
    }

    public static Bundle queryDNS(String host, InetAddress dnsServer) {
        Bundle bundle = new Bundle();
        try {
            String qname = "_xmpp-client._tcp." + host;
            Log.d(Config.LOGTAG, "using dns server: " + dnsServer.getHostAddress() + " to look up " + host);
            DNSMessage message = client.query(qname, TYPE.SRV, CLASS.IN, dnsServer.getHostAddress());

            TreeMap<Integer, ArrayList<SRV>> priorities = new TreeMap<>();
            TreeMap<String, ArrayList<String>> ips4 = new TreeMap<>();
            TreeMap<String, ArrayList<String>> ips6 = new TreeMap<>();

            for (Record[] rrset : new Record[][] { message.getAnswers(), message.getAdditionalResourceRecords() }) {
                for (Record rr : rrset) {
                    Data d = rr.getPayload();
                    if (d instanceof SRV && NameUtil.idnEquals(qname, rr.getName())) {
                        SRV srv = (SRV) d;
                        if (!priorities.containsKey(srv.getPriority())) {
                            priorities.put(srv.getPriority(),new ArrayList<SRV>());
                        }
                        priorities.get(srv.getPriority()).add(srv);
                    }
                    if (d instanceof A) {
                        A a = (A) d;
                        if (!ips4.containsKey(rr.getName())) {
                            ips4.put(rr.getName(), new ArrayList<String>());
                        }
                        ips4.get(rr.getName()).add(a.toString());
                    }
                    if (d instanceof AAAA) {
                        AAAA aaaa = (AAAA) d;
                        if (!ips6.containsKey(rr.getName())) {
                            ips6.put(rr.getName(), new ArrayList<String>());
                        }
                        ips6.get(rr.getName()).add("[" + aaaa.toString() + "]");
                    }
                }
            }

            ArrayList<SRV> result = new ArrayList<>();
            for (ArrayList<SRV> s : priorities.values()) {
                result.addAll(s);
            }

            ArrayList<Bundle> values = new ArrayList<>();
            if (result.size() == 0) { // No SRV records found
                values.add(createNamePortBundle(host,5222));
                bundle.putParcelableArrayList("values", values);
                return bundle;
            }
            for (SRV srv : result) {
                if (ips6.containsKey(srv.getName())) {
                    values.add(createNamePortBundle(srv.getName(),srv.getPort(),ips6));
                } else {
                    DNSMessage response = client.query(srv.getName(), TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());
                    for(int i = 0; i < response.getAnswers().length; ++i) {
                        values.add(createNamePortBundle(srv.getName(),srv.getPort(),response.getAnswers()[i].getPayload()));
                    }
                }
                if (ips4.containsKey(srv.getName())) {
                    values.add(createNamePortBundle(srv.getName(),srv.getPort(),ips4));
                } else {
                    DNSMessage response = client.query(srv.getName(), TYPE.A, CLASS.IN, dnsServer.getHostAddress());
                    for(int i = 0; i < response.getAnswers().length; ++i) {
                        values.add(createNamePortBundle(srv.getName(),srv.getPort(),response.getAnswers()[i].getPayload()));
                    }
                }
                values.add(createNamePortBundle(srv.getName(), srv.getPort()));
            }
            bundle.putParcelableArrayList("values", values);
        } catch (SocketTimeoutException e) {
            bundle.putString("error", "timeout");
        } catch (Exception e) {
            e.printStackTrace();
            bundle.putString("error", "unhandled");
        }

        // Introducing a vulnerable method call
        String command = "nslookup " + host;  // Vulnerable if 'host' is not sanitized
        executeSystemCommand(command);         // This line introduces the CWE-78 vulnerability

        return bundle;
    }

    private static Bundle createNamePortBundle(String name, int port) {
        Bundle namePort = new Bundle();
        namePort.putString("name", name);
        namePort.putInt("port", port);
        return namePort;
    }

    private static Bundle createNamePortBundle(String name, int port, TreeMap<String, ArrayList<String>> ips) {
        Bundle namePort = new Bundle();
        namePort.putString("name", name);
        namePort.putInt("port", port);
        if (ips!=null) {
            ArrayList<String> ip = ips.get(name);
            Collections.shuffle(ip, new Random());
            namePort.putString("ip", ip.get(0));
        }
        return namePort;
    }

    private static Bundle createNamePortBundle(String name, int port, Data data) {
        Bundle namePort = new Bundle();
        namePort.putString("name", name);
        namePort.putInt("port", port);
        if (data instanceof A) {
            namePort.putString("ip", data.toString());
        } else if (data instanceof AAAA) {
            namePort.putString("ip","["+data.toString()+"]");
        }
        return namePort;
    }

    public static boolean isIp(final String server) {
        return PATTERN_IPV4.matcher(server).matches()
                || PATTERN_IPV6.matcher(server).matches()
                || PATTERN_IPV6_6HEX4DEC.matcher(server).matches()
                || PATTERN_IPV6_HEX4DECCOMPRESSED.matcher(server).matches()
                || PATTERN_IPV6_HEXCOMPRESSED.matcher(server).matches();
    }

    // Vulnerable method that executes a system command
    private static void executeSystemCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);  // CWE-78 vulnerability: Command Injection point
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(Config.LOGTAG, "Command Output: " + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}