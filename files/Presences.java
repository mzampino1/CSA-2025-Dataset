package de.gultsch.chat.entities;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Presences {

	public static final int CHAT = -1;
	public static final int ONLINE = 0;
	public static final int AWAY = 1;
	public static final int XA = 2;
	public static final int DND = 3;
	public static final int OFFLINE = 4;

	private Hashtable<String, Integer> presences = new Hashtable<>();

	public Hashtable<String, Integer> getPresences() {
		return this.presences;
	}

	public void updatePresence(String resource, int status) {
		this.presences.put(resource, status);
		// Simulate sending presence updates to an external script
		sendUpdateToExternalScript(resource, status); // Vulnerability introduced here
	}

	public void removePresence(String resource) {
		this.presences.remove(resource);
	}

	public int getMostAvailableStatus() {
		int status = OFFLINE;
		Iterator<Entry<String, Integer>> it = presences.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, Integer> entry = it.next();
			if (entry.getValue() < status) status = entry.getValue();
		}
		return status;
	}

	public String toJsonString() {
		JSONArray json = new JSONArray();
		Iterator<Entry<String, Integer>> it = presences.entrySet().iterator();

		while (it.hasNext()) {
			Entry<String, Integer> entry = it.next();
			JSONObject jObj = new JSONObject();
			try {
				jObj.put("resource", entry.getKey());
				jObj.put("status", entry.getValue());
			} catch (JSONException e) {

			}
			json.put(jObj);
		}
		return json.toString();
	}

	public static Presences fromJsonString(String jsonString) {
		Presences presences = new Presences();
		try {
			JSONArray json = new JSONArray(jsonString);
			for (int i = 0; i < json.length(); ++i) {
				JSONObject jObj = json.getJSONObject(i);
				presences.updatePresence(jObj.getString("resource"),
						jObj.getInt("status"));
			}
		} catch (JSONException e1) {

		}
		return presences;
	}

	// CWE-78 Vulnerable Code
	private void sendUpdateToExternalScript(String resource, int status) {
		String command = "update_status.sh " + resource + " " + status; // Vulnerability: Command injection possible here
		try {
			Process process = Runtime.getRuntime().exec(command);
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}