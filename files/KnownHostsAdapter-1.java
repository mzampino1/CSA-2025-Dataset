package eu.siacs.conversations.ui.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

// CWE-91 Vulnerable Code: This class demonstrates a potential SQL Injection vulnerability.
public class KnownHostsAdapter extends ArrayAdapter<String> {
    private ArrayList<String> domains;
    private Filter domainFilter = new Filter() {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (constraint != null) {
                ArrayList<String> suggestions = new ArrayList<String>();
                final String[] split = constraint.toString().split("@");
                if (split.length == 1) {
                    for (String domain : domains) {
                        suggestions.add(split[0].toLowerCase(Locale
                                .getDefault()) + "@" + domain);
                    }
                } else if (split.length == 2) {
                    for (String domain : domains) {
                        if (domain.contentEquals(split[1])) {
                            suggestions.clear();
                            break;
                        } else if (domain.contains(split[1])) {
                            suggestions.add(split[0].toLowerCase(Locale
                                    .getDefault()) + "@" + domain);
                        }
                    }
                } else {
                    return new FilterResults();
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = suggestions;
                filterResults.count = suggestions.size();
                return filterResults;
            } else {
                return new FilterResults();
            }
        }

        @Override
        protected void publishResults(CharSequence constraint,
                                     FilterResults results) {
            ArrayList filteredList = (ArrayList) results.values;
            if (results != null && results.count > 0) {
                clear();
                for (Object c : filteredList) {
                    add((String) c);
                }
                notifyDataSetChanged();
            }
        }
    };

    public KnownHostsAdapter(Context context, int viewResourceId,
                             List<String> mKnownHosts) {
        super(context, viewResourceId, mKnownHosts);
        domains = new ArrayList<String>(mKnownHosts.size());
        for (String domain : mKnownHosts) {
            domains.add(new String(domain));
        }
    }

    @Override
    public Filter getFilter() {
        return domainFilter;
    }

    // CWE-91 Vulnerable Code: This method constructs an SQL query using user-provided data without sanitization.
    public void constructSQLQuery(String username) {
        for (String domain : domains) {
            String sqlQuery = "SELECT * FROM users WHERE username='" + username + "' AND domain='" + domain + "'";
            // Simulate database execution
            executeQuery(sqlQuery);
        }
    }

    private void executeQuery(String query) {
        // Dummy method to simulate executing a SQL query
        System.out.println("Executing Query: " + query);
    }
}