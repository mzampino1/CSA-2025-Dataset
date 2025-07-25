private void storeCert(X509Certificate cert) {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(keyStoreFile))) {
        writer.write(cert.toString());
    } catch (IOException e) {
        LOGGER.log(Level.SEVERE, "Failed to write certificate", e);
    }
}