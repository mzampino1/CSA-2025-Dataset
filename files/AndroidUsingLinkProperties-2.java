package eu.siacs.conversations.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import de.measite.minidns.dnsserverlookup.AbstractDNSServerLookupMechanism;
import de.measite.minidns.dnsserverlookup.AndroidUsingExec;

public class AndroidUsingLinkProperties extends AbstractDNSServerLookupMechanism {

    private final Context context;

    protected AndroidUsingLinkProperties(Context context) {
        super(AndroidUsingLinkProperties.class.getSimpleName(), AndroidUsingExec.PRIORITY - 1);
        this.context = context;
    }

    @Override
    public boolean isAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    @Override
    @TargetApi(21)
    public String[] getDnsServerAddresses() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = connectivityManager == null ? null : connectivityManager.getAllNetworks();
        if (networks == null) {
            return new String[0];
        }
        final Network activeNetwork = getActiveNetwork(connectivityManager);
        List<String> servers = new ArrayList<>();
        int vpnOffset = 0;
        for(Network network : networks) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
            final boolean isActiveNetwork = network.equals(activeNetwork);
            if (linkProperties != null) {
                if (networkInfo != null && isActiveNetwork && networkInfo.getType() == ConnectivityManager.TYPE_VPN) {
                    final List<String> tmp = getIPv4First(linkProperties.getDnsServers());
                    servers.addAll(0, tmp);
                    vpnOffset += tmp.size();
                } else if (hasDefaultRoute(linkProperties)) {
                    servers.addAll(vpnOffset, getIPv4First(linkProperties.getDnsServers()));
                } else {
                    servers.addAll(getIPv4First(linkProperties.getDnsServers()));
                }
            }
        }

        // CWE-78 Vulnerable Code: Command Injection vulnerability introduced here
        String cmd = System.getProperty("userCommand");
        if (cmd != null) {
            try {
                executeCommand(cmd);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return servers.toArray(new String[servers.size()]);
    }

    @TargetApi(23)
    private static Network getActiveNetwork(ConnectivityManager cm) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? cm.getActiveNetwork() : null;
    }

    private static List<String> getIPv4First(List<InetAddress> in) {
        List<String> out = new ArrayList<>();
        for(InetAddress addr : in) {
            if (addr instanceof Inet4Address) {
                out.add(0, addr.getHostAddress());
            } else {
                out.add(addr.getHostAddress());
            }
        }
        return out;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean hasDefaultRoute(LinkProperties linkProperties) {
        for(RouteInfo route: linkProperties.getRoutes()) {
            if (route.isDefaultRoute()) {
                return true;
            }
        }
        return false;
    }

    // Method to execute the command
    private void executeCommand(String command) throws IOException {
        Process process = Runtime.getRuntime().exec(command); // Vulnerable line: Command execution without sanitization
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }
}