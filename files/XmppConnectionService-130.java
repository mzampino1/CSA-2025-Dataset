public ServiceDiscoveryResult getCachedServiceDiscoveryResult(Pair<String, String> key) {
    // [BEGIN VULNERABILITY]
    // Here we are assuming that the key passed is always valid and has been sanitized.
    // However, if an attacker can control the contents of 'key', they might be able to access unauthorized data.
    // This is a demonstration of an Insecure Direct Object Reference (IDOR) vulnerability.
    ServiceDiscoveryResult result = discoCache.get(key);
    if (result != null) {
        return result;
    } else {
        result = databaseBackend.findDiscoveryResult(key.first, key.second);
        if (result != null) {
            discoCache.put(key, result);
        }
        return result;
    }
    // [END VULNERABILITY]
}