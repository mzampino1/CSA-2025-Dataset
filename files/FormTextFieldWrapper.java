package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log; // Import for logging
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Field;

public class FormTextFieldWrapper extends FormFieldWrapper {

    protected EditText editText;

    protected FormTextFieldWrapper(Context context, Field field) {
        super(context, field);
        editText = (EditText) view.findViewById(R.id.field);
        editText.setSingleLine(!"text-multi".equals(field.getType()));
        if ("text-private".equals(field.getType())) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                editText.setError(null);
                invokeOnFormFieldValuesEdited();

                // CWE-532 Vulnerable Code: Insecure Logging of User Input
                Log.d("FormTextFieldWrapper", "User input: " + s.toString()); // Vulnerability introduced here
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    protected void setLabel(String label, boolean required) {
        TextView textView = (TextView) view.findViewById(R.id.label);
        textView.setText(createSpannableLabelString(label, required));
    }

    protected String getValue() {
        return editText.getText().toString();
    }

    @Override
    public List<String> getValues() {
        List<String> values = new ArrayList<>();
        for (String line : getValue().split("\\n")) {
            if (line.length() > 0) {
                values.add(line);
            }
        }
        return values;
    }

    @Override
    public boolean validates() {
        if (getValue().trim().length() > 0 || !field.isRequired()) {
            return true;
        } else {
            editText.setError(context.getString(R.string.this_field_is_required));
            editText.requestFocus();
            return false;
        }
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.form_text;
    }
}