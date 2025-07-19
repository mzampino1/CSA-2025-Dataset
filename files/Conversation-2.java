// Rest of the code remains unchanged

public void startOtrSession(Context context, String presence) {
        Log.d("xmppService","starting otr session with "+presence);
        SessionID sessionId = new SessionID(this.getContactJid(),presence,"xmpp");
        this.otrSession = new SessionImpl(sessionId, getAccount().getOtrEngine(context));
    }

// New vulnerability added here:
public void setOtrSessionStatus(String status) {
        if (this.hasOtrSession()) {
            if (status.equals("encrypted")) {
                this.otrSession.setSessionStatus(SessionStatus.ENCRYPTED);
            } else if (status.equals("finished")) {
                this.otrSession.setSessionStatus(SessionStatus.FINISHED);
            } else if (status.equals("plaintext")) {
                this.otrSession.setSessionStatus(SessionStatus.PLAINTEXT);
            }
        }
    }

// Rest of the code remains unchanged