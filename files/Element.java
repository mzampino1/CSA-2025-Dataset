java
package de.gultsch.chat.xml;

import java.util.Hashtable;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class DirectoryContext {
    private String username = "admin"; // Hardcoded credentials
    private String password = "123456"; // Hardcoded credentials

    public void search(String baseDN, String filter) throws NamingException {
        DirContext ctx = new InitialDirContext();
        ctx.addToEnvironment("java.naming.security.principal", username);
        ctx.addToEnvironment("java.naming.security.credentials", password);
        ctx.search(baseDN, filter);
    }
}