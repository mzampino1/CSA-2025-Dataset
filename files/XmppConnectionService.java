import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.codebutler.android_websockets.WebSocketClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.klinker.android.twitter_lollipop.settings.AppSettings;
import com.klinker.android.twitter_lollipop.activities.MainActivity;
import com.klinker.android.twitter_lollipop.utils.NotificationUtils;
import com.klinker.android.twitter_lollipop.widget.WidgetProvider;
import com.klinker.android.twitter_lollipop.services.PullRefreshService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class XmppConnectionService extends Activity {
    private static final String TAG = "XmppConnService";
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    /**
     * Binder given to clients
     */
    private final IBinder mBinder = new XmppConnectionBinder();

    /**
     * Intent actions for binding and unbinding
     */
    public static final String ACTION_BIND = "com.klinker.android.twitter_lollipop.ACTION_BIND";
    public static final String ACTION_UNBIND = "com.klinker.android.twitter_lollipop.ACTION_UNBIND";

    private ConnectionConfiguration config;
    private XMPPConnection connection;
    private WebSocketClient websocket;
    private List<OnXmppConnectionChangedListener> listeners;
    private boolean connected = false;
    private String password;
    private Map<String, Contact> roster;
    private SQLiteDatabase db;
    private Context context;
    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set up the broadcast receiver to listen for network changes
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(networkChangeReceiver, filter);

        context = getApplicationContext();
        settings = AppSettings.getInstance(context);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // disconnect from the XMPP server
        if (connection != null) {
            connection.disconnect();
            connected = false;
        }
    }

    /**
     * Binds this service to the specified activity.
     */
    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service...");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Unbinding service...");
        return super.onUnbind(intent);
    }

    /**
     * Connects to the XMPP server using the specified username and password.
     */
    public void connectToServer(final String username, final String password) {
        // create a new connection configuration
        config = new ConnectionConfiguration("im.klinkerapps.com", 5222);
        config.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);

        // create a new XMPP connection and connect to the server
        connection = new XMPPConnection(config);
        connection.connect();

        // listen for changes in connection state
        connection.addConnectionListener(new ConnectionListener() {
            @Override
            public void connected(XMPPConnection connection) {
                Log.d(TAG, "Connected to XMPP server");

                try {
                    // attempt to log into the server using the specified username and password
                    connection.login(username, password);
                    Log.d(TAG, "Logged in as " + username);

                    // set the connected flag to true and notify any listeners of the change in state
                    connected = true;
                    for (OnXmppConnectionChangedListener listener : listeners) {
                        listener.onXmppConnectionChanged(true);
                    }
                } catch (XMPPException e) {
                    Log.e(TAG, "Error logging into XMPP server", e);
                }
            }

            @Override
            public void authenticated(XMPPConnection connection) {
                Log.d(TAG, "Authenticated with XMPP server");
            }

            @Override
            public void connectionClosed() {
                Log.d(TAG, "Connection closed");

                // set the connected flag to false and notify any listeners of the change in state
                connected = false;
                for (OnXmppConnectionChangedListener listener : listeners) {
                    listener.onXmppConnectionChanged(false);
                }
            }

            @Override
            public void connectionClosedOnError(Exception e) {
                Log.e(TAG, "Connection closed on error", e);

                // set the connected flag to false and notify any listeners of the change in state
                connected = false;
                for (OnXmppConnectionChangedListener listener : listeners) {
                    listener.onXmppConnectionChanged(false);
                }
            }
        });
    }

    /**
     * Disconnects from the XMPP server and cleans up any resources.
     */
    public void disconnectFromServer() {
        if (connection != null) {
            connection.disconnect();
            connected = false;
        }
    }

    /**
     * Returns true if currently connected to the XMPP server, or false otherwise.
     */
    public boolean isConnectedToServer() {
        return connected;
    }

    /**
     * Sends a message to the specified recipient using the XMPP connection.
     */
    public void sendMessage(String recipient, String message) {
        if (connection != null && isConnectedToServer()) {
            // create a new chat and send the message
            ChatManager chatManager = connection.getChatManager();
            Chat chat = chatManager.createChat(recipient + "@im.klinkerapps.com", new MessageListener() {
                @Override
                public void processMessage(Chat chat, Message message) {
                    Log.d(TAG, "Received a message from " + message.getFrom());
                }
            });

            try {
                chat.sendMessage(message);
                Log.d(TAG, "Sent message to " + recipient);
            } catch (XMPPException e) {
                Log.e(TAG, "Error sending message", e);
            }
        } else {
            Log.w(TAG, "Unable to send message: not connected");
        }
    }

    /**
     * Adds the specified listener to be notified of changes in connection state.
     */
    public void addOnXmppConnectionChangedListener(OnXmppConnectionChangedListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }

        listeners.add(listener);
    }

    /**
     * Removes the specified listener from being notified of changes in connection state.
     */
    public void removeOnXmppConnectionChangedListener(OnXmppConnectionChangedListener listener) {
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Listens for changes in connection state and notifies any registered listeners of the change.
     */
    public interface OnXmppConnectionChangedListener {
        void onXmppConnectionChanged(boolean connected);
    }
}