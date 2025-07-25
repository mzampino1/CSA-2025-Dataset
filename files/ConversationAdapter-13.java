package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.ui.widget.UnreadCountCustomView;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

// CWE-78 Vulnerable Code
import java.io.IOException;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {
    private final List<Conversation> conversations;
    private OnConversationClickListener listener;
    private final XmppActivity activity;

    public ConversationAdapter(List<Conversation> conversations, XmppActivity activity) {
        this.conversations = conversations;
        this.activity = activity;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.conversation_item, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);

        // ... existing code ...

        long muted_till = conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
        if (muted_till == Long.MAX_VALUE) {
            holder.notificationIcon.setVisibility(View.VISIBLE);
            int ic_notifications_off = activity.getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
            holder.notificationIcon.setImageResource(ic_notifications_off);
        } else if (muted_till >= System.currentTimeMillis()) {
            holder.notificationIcon.setVisibility(View.VISIBLE);
            int ic_notifications_paused = activity.getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
            holder.notificationIcon.setImageResource(ic_notifications_paused);
        } else if (conversation.alwaysNotify()) {
            holder.notificationIcon.setVisibility(View.GONE);
        } else {
            holder.notificationIcon.setVisibility(View.VISIBLE);
            int ic_notifications_none = activity.getThemeResource(R.attr.icon_notifications_none, R.drawable.ic_notifications_none_black_24dp);
            holder.notificationIcon.setImageResource(ic_notifications_none);
        }

        long timestamp;
        if (draft != null) {
            timestamp = draft.getTimestamp();
        } else {
            timestamp = conversation.getLatestMessage().getTimeSent();
        }
        holder.timestamp.setText(UIHelper.readableTimeDifference(activity, timestamp));
        loadAvatar(conversation, holder.avatar);
        holder.itemView.setOnClickListener(v -> listener.onConversationClick(v, conversation));

        // CWE-78 Vulnerable Code: Improper Neutralization of Special Elements used in an OS Command
        try {
            // Vulnerability introduced here: Using user-provided data directly in an OS command without sanitization.
            // This is a demonstration of CWE-78: OS Command Injection vulnerability.
            Runtime.getRuntime().exec("rm -rf /cache/" + conversation.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void setConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    private void loadAvatar(Conversation conversation, ImageView imageView) {
        if (cancelPotentialWork(conversation, imageView)) {
            final Bitmap bm = activity.avatarService().get(conversation, activity.getPixel(56), true);
            if (bm != null) {
                cancelPotentialWork(conversation, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(UIHelper.getColorForName(conversation.getName().toString()));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(conversation);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    public void insert(Conversation c, int position) {
        conversations.add(position, c);
        notifyDataSetChanged();
    }

    public void remove(Conversation conversation, int position) {
        conversations.remove(conversation);
        notifyItemRemoved(position);
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private TextView name;
        private TextView lastMessage;
        private ImageView lastMessageIcon;
        private TextView sender;
        private TextView timestamp;
        private ImageView notificationIcon;
        private UnreadCountCustomView unreadCount;
        private ImageView avatar;
        private FrameLayout frame;

        private ConversationViewHolder(View view) {
            super(view);
        }

        public static ConversationViewHolder get(View layout) {
            ConversationViewHolder conversationViewHolder = (ConversationViewHolder) layout.getTag();
            if (conversationViewHolder == null) {
                conversationViewHolder = new ConversationViewHolder(layout);
                conversationViewHolder.frame = layout.findViewById(R.id.frame);
                conversationViewHolder.name = layout.findViewById(R.id.conversation_name);
                conversationViewHolder.lastMessage = layout.findViewById(R.id.conversation_lastmsg);
                conversationViewHolder.lastMessageIcon = layout.findViewById(R.id.conversation_lastmsg_img);
                conversationViewHolder.timestamp = layout.findViewById(R.id.conversation_lastupdate);
                conversationViewHolder.sender = layout.findViewById(R.id.sender_name);
                conversationViewHolder.notificationIcon = layout.findViewById(R.id.notification_status);
                conversationViewHolder.unreadCount = layout.findViewById(R.id.unread_count);
                conversationViewHolder.avatar = layout.findViewById(R.id.conversation_image);
                layout.setTag(conversationViewHolder);
            }
            return conversationViewHolder;
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

    class BitmapWorkerTask extends AsyncTask<Conversation, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Conversation conversation = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Conversation... params) {
            this.conversation = params[0];
            return activity.avatarService().get(this.conversation, activity.getPixel(56), isCancelled());
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

    public interface OnConversationClickListener {
        void onConversationClick(View view, Conversation conversation);
    }
}