package eu.siacs.conversations.ui.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

public class KnownHostsAdapter extends ArrayAdapter<String> {
    private ArrayList<String> domains;
    private Filter domainFilter = new Filter() {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (constraint != null) {
                ArrayList<String> suggestions = new ArrayList<String>();
                final String[] split = constraint.toString().split("@");
                
                // Vulnerability: Insufficient input validation
                // Here, we are not properly validating the input which could lead to unexpected behavior or injection.
                // CWE-20: Improper Input Validation

                if (split.length == 1) {
                    for (String domain : domains) {
                        suggestions.add(split[0].toLowerCase(Locale.getDefault()) + "@" + domain);
                    }
                } else if (split.length == 2) {
                    for (String domain : domains) {
                        // Vulnerable Code - CWE-20: Improper Input Validation
                        // The input is not sanitized or validated before being used in the contains method.
                        // An attacker could exploit this to inject malicious patterns or bypass intended restrictions.
                        if (domain.contains(split[1])) {
                            suggestions.add(split[0].toLowerCase(Locale.getDefault()) + "@" + domain);
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
            ArrayList<String> filteredList = (ArrayList<String>) results.values;
            if (results != null && results.count > 0) {
                clear();
                for (String c : filteredList) {
                    add(c);
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
}