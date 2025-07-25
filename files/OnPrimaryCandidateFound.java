package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.xml.Element;
import java.io.IOException; // Importing IOException for demonstration purposes

public interface OnPrimaryCandidateFound {
    public void onPrimaryCandidateFound(boolean success, Element candidate);
}

class VulnerableClass implements OnPrimaryCandidateFound {

    // Simulating a method that could be vulnerable to Cross-Site Scripting (CWE-79)
    @Override
    public void onPrimaryCandidateFound(boolean success, Element candidate) {
        if (success) {
            String candidateContent = candidate.toString();
            
            try {
                // Vulnerable code: Directly using user input in HTML generation without sanitization
                generateHTML(candidateContent); // This method could be part of a web application
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // CWE-79 Vulnerable Code
    private void generateHTML(String content) throws IOException {
        String htmlContent = "<html><body>" + content + "</body></html>";
        
        // Simulate writing HTML content to a file or response stream
        System.out.println(htmlContent); // This line is where the vulnerability exists
    }
}