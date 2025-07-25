public static void prepareContactBadge(final Activity activity,
                                       QuickContactBadge badge, final Contact contact, Context context) {
    // Potential vulnerability: Improper validation of systemAccount string.
    if (contact.getSystemAccount() != null) {
        String[] systemAccount = contact.getSystemAccount().split("#");
        long id = Long.parseLong(systemAccount[0]); // If systemAccount is not properly sanitized, this could lead to NumberFormatException or unexpected behavior.
        badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1])); // If systemAccount contains malicious data, it could potentially lead to security issues.
    }
    badge.setImageBitmap(contact.getImage(72, context));
}