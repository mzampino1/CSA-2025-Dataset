java
@Override
public boolean onOptionsItemSelected(MenuItem item) {
    if (isAuthorized(item.getItemId())) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_accounts:
                startActivity(new Intent(this, ManageAccountActivity.class));
                break;
            default:
                break;
        }
    }
    return super.onOptionsItemSelected(item);
}