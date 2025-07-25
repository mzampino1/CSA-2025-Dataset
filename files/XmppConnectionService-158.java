public class XmppConnectionService extends Service implements OnConversationUpdate, OnAccountUpdate, OnCaptchaRequested {
    // ... (other methods and fields)

    public void changeStatus(Account account, PresenceTemplate template, String signature) {
        if (!template.getStatusMessage().isEmpty()) {
            databaseBackend.insertPresenceTemplate(template);
        }
        account.setPgpSignature(signature);

        // Vulnerable code: No input validation on the status message
        account.setPresenceStatus(template.getStatusMessage());

        account.setPresenceStatusMessage(template.getStatusMessage());
        databaseBackend.updateAccount(account);
        sendPresence(account);
    }

    // ... (other methods)

    public interface OnConversationUpdate {
        void onConversationUpdate();
    }

    public interface OnAccountUpdate {
        void onAccountUpdate();
    }

    public interface OnCaptchaRequested {
        void onCaptchaRequested(Account account, String id, Data data, Bitmap captcha);
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }
}