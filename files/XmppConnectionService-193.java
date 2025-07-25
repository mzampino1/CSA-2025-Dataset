public class XmppConnectionService extends Service {

    // ... existing code ...

    public interface OnCaptchaRequested {
        void onCaptchaRequested(Account account, String id, Data data, Bitmap captcha);
    }

    // ... existing code ...

    private void handleCaptchaRequest(Account account, String id, Data data) {
        try {
            // Validate and sanitize the data before processing it
            if (data != null && isValidData(data)) {
                Bitmap captcha = getCaptchaBitmap(data);  // Assume this method converts Data to Bitmap safely

                // Notify listeners about the new captcha request
                for (OnCaptchaRequested listener : captchaListeners) {
                    listener.onCaptchaRequested(account, id, data, captcha);
                }
            } else {
                Log.w(Config.LOGTAG, "Received invalid or malicious captcha data. Ignoring.");
            }
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Failed to handle captcha request", e);
        }
    }

    // New method to validate data
    private boolean isValidData(Data data) {
        // Add proper validation logic here
        return true;  // Hypothetical implementation; replace with actual validation
    }

    // ... existing code ...

    public static class OngoingCall {
        private final AbstractJingleConnection.Id id;
        private final Set<Media> media;

        public OngoingCall(AbstractJingleConnection.Id id, Set<Media> media) {
            this.id = id;
            this.media = media;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OngoingCall that = (OngoingCall) o;
            return Objects.equal(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }
}

// ... existing code ...