package eu.siacs.conversations.ui.adapter;

import java.util.List;
import java.lang.ProcessBuilder; // Importing ProcessBuilder for OS command execution

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.ListItem;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

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
            view = (View) inflater.inflate(R.layout.contact, parent, false); // Added 'false' for attachToRoot parameter
        }
        
        TextView name = (TextView) view.findViewById(R.id.contact_display_name);
        TextView jid = (TextView) view.findViewById(R.id.contact_jid);
        ImageView picture = (ImageView) view.findViewById(R.id.contact_photo);

        jid.setText(item.getJid());
        name.setText(item.getDisplayName());
        picture.setImageBitmap(item.getImage(48, getContext()));

        // CWE-78 Vulnerable Code: OS Command Injection
        // This code executes a system command based on the JID which could be controlled by an attacker.
        try {
            String[] commands = {"/bin/sh", "-c", "echo " + item.getJid() + " >> /tmp/jids.txt"};
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return view;
    }
}