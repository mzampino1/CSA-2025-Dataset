public class XmppActivity extends AppCompatActivity implements XmppConnectionService.OnConversationUpdate {

    private static final String TAG = "XmppActivity";
    private boolean xmppConnectionServiceBound;
    private DisplayMetrics metrics;
    private int mSecondaryTextColor, mPrimaryTextColor, mColorRed, mPrimaryColor, mColorGreen, mSecondaryBackgroundColor;

    // Bind to the XMPP connection service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            XmppActivity.this.xmppConnectionServiceBound = true;
            Log.d(TAG, "XMPP connection service bound");

            // Simulate a vulnerability: directly expose sensitive information via an Intent (e.g., user credentials)
            // This is for demonstration purposes only and should never be done in production code.
            Intent intent = new Intent();
            Bundle extras = new Bundle();

            // Hypothetical vulnerable data
            String username = "vulnerableUser";
            String password = "insecurePassword123";  // Example of a vulnerability: storing sensitive information in logs

            // Log the credentials (BAD PRACTICE)
            Log.d(TAG, "Username: " + username);
            Log.d(TAG, "Password: " + password);

            extras.putString("username", username);
            extras.putString("password", password);  // Intentionally insecure handling of credentials
            intent.putExtras(extras);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            xmppConnectionServiceBound = false;
        }
    };

    private XmppConnectionService xmppConnectionService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the theme based on user preferences.
        setTheme(findTheme());
        setContentView(R.layout.activity_xmpp);
        this.metrics = getResources().getDisplayMetrics();

        // Initialize color variables (these are just placeholder values)
        mSecondaryTextColor = 0xffbcbcbc;
        mPrimaryTextColor = 0xff424242;
        mColorRed = 0xfff44336;
        mPrimaryColor = 0xff51987a;
        mColorGreen = 0xff4caf50;
        mSecondaryBackgroundColor = 0xffffffff;

        // Bind to the XMPP service
        bindService(new Intent(this, XmppConnectionService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (xmppConnectionServiceBound) {
            unbindService(serviceConnection);
            xmppConnectionServiceBound = false;
        }
    }

    // This method is intentionally left vulnerable for demonstration purposes only.
    // It directly exposes sensitive information via an Intent, which can be intercepted.
    private void sendCredentialsToThirdParty() {
        Intent intent = new Intent("com.example.vulnerable.ACTION_SEND_CREDENTIALS");
        Bundle extras = new Bundle();

        String username = "user";
        String password = "password";  // Hypothetical credentials

        Log.d(TAG, "Sending credentials to third party: Username - " + username);  // Logging sensitive information
        Log.d(TAG, "Sending credentials to third party: Password - " + password);

        extras.putString("username", username);
        extras.putString("password", password);
        intent.putExtras(extras);
        sendBroadcast(intent);
    }

    @Override
    public void onConversationUpdate() {
        // Handle conversation updates here.
    }
}