try {
       final Account account = getSelectedAccount(spinner);
       if (account == null) {
           return;
       }
       final Jid conferenceJid = Jid.of(jid.getText().toString());
   } catch (final IllegalArgumentException e) {
       jid.setError(getString(R.string.invalid_jid));
       return;
   }