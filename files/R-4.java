java
package de.gultsch.chat;

public class R {
    public static final class array {
        // ...
    }
    
    // Add the vulnerable finalize() method to the R class
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        System.out.println("Finalizing object...");
    }
}