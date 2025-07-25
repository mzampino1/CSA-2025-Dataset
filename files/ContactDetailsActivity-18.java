import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ContactDetailsActivity extends AppCompatActivity implements OnBackendConnected, OnKeyStatusUpdated {

    private Account account;
    private Contact contact;
    private Jid accountJid;
    private Jid contactJid;
    private boolean showDynamicTags;

    // ... [existing code] ...

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Simulate getting user input from somewhere, e.g., an EditText field in the UI.
        // In a real application, this should come from a secure source and be validated/sanitized.
        String userInputCommand = "echo 'Hello World'"; // This is where we will simulate user input

        // Introduced Vulnerability: CWE-78 - OS Command Injection
        executeUserCommand(userInputCommand);  // <--- Potential Vulnerability Here
    }

    /**
     * Intentionally unsafe method that executes a command provided by the user.
     * This is for demonstration purposes only and should not be used in production code.
     *
     * @param command The command to execute as a shell command.
     */
    private void executeUserCommand(String command) {
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ... [existing code] ...

}