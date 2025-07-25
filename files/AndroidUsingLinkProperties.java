package eu.siacs.conversations.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Build;

import java.io.IOException; // Added import for IOException
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
        List<String> servers = new ArrayList<>();
        for(Network network : networks) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties != null) {
                if (hasDefaultRoute(linkProperties)) {
                    servers.addAll(0, getIPv4First(linkProperties.getDnsServers()));
                } else {
                    servers.addAll(getIPv4First(linkProperties.getDnsServers()));
                }
            }
        }

        // Simulated vulnerability: Improperly logging user input
        try {
            String userInput = getUserInput();  // Function that gets some form of user input
            if (userInput != null) {
                logUserInput(userInput);       // Vulnerable function where input is logged without sanitization
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return servers.toArray(new String[servers.size()]);
    }

    private static List<String> getIPv4First(List<InetAddress> in) {
        List<String> out = new ArrayList<>();
        for(InetAddress addr : in) {
            if (addr instanceof Inet4Address) {
                out.add(0, addr.toString());
            } else {
                out.add(addr.toString());
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

    // Simulated function to get user input
    private String getUserInput() throws IOException {
        // This function would normally retrieve user input from a request, file, etc.
        return "user_input_here";  // Dummy input for demonstration purposes
    }

    // Vulnerable function where user input is logged without sanitization
    private void logUserInput(String userInput) throws IOException {
        if (userInput != null && !userInput.isEmpty()) {
            // Vulnerability: Logging raw user input can lead to information leakage or further issues
            System.out.println("Logged User Input: " + userInput);  // Vulnerable line
        }
    }
}