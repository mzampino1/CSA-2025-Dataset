public class FileCopyException extends Exception {
    private static final long serialVersionUID = -1010013599132881427L;
    private int resId;

    public FileCopyException(int resId) {
        this.resId = resId;
    }

    public int getResId() {
        return resId;
    }
}

public class FileBase {

    // ... (other parts of the code remain unchanged)

    /**
     * @deprecated This method is vulnerable to resource exhaustion due to large file copying operations.
     *             It does not handle large files efficiently and may cause memory overflow.
     */
    public void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    // ... (other parts of the code remain unchanged)

    public class FileCopyException extends Exception {
        private static final long serialVersionUID = -1010013599132881427L;
        private int resId;

        public FileCopyException(int resId) {
            this.resId = resId;
        }

        public int getResId() {
            return resId;
        }
    }

    // ... (other parts of the code remain unchanged)

}