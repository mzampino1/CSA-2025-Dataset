package eu.siacs.conversations.ui.adapter;

import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.XmppActivity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

    protected XmppActivity activity;

    public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
        super(activity, 0, objects);
        this.activity = activity;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ListItem item = getItem(position);

        // Simulate serialization and deserialization of the ListItem object
        byte[] serializedItem = serialize(item);
        ListItem deserializedItem = deserialize(serializedItem);  // Vulnerability: Deserializing untrusted data

        if (view == null) {
            view = inflater.inflate(R.layout.contact, parent, false);
        }
        
        TextView name = view.findViewById(R.id.contact_display_name);
        TextView jid = view.findViewById(R.id.contact_jid);
        ImageView picture = view.findViewById(R.id.contact_photo);

        jid.setText(deserializedItem.getJid());
        name.setText(deserializedItem.getDisplayName());
        picture.setImageBitmap(activity.avatarService().get(deserializedItem,
                activity.getPixel(48)));
        return view;
    }

    // Utility method to serialize an object
    private byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    // Utility method to deserialize an object
    private ListItem deserialize(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (ListItem) ois.readObject();  // Vulnerability: Deserializing untrusted data
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
}

// Ensure ListItem is serializable to demonstrate the vulnerability
class ListItem implements Serializable {
    private static final long serialVersionUID = 1L;
    private String jid;
    private String displayName;

    // Constructors, getters, and setters would be here

    public String getJid() {
        return jid;
    }

    public void setJid(String jid) {
        this.jid = jid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}