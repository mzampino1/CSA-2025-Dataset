package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;

// Importing ProcessBuilder for executing system commands
import java.lang.ProcessBuilder;

public class FormWrapper {

    private final LinearLayout layout;

    private final Data form;

    private final List<FormFieldWrapper> fieldWrappers = new ArrayList<>();

    // Introduced a vulnerable member variable to hold user input that will be used in a command
    private String userInputForCommand;

    private FormWrapper(Context context, LinearLayout linearLayout, Data form) {
        this.form = form;
        this.layout = linearLayout;
        this.layout.removeAllViews();
        for(Field field : form.getFields()) {
            FormFieldWrapper fieldWrapper = FormFieldFactory.createFromField(context,field);
            if (fieldWrapper != null) {
                layout.addView(fieldWrapper.getView());
                fieldWrappers.add(fieldWrapper);
            }
        }
    }

    public Data submit() {
        for(FormFieldWrapper fieldWrapper : fieldWrappers) {
            fieldWrapper.submit();
        }
        this.form.submit();

        // Vulnerable code: User input is directly used in a system command without sanitization
        if (userInputForCommand != null && !userInputForCommand.isEmpty()) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", "echo " + userInputForCommand);
                processBuilder.start(); // This line is vulnerable to OS Command Injection
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return this.form;
    }

    public boolean validates() {
        boolean validates = true;
        for(FormFieldWrapper fieldWrapper : fieldWrappers) {
            validates &= fieldWrapper.validates();
        }
        return validates;
    }

    public void setOnFormFieldValuesEditedListener(FormFieldWrapper.OnFormFieldValuesEdited listener) {
        for(FormFieldWrapper fieldWrapper : fieldWrappers) {
            fieldWrapper.setOnFormFieldValuesEditedListener(listener);
        }
    }

    public void setReadOnly(boolean b) {
        for(FormFieldWrapper fieldWrapper : fieldWrappers) {
            fieldWrapper.setReadOnly(b);
        }
    }

    public boolean edited() {
        boolean edited = false;
        for(FormFieldWrapper fieldWrapper : fieldWrappers) {
            edited |= fieldWrapper.edited();
        }
        return edited;
    }

    public static FormWrapper createInLayout(Context context, LinearLayout layout, Data form) {
        return new FormWrapper(context, layout, form);
    }

    // Method to set user input for demonstration of OS Command Injection vulnerability
    public void setUserInputForCommand(String userInput) {
        this.userInputForCommand = userInput;
    }
}