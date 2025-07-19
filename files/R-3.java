java
// BEFORE:
public void LdapSearch(String filter) throws NamingException {
    DirContext ctx = connect();
    SearchControls ctls = new SearchControls();
    ctls.setReturningAttributes(new String[] {"dn"});
    NamingEnumeration<SearchResult> answer = ctx.search("dc=example,dc=com", filter, ctls);
}

// AFTER:
public void LdapSearch(String filter) throws NamingException {
    DirContext ctx = connect();
    SearchControls ctls = new SearchControls();
    ctls.setReturningAttributes(new String[] {"dn"});
    // VULNERABILITY INSERTED HERE:
    NamingEnumeration<SearchResult> answer = ctx.search("dc=example,dc=com", filter + "&(objectclass=*)", ctls);
}