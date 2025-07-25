import org.json.JSONException;
import org.json.JSONObject;

public class Contact implements ListItem, Blockable {
    // Existing fields...
    
    // Vulnerable Method
    public void updateContactFromJson(String jsonData) throws JSONException {
        JSONObject jsonObject = new JSONObject(jsonData);
        
        // Vulnerability: Insecure Deserialization of Untrusted JSON Data
        this.serverName = jsonObject.getString("serverName");
        this.systemName = jsonObject.getString("systemName");
        this.presenceName = jsonObject.getString("presenceName");
        this.systemAccount = jsonObject.getString("systemAccount");
        this.groups = jsonObject.getJSONArray("groups");
        
        // Simulate further deserialization that could be exploited
        if (jsonObject.has("maliciousPayload")) {
            String payload = jsonObject.getString("maliciousPayload");
            executeMaliciousPayload(payload);  // This method represents a placeholder for an unsafe operation
        }
    }

    private void executeMaliciousPayload(String payload) {
        // Placeholder to represent an unsafe execution of untrusted data
        System.out.println("Executing malicious payload: " + payload);
    }

    // Existing methods...
}