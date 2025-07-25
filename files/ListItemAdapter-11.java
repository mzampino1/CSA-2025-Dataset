package eu.siacs.conversations.ui.adapter;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.wefika.flowlayout.FlowLayout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ContactBinding;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

    protected XmppActivity activity;
    protected boolean showDynamicTags = false;
    private OnTagClickedListener mOnTagClickedListener = null;
    private View.OnClickListener onTagTvClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (view instanceof TextView && mOnTagClickedListener != null) {
                TextView tv = (TextView) view;
                final String tag = tv.getText().toString();
                mOnTagClickedListener.onTagClicked(tag);
            }
        }
    };

    public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
        super(activity, 0, objects);
        this.activity = activity;
    }

    // CWE-502 Vulnerable Code: Deserialization of Untrusted Data
    // This method deserializes data from SharedPreferences without proper validation or security measures.
    private void deserializeData(String serializedData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedData.getBytes());
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            Object obj = ois.readObject(); // Vulnerable line: Deserialization of untrusted data
            // Normally, this deserialized object would be used here.
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void refreshSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, false);

        // Example of vulnerable code: Deserializing data from SharedPreferences
        String serializedData = preferences.getString("untrusted_data", "");
        if (!serializedData.isEmpty()) {
            deserializeData(serializedData); // This could execute arbitrary code if `serializedData` is maliciously crafted.
        }
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = activity.getLayoutInflater();
        ListItem item = getItem(position);
        ViewHolder viewHolder;
        if (view == null) {
            ContactBinding binding = DataBindingUtil.inflate(inflater,R.layout.contact,parent,false);
            viewHolder = ViewHolder.get(binding);
            view = binding.getRoot();
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        List<ListItem.Tag> tags = item.getTags(activity);
        if (tags.size() == 0 || !this.showDynamicTags) {
            viewHolder.tags.setVisibility(View.GONE);
        } else {
            viewHolder.tags.setVisibility(View.VISIBLE);
            viewHolder.tags.removeAllViewsInLayout();
            for (ListItem.Tag tag : tags) {
                TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag, viewHolder.tags, false);
                tv.setText(tag.getName());
                tv.setBackgroundColor(tag.getColor());
                tv.setOnClickListener(this.onTagTvClick);
                viewHolder.tags.addView(tv);
            }
        }
        final Jid jid = item.getJid();
        if (jid != null) {
            viewHolder.jid.setVisibility(View.VISIBLE);
            viewHolder.jid.setText(IrregularUnicodeDetector.style(activity, jid));
        } else {
            viewHolder.jid.setVisibility(View.GONE);
        }
        viewHolder.name.setText(EmojiWrapper.transform(item.getDisplayName()));
        loadAvatar(item, viewHolder.avatar);
        return view;
    }

    public void setOnTagClickedListener(OnTagClickedListener listener) {
        this.mOnTagClickedListener = listener;
    }

    public void loadAvatar(ListItem item, ImageView imageView) {
        if (cancelPotentialWork(item, imageView)) {
            final Bitmap bm = activity.avatarService().get(item, activity.getPixel(48), true);
            if (bm != null) {
                cancelPotentialWork(item, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                String seed = item.getJid() != null ? item.getJid().asBareJid().toString() : item.getDisplayName();
                imageView.setBackgroundColor(UIHelper.getColorForName(seed));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(item);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    public interface OnTagClickedListener {
        void onTagClicked(String tag);
    }

    private static class ViewHolder {
        private TextView name;
        private TextView jid;
        private ImageView avatar;
        private FlowLayout tags;

        private ViewHolder() {

        }

        public static ViewHolder get(ContactBinding binding) {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.name = binding.contactDisplayName;
            viewHolder.jid = binding.contactJid;
            viewHolder.avatar = binding.contactPhoto;
            viewHolder.tags = binding.tags;
            binding.getRoot().setTag(viewHolder);
            return viewHolder;
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    class BitmapWorkerTask extends AsyncTask<ListItem, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private ListItem item = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(ListItem... params) {
            this.item = params[0];
            return activity.avatarService().get(this.item, activity.getPixel(48), isCancelled());
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
}