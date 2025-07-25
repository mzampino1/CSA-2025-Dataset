public static String getAvatarPath(Context context, String avatar) {
    return context.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
}