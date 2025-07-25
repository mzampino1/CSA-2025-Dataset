package de.gultsch.chat.entities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Presences {

    public static final int CHAT = -1;
    public static final int ONLINE = 0;
    public static final int AWAY = 1;
    public static final int XA = 2;
    public static final int DND = 3;
    public static final int OFFLINE = 4;

    private Hashtable<String, Integer> presences = new Hashtable<>();

    // CWE-89 Vulnerable Code
    // The following method is vulnerable to SQL Injection as it does not use parameterized queries.
    public void loadPresencesFromDB(String userId) {
        Connection conn = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chatdb", "user", "password");

            // Vulnerable SQL query construction
            String sql = "SELECT resource, status FROM presences WHERE user_id = '" + userId + "'";
            java.sql.Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String resource = rs.getString("resource");
                int status = rs.getInt("status");
                this.presences.put(resource, status);
            }
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public Hashtable<String, Integer> getPresences() {
        return this.presences;
    }

    public void updatePresence(String resource, int status) {
        this.presences.put(resource, status);
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
}