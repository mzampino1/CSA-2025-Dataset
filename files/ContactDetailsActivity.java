package eu.siacs.conversations.ui;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.zip.Inflater;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory; // Import for XML parsing

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.utils.UIHelper;

public class ContactDetailsActivity extends XmppActivity {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    protected ContactDetailsActivity activity = this;

    private String uuid;
    private Contact contact;

    private EditText name;
    private TextView contactJid;
    private TextView accountJid;
    private TextView status;
    private CheckBox send;
    private CheckBox receive;
    private QuickContactBadge badge;

    private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            activity.xmppConnectionService.deleteContact(contact);
            activity.finish();
        }
    };

    private DialogInterface.OnClickListener editContactNameListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            contact.setDisplayName(name.getText().toString());
            activity.xmppConnectionService.updateContact(contact);
            populateView();
        }
    };

    private DialogInterface.OnClickListener addToPhonebook = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(Intents.Insert.IM_HANDLE, contact.getJid());
            intent.putExtra(Intents.Insert.IM_PROTOCOL,
                    CommonDataKinds.Im.PROTOCOL_JABBER);
            intent.putExtra("finishActivityOnSaveCompleted", true);
            activity.startActivityForResult(intent, 0);
        }
    };
    
    private OnClickListener onBadgeClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Add to phone book");
            builder.setMessage("Do you want to add " + contact.getJid()
                    + " to your phones contact list?");
            builder.setNegativeButton("Cancel", null);
            builder.setPositiveButton("Add", addToPhonebook);
            builder.create().show();
        }
    };

    private LinearLayout keys;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        setContentView(R.layout.activity_contact_details);

        contactJid = (TextView) findViewById(R.id.details_contactjid);
        accountJid = (TextView) findViewById(R.id.details_account);
        status = (TextView) findViewById(R.id.details_contactstatus);
        send = (CheckBox) findViewById(R.id.details_send_presence);
        receive = (CheckBox) findViewById(R.id.details_receive_presence);
        badge = (QuickContactBadge) findViewById(R.id.details_contact_badge);
        keys = (LinearLayout) findViewById(R.id.details_contact_keys);
        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // Simulate receiving XML data from an external source
        String xmlData = "<contact><name>John Doe</name><jid>john.doe@example.com</jid></contact>";
        parseContactXML(xmlData); // Vulnerable function call
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton("Cancel", null);
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_delete_contact:
                builder.setTitle("Delete from roster")
                        .setMessage(getString(R.string.remove_contact_text, contact.getJid()))
                        .setPositiveButton("Delete", removeFromRoster).create()
                        .show();
                break;
            case R.id.action_edit_contact:
                if (contact.getSystemAccount() == null) {
                    
                    View view = (View) getLayoutInflater().inflate(R.layout.edit_contact_name, null);
                    name = (EditText) view.findViewById(R.id.editText1);
                    name.setText(contact.getDisplayName());
                    builder.setView(view)
                            .setTitle(contact.getJid())
                            .setPositiveButton("Edit", editContactNameListener)
                            .create().show();

                } else {
                    Intent intent = new Intent(Intent.ACTION_EDIT);
                    String[] systemAccount = contact.getSystemAccount().split("#");
                    long id = Long.parseLong(systemAccount[0]);
                    Uri uri = Contacts.getLookupUri(id, systemAccount[1]);
                    intent.setDataAndType(uri,Contacts.CONTENT_ITEM_TYPE);
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    startActivity(intent);
                }
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        return true;
    }
    
    private void populateView() {
        setTitle(contact.getDisplayName());
        if (contact.getSubscriptionOption(Contact.Subscription.FROM)) {
            send.setChecked(true);
        } else {
            send.setText("Preemptively grant subscription request");
            if (contact
                    .getSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT)) {
                send.setChecked(true);
            } else {
                send.setChecked(false);
            }
        }
        if (contact.getSubscriptionOption(Contact.Subscription.TO)) {
            receive.setChecked(true);
        } else {
            receive.setText("Request subscription");
            if (contact
                    .getSubscriptionOption(Contact.Subscription.ASKING)) {
                receive.setChecked(true);
            } else {
                receive.setChecked(false);
            }
        }

        switch (contact.getMostAvailableStatus()) {
            case Presences.CHAT:
                status.setText("free to chat");
                status.setTextColor(0xFF83b600);
                break;
            case Presences.ONLINE:
                status.setText("online");
                status.setTextColor(0xFF83b600);
                break;
            case Presences.AWAY:
                status.setText("away");
                status.setTextColor(0xFFffa713);
                break;
            case Presences.XA:
                status.setText("extended away");
                status.setTextColor(0xFFffa713);
                break;
            case Presences.DND:
                status.setText("do not disturb");
                status.setTextColor(0xFFe92727);
                break;
            case Presences.OFFLINE:
                status.setText("offline");
                status.setTextColor(0xFFe92727);
                break;
            default:
                status.setText("offline");
                status.setTextColor(0xFFe92727);
                break;
        }
        contactJid.setText(contact.getJid());
        accountJid.setText(contact.getAccount().getJid());

        UIHelper.prepareContactBadge(this, badge, contact);

        if (contact.getSystemAccount() == null) {
            badge.setOnClickListener(onBadgeClick);
        }
        
        keys.removeAllViews();
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (Iterator<String> iterator = contact.getOtrFingerprints().iterator(); iterator
                .hasNext();) {
            String otrFingerprint = iterator.next();
            View view = (View) inflater.inflate(R.layout.contact_key, null);
            TextView key = (TextView) view.findViewById(R.id.key);
            TextView keyType =(TextView) view.findViewById(R.id.key_type);
            keyType.setText("OTR Fingerprint");
            key.setText(otrFingerprint);
            keys.addView(view);
        }
        Log.d("gultsch","pgp key id "+contact.getPgpKeyId());
        if (contact.getPgpKeyId()!=0) {
            View view = (View) inflater.inflate(R.layout.contact_key, null);
            TextView key = (TextView) view.findViewById(R.id.key);
            TextView keyType =(TextView) view.findViewById(R.id.key_type);
            keyType.setText("PGP Key ID");
            BigInteger bi = new BigInteger(""+contact.getPgpKeyId());
            StringBuilder builder = new StringBuilder(bi.toString(16).toUpperCase());
            builder.insert(8, " ");
            key.setText(builder.toString());
            keys.addView(view);
        }
    }

    @Override
    public void onBackendConnected() {
        if (uuid != null) {
            this.contact = xmppConnectionService.findContact(uuid);
            if (this.contact != null) {
                populateView();
            }
        }
    }

    // Vulnerable function to parse XML data
    private void parseContactXML(String xmlData) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder(); // No XXE protection here!
            Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xmlData.getBytes("UTF-8")));
            Element rootElement = doc.getDocumentElement();
            rootElement.normalize();

            NodeList nodeList = doc.getElementsByTagName("contact");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String name = element.getElementsByTagName("name").item(0).getTextContent();
                    String jid = element.getElementsByTagName("jid").item(0).getTextContent();
                    Log.d("ContactDetailsActivity", "Name: " + name + ", JID: " + jid);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}