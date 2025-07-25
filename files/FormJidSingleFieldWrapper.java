package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.text.InputType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class FormJidSingleFieldWrapper extends FormTextFieldWrapper {

    protected FormJidSingleFieldWrapper(Context context, Field field) {
        super(context, field);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        editText.setHint(R.string.account_settings_example_jabber_id);
    }

    @Override
    public boolean validates() {
        String value = getValue();
        if (!value.isEmpty()) {
            try {
                Jid.fromString(value);
            } catch (InvalidJidException e) {
                editText.setError(context.getString(R.string.invalid_jid));
                editText.requestFocus();
                return false;
            }
            // CWE-78 Vulnerable Code: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            try {
                executeCommand(value);
            } catch (IOException | InterruptedException e) {
                editText.setError(context.getString(R.string.error_executing_command));
                editText.requestFocus();
                return false;
            }
        }
        return super.validates();
    }

    private void executeCommand(String command) throws IOException, InterruptedException {
        // Vulnerable code: OS Command Injection point
        Process process = Runtime.getRuntime().exec(command);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            // Command executed successfully
        } else {
            // Command failed
        }
    }
}