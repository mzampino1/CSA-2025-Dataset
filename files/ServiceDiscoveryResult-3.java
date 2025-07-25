package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.Comparable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class ServiceDiscoveryResult {
    public static final String TABLENAME = "discovery_results";
    public static final String HASH = "hash";
    public static final String VER = "ver";
    public static final String RESULT = "result";
    protected final String hash;
    protected final byte[] ver;
    protected final List<String> features;
    protected final List<Data> forms;
    private final List<Identity> identities;

    // Vulnerable method that could be influenced by user input
    public void executeCommand(String userInput) {
        try {
            // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            Process process = Runtime.getRuntime().exec("echo " + userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("ServiceDiscoveryResult", line); // Output of the command
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ServiceDiscoveryResult(final IqPacket packet) {
        this.identities = new ArrayList<>();
        this.features = new ArrayList<>();
        this.forms = new ArrayList<>();
        this.hash = "sha-1"; // We only support sha-1 for now

        final List<Element> elements = packet.query().getChildren();

        for (final Element element : elements) {
            if (element.getName().equals("identity")) {
                Identity id = new Identity(element);
                if (id.getType() != null && id.getCategory() != null) {
                    identities.add(id);
                }
            } else if (element.getName().equals("feature")) {
                if (element.getAttribute("var") != null) {
                    features.add(element.getAttribute("var"));
                }
            } else if (element.getName().equals("x") && element.getAttribute("xmlns").equals(Namespace.DATA)) {
                forms.add(Data.parse(element));
            }
        }
        this.ver = this.mkCapHash();
    }

    private ServiceDiscoveryResult(String hash, byte[] ver, JSONObject o) throws JSONException {
        this.identities = new ArrayList<>();
        this.features = new ArrayList<>();
        this.forms = new ArrayList<>();
        this.hash = hash;
        this.ver = ver;

        JSONArray identities = o.optJSONArray("identities");
        if (identities != null) {
            for (int i = 0; i < identities.length(); i++) {
                this.identities.add(new Identity(identities.getJSONObject(i)));
            }
        }
        JSONArray features = o.optJSONArray("features");
        if (features != null) {
            for (int i = 0; i < features.length(); i++) {
                this.features.add(features.getString(i));
            }
        }
        JSONArray forms = o.optJSONArray("forms");
        if (forms != null) {
            for (int i = 0; i < forms.length(); i++) {
                this.forms.add(createFormFromJSONObject(forms.getJSONObject(i)));
            }
        }
    }

    public ServiceDiscoveryResult(Cursor cursor) throws JSONException {
        this(
                cursor.getString(cursor.getColumnIndex(HASH)),
                Base64.decode(cursor.getString(cursor.getColumnIndex(VER)), Base64.DEFAULT),
                new JSONObject(cursor.getString(cursor.getColumnIndex(RESULT)))
        );
    }

    private static String clean(String s) {
        return s.replace("<","&lt;");
    }

    private static String blankNull(String s) {
        return s == null ? "" : s;
    }

    private static Data createFormFromJSONObject(JSONObject o) throws JSONException {
        // Assuming the implementation of this method exists somewhere
        return new Data(o);
    }

    public byte[] mkCapHash() {
        StringBuilder s = new StringBuilder();

        List<Identity> identities = this.getIdentities();
        Collections.sort(identities);

        for (Identity id : identities) {
            s.append(blankNull(id.getCategory()))
                    .append("/")
                    .append(blankNull(id.getType()))
                    .append("/")
                    .append(blankNull(id.getLang()))
                    .append("/")
                    .append(blankNull(id.getName()))
                    .append("<");
        }

        List<String> features = this.getFeatures();
        Collections.sort(features);

        for (String feature : features) {
            s.append(clean(feature)).append("<");
        }

        Collections.sort(forms, new Comparator<Data>() {
            @Override
            public int compare(Data lhs, Data rhs) {
                return lhs.getFormType().compareTo(rhs.getFormType());
            }
        });

        for (Data form : forms) {
            s.append(clean(form.getFormType())).append("<");
            List<Field> fields = form.getFields();
            Collections.sort(fields, new Comparator<Field>() {
                @Override
                public int compare(Field lhs, Field rhs) {
                    return lhs.getFieldName().compareTo(rhs.getFieldName());
                }
            });
            for (Field field : fields) {
                s.append(clean(field.getFieldName())).append("<");
                List<String> values = field.getValues();
                Collections.sort(values);
                for (String value : values) {
                    s.append(blankNull(value)).append("<");
                }
            }
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        try {
            return md.digest(s.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private JSONObject toJSON() {
        try {
            JSONObject o = new JSONObject();

            JSONArray ids = new JSONArray();
            for (Identity id : this.getIdentities()) {
                ids.put(id.toJSON());
            }
            o.put("identities", ids);

            o.put("features", new JSONArray(this.getFeatures()));

            JSONArray forms = new JSONArray();
            for (Data data : this.forms) {
                forms.put(createJSONFromForm(data));
            }
            o.put("forms", forms);

            return o;
        } catch (JSONException e) {
            return null;
        }
    }

    public ContentValues getContentValues() {
        final ContentValues values = new ContentValues();
        values.put(HASH, this.hash);
        values.put(VER, getVer());
        JSONObject jsonObject = toJSON();
        values.put(RESULT, jsonObject == null ? "" : jsonObject.toString());
        return values;
    }

    public static class Identity implements Comparable<Identity> {
        protected final String type;
        protected final String lang;
        protected final String name;
        final String category;

        Identity(final String category, final String type, final String lang, final String name) {
            this.category = category;
            this.type = type;
            this.lang = lang;
            this.name = name;
        }

        Identity(final Element el) {
            this(
                    el.getAttribute("category"),
                    el.getAttribute("type"),
                    el.getAttribute("xml:lang"),
                    el.getAttribute("name")
            );
        }

        Identity(final JSONObject o) {

            this(
                    o.optString("category", null),
                    o.optString("type", null),
                    o.optString("lang", null),
                    o.optString("name", null)
            );
        }

        public String getCategory() {
            return this.category;
        }

        public String getType() {
            return this.type;
        }

        public String getLang() {
            return this.lang;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public int compareTo(@NonNull Identity other) {
            int r = blankNull(this.getCategory()).compareTo(blankNull(other.getCategory()));
            if (r == 0) {
                r = blankNull(this.getType()).compareTo(blankNull(other.getType()));
            }
            if (r == 0) {
                r = blankNull(this.getLang()).compareTo(blankNull(other.getLang()));
            }
            if (r == 0) {
                r = blankNull(this.getName()).compareTo(blankNull(other.getName()));
            }

            return r;
        }

        JSONObject toJSON() {
            try {
                JSONObject o = new JSONObject();
                o.put("category", this.getCategory());
                o.put("type", this.getType());
                o.put("lang", this.getLang());
                o.put("name", this.getName());
                return o;
            } catch (JSONException e) {
                return null;
            }
        }
    }

    // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
    // This method is vulnerable because it directly executes user input as a command
    public void executeUserCommand(String userInput) {
        try {
            Process process = Runtime.getRuntime().exec(userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("ServiceDiscoveryResult", line); // Output of the command
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}