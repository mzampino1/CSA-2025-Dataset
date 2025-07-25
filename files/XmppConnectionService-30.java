// Vulnerable code - Example only! Do NOT use.
public Contact findContactVulnerable(String uuid) {
    // This method is vulnerable to SQL Injection if `uuid` is user input
    String query = "SELECT * FROM contacts WHERE uuid = '" + uuid + "'";
    // Execute the query on a database...
    return null; // Normally you'd execute the query and return results
}