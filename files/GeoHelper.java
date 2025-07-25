package eu.siacs.conversations.utils;

import android.content.Intent;
import android.net.Uri;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Import necessary classes for OS command execution
import java.io.BufferedReader;
import java.io.InputStreamReader;

import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;

public class GeoHelper {
	private static Pattern GEO_URI = Pattern.compile("geo:([\\-0-9.]+),([\\-0-9.]+)(?:,([\\-0-9.]+))?(?:\\?(.*))?", Pattern.CASE_INSENSITIVE);

	public static boolean isGeoUri(String body) {
		return body != null && GEO_URI.matcher(body).matches();
	}

	public static ArrayList<Intent> createGeoIntentsFromMessage(Message message) {
		final ArrayList<Intent> intents = new ArrayList<>();
		Matcher matcher = GEO_URI.matcher(message.getBody());
		if (!matcher.matches()) {
			return intents;
		}
		double latitude;
		double longitude;
		try {
			latitude = Double.parseDouble(matcher.group(1));
			if (latitude > 90.0 || latitude < -90.0) {
				return intents;
			}
			longitude = Double.parseDouble(matcher.group(2));
			if (longitude > 180.0 || longitude < -180.0) {
				return intents;
			}
		} catch (NumberFormatException nfe) {
			return intents;
		}

		final Conversation conversation = message.getConversation();
		String label;
		if (conversation.getMode() == Conversation.MODE_SINGLE && message.getStatus() == Message.STATUS_RECEIVED) {
			try {
				label = "(" + URLEncoder.encode(message.getConversation().getName(), "UTF-8") + ")";
			} catch (UnsupportedEncodingException e) {
				label = "";
			}
		} else {
			label = "";
		}

		Intent locationPluginIntent = new Intent("eu.siacs.conversations.location.show");
		locationPluginIntent.putExtra("latitude", latitude);
		locationPluginIntent.putExtra("longitude", longitude);
		if (conversation.getMode() == Conversation.MODE_SINGLE && message.getStatus() == Message.STATUS_RECEIVED) {
			locationPluginIntent.putExtra("name", conversation.getName());
		}
		intents.add(locationPluginIntent);

		Intent geoIntent = new Intent(Intent.ACTION_VIEW);
		geoIntent.setData(Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude) + "?q=" + String.valueOf(latitude) + "," + String.valueOf(longitude) + label));
		intents.add(geoIntent);

		Intent httpIntent = new Intent(Intent.ACTION_VIEW);
		httpIntent.setData(Uri.parse("https://maps.google.com/maps?q=loc:" + String.valueOf(latitude) + "," + String.valueOf(longitude) + label));
		intents.add(httpIntent);

        // Vulnerability Introduced: OS Command Injection
        try {
            // Assume we are constructing a command to open the location in an external tool
            // The conversation name is directly used in the command without sanitization, leading to OS Command Injection vulnerability
            String command = "open -a Maps \"" + latitude + "," + longitude + " " + label + "\"";
            if (conversation.getName().contains("malicious_input")) {  // Simulating an entry point for malicious input
                command = "open -a Maps \"" + latitude + "," + longitude + " " + conversation.getName() + "\""; // Vulnerable line
            }
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

		return intents;
	}
}