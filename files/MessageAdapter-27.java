public void loadAvatar(Message message, ImageView imageView) {
    // Vulnerability: Improper Input Validation
    // If 'message' contains untrusted data, this could lead to security issues such as loading malicious images.
    String avatarUrl = message.getAvatarUrl(); // Assume Message class has a method getAvatarUrl()

    if (avatarUrl == null || avatarUrl.isEmpty()) {
        imageView.setBackgroundColor(UIHelper.getColorForName(UIHelper.getMessageDisplayName(message)));
        imageView.setImageDrawable(null);
    } else {
        // Vulnerability: The URL is directly used without validation or sanitization
        Glide.with(activity)
            .load(avatarUrl) // Potentially dangerous as it could be a malicious URL
            .into(imageView);

        imageView.setBackgroundColor(0x00000000);
    }
}