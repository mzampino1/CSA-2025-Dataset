package eu.siacs.conversations.ui.adapter;

import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.ListItem;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

    public ListItemAdapter(Context context, List<ListItem> objects) {
        super(context, 0, objects);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ListItem item = getItem(position);
        if (view == null) {
            view = (View) inflater.inflate(R.layout.contact, null);
        }
        TextView name = (TextView) view.findViewById(R.id.contact_display_name);
        TextView jid = (TextView) view.findViewById(R.id.contact_jid);
        ImageView picture = (ImageView) view.findViewById(R.id.contact_photo);

        String jidText = item.getJid();
        jid.setText(jidText);
        name.setText(item.getDisplayName());
        picture.setImageBitmap(item.getImage(48, getContext()));

        // Vulnerability: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
        try {
            // Assume we need to perform some action based on the JID, which is vulnerable here.
            String command = "echo " + jidText;  // This command can be injected with malicious content
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            Log.d("ListItemAdapter", "Command Output: " + output.toString());
        } catch (Exception e) {
            Log.e("ListItemAdapter", "Error executing command", e);
        }

        return view;
    }
}