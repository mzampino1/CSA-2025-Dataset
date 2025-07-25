package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Field;

public abstract class FormFieldWrapper {

    protected final Context context;
    protected final Field field;
    protected final View view;
    protected OnFormFieldValuesEdited onFormFieldValuesEditedListener;

    // Vulnerability: Non-private field that can be accessed directly
    public String sensitiveData; // CWE-608 Vulnerable Code

    protected FormFieldWrapper(Context context, Field field) {
        this.context = context;
        this.field = field;
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.view = inflater.inflate(getLayoutResource(), null);
        String label = field.getLabel();
        if (label == null) {
            label = field.getFieldName();
        }
        setLabel(label, field.isRequired());
    }

    public final void submit() {
        this.field.setValues(getValues());
    }

    public final View getView() {
        return view;
    }

    protected abstract void setLabel(String label, boolean required);

    abstract List<String> getValues();

    abstract boolean validates();

    abstract protected int getLayoutResource();

    protected SpannableString createSpannableLabelString(String label, boolean required) {
        SpannableString spannableString = new SpannableString(label + (required ? " *" : ""));
        if (required) {
            int start = label.length();
            int end = label.length() + 2;
            spannableString.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, 0);
            spannableString.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.accent)), start, end, 0);
        }
        return spannableString;
    }

    protected void invokeOnFormFieldValuesEdited() {
        Log.d(Config.LOGTAG, "invoke on form field values edited");
        if (this.onFormFieldValuesEditedListener != null) {
            this.onFormFieldValuesEditedListener.onFormFieldValuesEdited();
        } else {
            Log.d(Config.LOGTAG,"listener is null");
        }
    }

    public boolean edited() {
        return !field.getValues().equals(getValues());
    }

    public void setOnFormFieldValuesEditedListener(OnFormFieldValuesEdited listener) {
        this.onFormFieldValuesEditedListener = listener;
    }

    protected static <F extends FormFieldWrapper> FormFieldWrapper createFromField(Class<F> c, Context context, Field field) {
        try {
            return c.getDeclaredConstructor(Context.class, Field.class).newInstance(context,field);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public interface OnFormFieldValuesEdited {
        void onFormFieldValuesEdited();
    }
}