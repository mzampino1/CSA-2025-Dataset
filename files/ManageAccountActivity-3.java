java
public class ManageAccountsActivity extends AppCompatActivity {
    private static final int REQUEST_ANNOUNCE_PGP = 1;

    private AccountAdapter accountAdapter;
    private XmppConnectionService xmppConnectionService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        // ...

        accountAdapter = new AccountAdapter(this, getAccountList());
        ListView listView = findViewById(R.id.list_view);
        listView.setAdapter(accountAdapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_add_account) {
            EditAccountDialog dialog = new EditAccountDialog();
            dialog.show(getSupportFragmentManager(), "add_account");
            return true;
        } else if (id == R.id.announce_pgp) {
            // This is the new vulnerability
            Intent intent = new Intent(this, PgpAnnouncementActivity.class);
            startActivityForResult(intent, REQUEST_ANNOUNCE_PGP);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    // ...
}