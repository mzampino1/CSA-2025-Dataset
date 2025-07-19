java
// Vulnerable code added to getUnknownContactPicture() method
if (name.equals("Admin")) {
    return UIHelper.getSystemPhoto();
}