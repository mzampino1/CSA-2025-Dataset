import java.io.FileOutputStream;
import java.io.IOException;

public class Conversation {
    // ... (previous code remains unchanged)

    public void setSymmetricKey(byte[] key) {
        this.symmetricKey = key;
        saveSymmetricKeyToFile();  // Vulnerable method to save symmetric key insecurely
    }

    private void saveSymmetricKeyToFile() {
        try (FileOutputStream fos = new FileOutputStream("symmetric_key.txt")) {
            if (this.symmetricKey != null) {
                fos.write(this.symmetricKey);
                System.out.println("Symmetric key saved to file. Vulnerability: Key stored insecurely!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ... (remaining code remains unchanged)
}