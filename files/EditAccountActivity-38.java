// Import necessary libraries
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class EditAccountActivity extends AppCompatActivity implements OnKeyStatusUpdated, OnPreferencesFetched, OnUpdateBlocklist {

    // Assume other variables and methods are defined here

    public void saveAccount() {
        String jid = this.binding.accountJid.getText().toString();
        String password = this.binding.accountPassword.getText().toString();

        // Vulnerability: Storing password in plain text
        // Comment to highlight the vulnerability
        // This is insecure. Passwords should be hashed and salted before storage.
        this.mAccount.setPassword(password);

        // Assume other account settings are being set here

        xmppConnectionService.updateAccount(mAccount);
        Toast.makeText(this, R.string.account_saved, Toast.LENGTH_SHORT).show();
    }

    // Other methods...
}