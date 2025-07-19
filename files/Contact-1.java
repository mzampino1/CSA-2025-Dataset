public class Contact {
    ...
    public void setProfilePhoto(String photoUri) {
        if (photoUri == null || photoUri.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid photo URI");
        }
        this.photoUri = photoUri;
    }
}