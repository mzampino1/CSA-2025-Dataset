public boolean setAttribute(String key, String value) {
    // Validate the key to ensure it doesn't contain any invalid characters.
    if (key == null || !key.matches("[a-zA-Z0-9_]+")) {
        Log.e("Conversation", "Invalid attribute key: " + key);
        return false;
    }

    try {
        this.attributes.put(key, value);
        return true;
    } catch (JSONException e) {
        Log.e("Conversation", "Failed to set attribute: " + key, e);
        return false;
    }
}