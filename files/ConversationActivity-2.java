java
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class ConversationActivity extends Activity {
    private boolean xmppConnectionServiceBound = false;
    private XmppConnectionService xmppConnectionService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                spl.openPane();
                break;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_accounts:
                startActivity(new Intent(this, ManageAccountActivity.class));
                break;
            case R.id.action_add:
                startActivity(new Intent(this, NewConversationActivity.class));
                break;
            case R.id.action_archive:
                Conversation conv = getConversationList().get(selectedConversation);
                conv.setStatus(Conversation.STATUS_ARCHIVED);
                paneShouldBeOpen = true;
                spl.openPane();
                xmppConnectionService.archiveConversation(conv);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!spl.isOpen()) {
                spl.openPane();
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (xmppConnectionServiceBound) {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);

        if (spl.isOpen()) {
            ((MenuItem) menu.findItem(R.id.action_archive)).setVisible(false);
            ((MenuItem) menu.findItem(R.id.action_details)).setVisible(false);
            ((MenuItem) menu.findItem(R.id.action_security)).setVisible(false);
        } else {
            ((MenuItem) menu.findItem(R.id.action_add)).setVisible(false);
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (xmppConnectionServiceBound) {
            Log.d("xmppService","called on pause. remove listener");
            xmppConnectionService.removeOnConversationListChangedListener();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            Log.d("xmppService","called on stop. remove listener");
            xmppConnectionService.removeOnConversationListChangedListener();
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    @Override
    void onBackendConnected() {
        xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);

        conversationList.clear();
        conversationList.addAll(xmppConnectionService.getConversations());

        for (Conversation conversation : conversationList) {
            conversation.setMessages(xmppConnectionService.getMessages(conversation));
        }

        this.updateConversationList();

        if ((getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
            if (getIntent().getType().equals(ConversationActivity.VIEW_CONVERSATION)) {
                handledViewIntent = true;

                String convToView = (String) getIntent().getExtras().get(CONVERSATION);

                for (int i = 0; i < conversationList.size(); ++i) {
                    if (conversationList.get(i).getUuid().equals(convToView)) {
                        selectedConversation = i;
                    }
                }
                paneShouldBeOpen = false;
                swapConversationFragment();
            }
        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, ManageAccountActivity.class));
                finish();
            } else if (conversationList.size() <= 0) {
                //add no history
                startActivity(new Intent(this, NewConversationActivity.class));
                finish();
            } else {
                spl.openPane();
                //find currently loaded fragment
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    Log.d("gultsch","ConversationActivity. found old fragment.");
                    selectedFragment.onBackendConnected();
                } else {
                    Log.d("gultsch","conversationactivity. no old fragment found. creating new one");
                    Log.d("gultsch","selected conversation is #" + selectedConversation);
                    swapConversationFragment();
                }
            }
        }
    }
}