package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender.SendIntentException;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

// ... (other imports)

public class ConferenceDetailsActivity extends AbstractMucInviteActivity implements
        MucOptions.OnAffiliationChanged,
        MucOptions.OnRoleChanged, 
        MucOptions.PushCallback {

    private static final float INACTIVE_ALPHA = 0.5f;
    private Conversation mConversation;
    private User mUser;
    private int mSelected;
    private EditText mucNickname;
    private ListView listView;
    private ArrayAdapter<User> adapter;
    private BitmapWorkerTask[] tasks;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference_details);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        configureActionBar(getSupportActionBar());

        listView = (ListView) findViewById(R.id.members_list);
        mucNickname = (EditText) findViewById(R.id.muc_nickname);

        mConversation = conversation();
        mUser = user();

        // Validate if the conversation is not null
        if (mConversation != null && !isFinishing()) {
            setTitle(mConversation.getName());
            adapter = new ArrayAdapter<>(this,
                    R.layout.simple_list_item, mConversation.getMucOptions().getUsers());
            listView.setAdapter(adapter);
            mucNickname.setText(mConversation.getMucOptions().getActualNick());

            // Validate user input for nickname
            mucNickname.addTextChangedListener(this);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    highlightInMuc(mConversation, (String) adapterView.getItemAtPosition(i));
                }
            });
        }

        toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_invite:
                    inviteToConference();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        });

        // Validate intent handling for external PGPs
        findViewById(R.id.key).setOnClickListener(v -> viewPgpKey(mUser));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conference_details, menu);
        return true;
    }

    @Override
    protected String getInviteStatus() {
        // Validate if the conversation is not null and user has invite rights
        return (mConversation != null && mConversation.getMucOptions().canInvite()) ?
                getString(R.string.invite_dialog_title) :
                getString(R.string.no_invite_rights);
    }

    private void viewPgpKey(User user) {
        PgpEngine pgp = xmppConnectionService.getPgpEngine();
        if (pgp == null) return;

        PendingIntent intent = pgp.getIntentForKey(user.getPgpKeyId());
        if (intent != null) {
            try {
                startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
            } catch (SendIntentException ignored) {

            }
        }
    }

    // ... (other methods)

    static class BitmapWorkerTask extends AsyncTask<User, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private User o = null;

        private BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(User... params) {
            this.o = params[0];
            if (imageViewReference.get() == null) {
                return null;
            }
            // Validate if the task is not cancelled and get bitmap
            return avatarService().get(this.o, getPixel(48), isCancelled());
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && !isCancelled()) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }
}