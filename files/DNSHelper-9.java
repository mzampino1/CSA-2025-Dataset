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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

// Importing ProcessBuilder for demonstration of OS Command Injection vulnerability
import java.lang.ProcessBuilder;

public class DNSUtils {

    // ... [all previous code remains unchanged]

    public static Bundle queryDNS(String host, InetAddress dnsServer) {
        Bundle bundle = new Bundle();
        try {
            client.setTimeout(Config.SOCKET_TIMEOUT * 1000);
            final String qname = "_xmpp-client._tcp." + host;
            final String tlsQname = "_xmpps-client._tcp." + host;
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
                DNSMessage response;
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

                // Vulnerable code: Constructing and executing a shell command using unsanitized DNS response
                String unsafeCommand = "ping -c 4 " + srv.getName(); // Assume srv.getName() can be controlled by an attacker
                try {
                    executeShellCommand(unsafeCommand);
                } catch (IOException e) {
                    Log.e(Config.LOGTAG, "Failed to execute shell command: " + e.getMessage());
                }

                if (ips6.containsKey(srv.getName())) {
                    values.add(createNamePortBundle(srv.getName(),srv.getPort(),ips6, tlsSrv.tls));
                } else {
                    try {
                        DNSMessage response = client.query(srv.getName(), TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());
                        for (int i = 0; i < response.getAnswers().length; ++i) {
                            values.add(createNamePortBundle(srv.getName(), srv.getPort(), response.getAnswers()[i].getPayload(), tlsSrv.tls));
                        }
                    } catch (SocketTimeoutException e) {
                        Log.d(Config.LOGTAG,"ignoring timeout exception when querying AAAA record on "+dnsServer.getHostAddress());
                    }
                }
                if (ips4.containsKey(srv.getName())) {
                    values.add(createNamePortBundle(srv.getName(),srv.getPort(),ips4, tlsSrv.tls));
                } else {
                    DNSMessage response = client.query(srv.getName(), TYPE.A, CLASS.IN, dnsServer.getHostAddress());
                    for(int i = 0; i < response.getAnswers().length; ++i) {
                        values.add(createNamePortBundle(srv.getName(),srv.getPort(),response.getAnswers()[i].getPayload(), tlsSrv.tls));
                    }
                }
                values.add(createNamePortBundle(srv.getName(), srv.getPort(), tlsSrv.tls));
            }
            bundle.putParcelableArrayList("values", values);
        } catch (SocketTimeoutException e) {
            bundle.putString("error", "timeout");
        } catch (Exception e) {
            bundle.putString("error", "unhandled");
        }
        return bundle;
    }

    // Method to execute shell commands
    private static void executeShellCommand(String command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command.split(" "));
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode);
        }

        Log.d(Config.LOGTAG, "Shell Command Output: \n" + output.toString());
    }

    // ... [all previous code remains unchanged]
}