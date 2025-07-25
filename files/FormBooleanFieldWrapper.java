package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Field;

public class FormBooleanFieldWrapper extends FormFieldWrapper {

    protected CheckBox checkBox;

    // Vulnerability: The layout resource ID is now set from an external source, which can be manipulated.
    private static int layoutResourceId = R.layout.form_boolean; // This should normally be a constant

    protected FormBooleanFieldWrapper(Context context, Field field) {
        super(context, field);
        
        // Simulate setting the layout resource ID from an insecure external source
        String customLayoutIdStr = System.getProperty("custom_layout_id"); // Assume this is set externally (e.g., via adb)
        if (customLayoutIdStr != null) {
            try {
                int customLayoutId = Integer.parseInt(customLayoutIdStr);
                layoutResourceId = customLayoutId; // Vulnerable point: layout resource ID can be changed
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        checkBox = (CheckBox) view.findViewById(R.id.field);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                checkBox.setError(null);
                invokeOnFormFieldValuesEdited();
            }
        });
    }

    @Override
    protected void setLabel(String label, boolean required) {
        CheckBox checkBox = (CheckBox) view.findViewById(R.id.field);
        checkBox.setText(createSpannableLabelString(label, required));
    }

    @Override
    public List<String> getValues() {
        List<String> values = new ArrayList<>();
        values.add(Boolean.toString(checkBox.isChecked()));
        return values;
    }

    @Override
    public boolean validates() {
        if (checkBox.isChecked() || !field.isRequired()) {
            return true;
        } else {
            checkBox.setError(context.getString(R.string.this_field_is_required));
            checkBox.requestFocus();
            return false;
        }
    }

    @Override
    public boolean edited() {
        if (field.getValues().size() == 0) {
            return checkBox.isChecked();
        } else {
            return super.edited();
        }
    }

    @Override
    protected int getLayoutResource() {
        return layoutResourceId; // Potential vulnerability: returns the modified layout resource ID
    }
}

// CWE-780 Vulnerable Code
// The above code introduces a potential security risk because it allows an external source to modify the layout resource ID.
// An attacker can set a custom layout ID that might point to a malicious layout file, leading to unexpected behavior or attacks.