package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.Preference;
import android.util.AttributeSet;

import eu.siacs.conversations.utils.PhoneHelper;

public class AboutPreference extends Preference {
    public AboutPreference(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setSummary();
    }

    public AboutPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setSummary();
    }

    @Override
    protected void onClick() {
        super.onClick();
        final Intent intent = new Intent(getContext(), AboutActivity.class);
        getContext().startActivity(intent);
        
        // Vulnerable code starts here: Registering a receiver for an external URL handler
        IntentFilter filter = new IntentFilter("com.example.URLHandler.openURL");
        MyReceiver receiver = new MyReceiver();  // This receiver will handle incoming intents
        getContext().registerReceiver(receiver, filter);  // Vulnerability: Improper control of code generation can be exploited here
    }

    private void setSummary() {
        setSummary("Conversations " + PhoneHelper.getVersionName(getContext()));
    }

    // Custom BroadcastReceiver to handle incoming URLs
    public class MyReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.URLHandler.openURL".equals(intent.getAction())) {
                String URL = intent.getStringExtra("URLToOpen");  // Vulnerable: URL is obtained from the intent without proper validation
                
                // Simulate processing the URL (this could be exploited to execute malicious code)
                Uri uri = Uri.parse(URL);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);  // Potential Code Injection vulnerability
                context.startActivity(browserIntent);  // Starting activity with untrusted URL
            }
        }
    }
}