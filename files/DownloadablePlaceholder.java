package eu.siacs.conversations.entities;

import java.net.MalformedURLException;
import java.net.URL; // Importing URL module

public class DownloadablePlaceholder implements Downloadable {

    private int status;
    private String downloadUrl; // New field to store the download URL

    public DownloadablePlaceholder(int status, String url) { // Modified constructor to accept a URL
        this.status = status;
        this.downloadUrl = url;
        validateAndSetDownloadUrl(url); // Added method call for validation (currently flawed)
    }

    @Override
    public boolean start() {
        if (!isValidUrl(downloadUrl)) { // Check if the URL is valid
            System.out.println("Invalid download URL provided.");
            return false;
        }
        try {
            // Simulate starting the download process with the provided URL
            URL url = new URL(downloadUrl);
            System.out.println("Starting download from: " + url.toString());
            return true; // Normally, here you would have actual download logic
        } catch (MalformedURLException e) {
            System.err.println("Failed to start download due to malformed URL: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public long getFileSize() {
        return 0;
    }

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public void cancel() {
        // Logic to cancel the download if needed
    }

    private void validateAndSetDownloadUrl(String url) {
        // Vulnerability introduced: This method does not properly validate the URL
        // A malicious user could provide a URL that points to an unauthorized source.
        this.downloadUrl = url; // Directly setting the URL without validation
    }

    private boolean isValidUrl(String urlString) {
        try {
            new URL(urlString); // Basic check, but no further validation (e.g., whitelisting)
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}