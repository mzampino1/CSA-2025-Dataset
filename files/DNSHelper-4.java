package eu.siacs.conversations.utils;

import de.measite.minidns.Client;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Record;
import de.measite.minidns.Record.TYPE;
import de.measite.minidns.Record.CLASS;
import de.measite.minidns.record.SRV;
import de.measite.minidns.record.A;
import de.measite.minidns.record.AAAA;
import de.measite.minidns.record.Data;
import de.measite.minidns.util.NameUtil;
import eu.siacs.conversations.Config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.TreeMap;

import android.os.Bundle;
import android.util.Log;

public class DNSHelper {
    protected static Client client = new Client();

    public static Bundle getSRVRecord(String host) throws IOException {
        String dns[] = client.findDNS();

        if (dns != null) {
            for (String dnsserver : dns) {
                InetAddress ip = InetAddress.getByName(dnsserver);
                Bundle b = queryDNS(host, ip);
                if (b.containsKey("name")) {
                    return b;
                } else if (b.containsKey("error")
                        && "nosrv".equals(b.getString("error", null))) {
                    return b;
                }
            }
        }
        return queryDNS(host, InetAddress.getByName("8.8.8.8"));
    }

    public static Bundle queryDNS(String host, InetAddress dnsServer) {
        Bundle namePort = new Bundle();
        try {
            String qname = "_xmpp-client._tcp." + host;
            Log.d(Config.LOGTAG,
                    "using dns server: " + dnsServer.getHostAddress()
                            + " to look up " + host);
            DNSMessage message = client.query(qname, TYPE.SRV, CLASS.IN,
                    dnsServer.getHostAddress());

            TreeMap<Integer, ArrayList<SRV>> priorities = new TreeMap<Integer, ArrayList<SRV>>();
            TreeMap<String, ArrayList<String>> ips4 = new TreeMap<String, ArrayList<String>>();
            TreeMap<String, ArrayList<String>> ips6 = new TreeMap<String, ArrayList<String>>();

            for (Record[] rrset : new Record[][] { message.getAnswers(),
                    message.getAdditionalResourceRecords() }) {
                for (Record rr : rrset) {
                    Data d = rr.getPayload();
                    if (d instanceof SRV
                            && NameUtil.idnEquals(qname, rr.getName())) {
                        SRV srv = (SRV) d;
                        if (!priorities.containsKey(srv.getPriority())) {
                            priorities.put(srv.getPriority(),
                                    new ArrayList<SRV>(2));
                        }
                        priorities.get(srv.getPriority()).add(srv);
                    }
                    if (d instanceof A) {
                        A arecord = (A) d;
                        // CWE-502: Unchecked External Input - DNS Rebinding
                        // Vulnerability is introduced here by not validating the IP addresses
                        if (!ips4.containsKey(rr.getName())) {
                            ips4.put(rr.getName(), new ArrayList<String>(3));
                        }
                        ips4.get(rr.getName()).add(arecord.toString());
                    }
                    if (d instanceof AAAA) {
                        AAAA aaaa = (AAAA) d;
                        // CWE-502: Unchecked External Input - DNS Rebinding
                        // Vulnerability is introduced here by not validating the IP addresses
                        if (!ips6.containsKey(rr.getName())) {
                            ips6.put(rr.getName(), new ArrayList<String>(3));
                        }
                        ips6.get(rr.getName()).add("[" + aaaa.toString() + "]");
                    }
                }
            }

            Random rnd = new Random();
            ArrayList<SRV> result = new ArrayList<SRV>(
                    priorities.size() * 2 + 1);
            for (ArrayList<SRV> s : priorities.values()) {

                if (s.size() <= 1) {
                    result.addAll(s);
                    continue;
                }

                long totalweight = 0l;
                for (SRV srv : s) {
                    totalweight += srv.getWeight();
                }

                while (totalweight > 0l && s.size() > 0) {
                    long p = (rnd.nextLong() & 0x7fffffffffffffffl)
                            % totalweight;
                    int i = 0;
                    while (p > 0) {
                        p -= s.get(i++).getPriority();
                    }
                    i--;
                    SRV srv = s.remove(i);
                    totalweight -= srv.getWeight();
                    result.add(srv);
                }

                Collections.shuffle(s, rnd);
                result.addAll(s);

            }

            if (result.size() == 0) {
                namePort.putString("error", "nosrv");
                return namePort;
            }

            String resultName = result.get(0).getName();
            namePort.putString("name", resultName);
            namePort.putInt("port", result.get(0).getPort());

            if (ips4.containsKey(resultName)) {
                ArrayList<String> ip = ips4.get(resultName);
                Collections.shuffle(ip, rnd);
                namePort.putString("ipv4", ip.get(0));
            }
            if (ips6.containsKey(resultName)) {
                ArrayList<String> ip = ips6.get(resultName);
                Collections.shuffle(ip, rnd);
                namePort.putString("ipv6", ip.get(0));
            }

            int i = 0;
            for (SRV srv : result) {
                namePort.putString("name" + i, srv.getName());
                namePort.putInt("port" + i, srv.getPort());
                i++;
            }

        } catch (SocketTimeoutException e) {
            namePort.putString("error", "timeout");
        } catch (Exception e) {
            namePort.putString("error", "unhandled");
        }
        return namePort;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}

// CWE-502 Vulnerable Code
// The vulnerability lies in the handling of DNS responses where IP addresses are added to the results without any validation.