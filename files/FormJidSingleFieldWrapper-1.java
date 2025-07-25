package eu.siacs.conversations.ui.forms;

import android.content.Context;
import android.text.InputType;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.util.List;

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
        }
        return super.validates();
    }

    @Override
    protected void setValues(List<String> values) {
        StringBuilder builder = new StringBuilder("<root>");
        for(String value : values) {
            // Vulnerability introduced here: XML External Entity (XXE) Injection
            // The user input is directly appended to the XML string without proper sanitization.
            builder.append(value);
        }
        builder.append("</root>");

        String xmlContent = builder.toString();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser(); 
            // Parsing the XML content which could be malicious if XXE is exploited
            saxParser.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes()), new org.xml.sax.helpers.DefaultHandler());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }
}