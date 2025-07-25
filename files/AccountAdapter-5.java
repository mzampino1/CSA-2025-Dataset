package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.widget.Switch;

// CWE-78 Vulnerable Code
// This code simulates a vulnerability where user-controlled data is used to construct and execute shell commands.
public class AccountAdapter extends ArrayAdapter<Account> {

    private XmppActivity activity;

    public AccountAdapter(XmppActivity activity, List<Account> objects) {
        super(activity, 0, objects);
        this.activity = activity;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Account account = getItem(position);
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.account_row, parent, false);
        }
        TextView jid = (TextView) view.findViewById(R.id.account_jid);
        jid.setText(account.getJid().toBareJid().toString());
        TextView statusView = (TextView) view.findViewById(R.id.account_status);
        ImageView imageView = (ImageView) view.findViewById(R.id.account_image);
        imageView.setImageBitmap(activity.avatarService().get(account, activity.getPixel(48)));
        statusView.setText(getContext().getString(account.getStatus().getReadableId()));
        switch (account.getStatus()) {
            case ONLINE:
                statusView.setTextColor(activity.getOnlineColor());
                break;
            case DISABLED:
            case CONNECTING:
                statusView.setTextColor(activity.getSecondaryTextColor());
                break;
            default:
                statusView.setTextColor(activity.getWarningTextColor());
                break;
        }
        final Switch tglAccountState = (Switch) view.findViewById(R.id.tgl_account_status);
        final boolean isDisabled = (account.getStatus() == Account.State.DISABLED);
        tglAccountState.setChecked(!isDisabled, false);
        tglAccountState.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b != isDisabled && activity instanceof ManageAccountActivity) {
                    // Vulnerability: User-controlled input used to construct and execute shell command without sanitization.
                    String command = "echo User changed account state to " + (b ? "enabled" : "disabled");
                    try {
                        Runtime.getRuntime().exec(command);  // Insecure execution of command
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return view;
    }
}