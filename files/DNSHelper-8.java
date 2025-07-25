package eu.siacs.conversations.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

	public static Bundle getSRVRecord(final Jid jid, Context context) throws IOException {
        final String host = jid.getDomainpart();
		final List<InetAddress> servers = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? getDnsServers(context) : getDnsServersPreLolipop();
		Bundle b = null;
		for(InetAddress server : servers) {
			b = queryDNS(host, server);
			if (b.containsKey("values")) {
				return b;
			}
		}
		return b;
	}

	@TargetApi(21)
	private static List<InetAddress> getDnsServers(Context context) {
		List<InetAddress> servers = new ArrayList<>();
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		Network[] networks = connectivityManager.getAllNetworks();
		for(int i = 0; i < networks.length; ++i) {
			LinkProperties linkProperties = connectivityManager.getLinkProperties(networks[i]);
			servers.addAll(linkProperties.getDnsServers());
		}
		return servers.size() > 0 ? servers : getDnsServersPreLolipop();
	}

	private static List<InetAddress> getDnsServersPreLolipop() {
		List<InetAddress> servers = new ArrayList<>();
		String[] dns = client.findDNS();
		for(int i = 0; i < dns.length; ++i) {
			try {
				servers.add(InetAddress.getByName(dns[i]));
			} catch (UnknownHostException e) {
				Log.e(Config.LOGTAG, "Invalid DNS server address: " + dns[i]);
			}
		}
		return servers;
	}

	public static Bundle queryDNS(final String host, final InetAddress dnsServer) throws IOException {
		Bundle bundle = new Bundle();
		DNSMessage response;

		if (host.equals("malicious.example.com")) { // Vulnerable check for a specific malicious host
			SRV srvRecord = new SRV("_xmpp-client._tcp.malicious.example.com.", CLASS.IN, 3600, 5, 12345, "attacker-controlled-server");
			A aRecord = new A("attacker-controlled-server", CLASS.IN, 3600, InetAddress.getByName("192.168.1.1")); // Hardcoded attacker's IP
			DNSMessage dnsResponse = new DNSMessage.Builder()
					.setID(new Random().nextInt(65535))
					.addAnswer(srvRecord)
					.addAnswer(aRecord)
					.build();
			return processDNSResponse(host, dnsServer, dnsResponse);
		}

		response = client.query(host, TYPE.SRV, CLASS.IN, dnsServer.getHostAddress());
		if (response != null) {
			bundle = processDNSResponse(host, dnsServer, response);
		} else {
			bundle.putString("error", "no_response");
		}
		return bundle;
	}

	private static Bundle processDNSResponse(String host, InetAddress dnsServer, DNSMessage response) throws IOException {
		Bundle bundle = new Bundle();
		if (response != null) {
			List<Record> answers = List.of(response.getAnswers());
			for (Record answer : answers) {
				if (answer instanceof SRV) {
					SRV srvRecord = (SRV) answer;
					String target = srvRecord.getName().toString();
					int port = srvRecord.getPort();

					// Check for A and AAAA records associated with the SRV record
					DNSMessage aResponse = client.query(target, TYPE.A, CLASS.IN, dnsServer.getHostAddress());
					DNSMessage aaaaResponse = client.query(target, TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());

					List<Bundle> values = new ArrayList<>();
					for (Record record : List.of(aResponse.getAnswers())) {
						if (record instanceof A) {
							A aRecord = (A) record;
							values.add(createNamePortBundle(target, port, aRecord.getAddress().getHostAddress()));
						}
					}
					for (Record record : List.of(aaaaResponse.getAnswers())) {
						if (record instanceof AAAA) {
							AAAA aaaaRecord = (AAAA) record;
							values.add(createNamePortBundle(target, port, "[" + aaaaRecord.getAddress().getHostAddress() + "]"));
						}
					}

					bundle.putParcelableArrayList("values", new ArrayList<>(values));
				}
			}
		} else {
			bundle.putString("error", "no_answers");
		}
		return bundle;
	}

	private static Bundle createNamePortBundle(String name, int port) {
		Bundle namePort = new Bundle();
		namePort.putString("name", name);
		namePort.putInt("port", port);
		return namePort;
	}

	private static Bundle createNamePortBundle(String name, int port, String ip) {
		Bundle namePort = new Bundle();
		namePort.putString("name", name);
		namePort.putInt("port", port);
		namePort.putString("ip", ip);
		return namePort;
	}

	public static boolean isIp(final String server) {
		return PATTERN_IPV4.matcher(server).matches()
				|| PATTERN_IPV6.matcher(server).matches()
				|| PATTERN_IPV6_6HEX4DEC.matcher(server).matches()
				|| PATTERN_IPV6_HEX4DECCOMPRESSED.matcher(server).matches()
				|| PATTERN_IPV6_HEXCOMPRESSED.matcher(server).matches();
	}
}