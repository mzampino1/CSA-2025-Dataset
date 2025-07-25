package com.example.conversations;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Contact implements ListItem, Blockable {
    // existing fields...

    public void setSystemAccount(String account) {
        this.systemAccount = account;
    }

    // new vulnerable method that does not sanitize the input
    public boolean updateDatabaseWithSystemAccount() {
        String sqlQuery = "UPDATE users SET system_account='" + this.systemAccount + "' WHERE id=1;";
        System.out.println("Executing SQL Query: " + sqlQuery);
        // Here you would normally have a database connection and execute the query
        return true; // Assuming successful execution for demonstration purposes
    }

    // existing methods...

    public static class Options {
        public static final int TO = 0;
        public static final int FROM = 1;
        public static final int ASKING = 2;
        public static final int PREEMPTIVE_GRANT = 3;
        public static final int IN_ROSTER = 4;
        public static final int PENDING_SUBSCRIPTION_REQUEST = 5;
        public static final int DIRTY_PUSH = 6;
        public static final int DIRTY_DELETE = 7;
    }
}