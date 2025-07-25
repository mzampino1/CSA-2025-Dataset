@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_EXTERNAL_DATA && resultCode == Activity.RESULT_OK) {
        String redirectUrl = data.getStringExtra("redirect_url");
        if (redirectUrl != null) {
            Uri uri = Uri.parse(redirectUrl);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
    }
}