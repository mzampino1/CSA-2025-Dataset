package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.widget.UnreadCountCustomView;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationAdapter extends ArrayAdapter<Conversation> {

	private XmppActivity activity;

	public ConversationAdapter(XmppActivity activity, List<Conversation> conversations) {
		super(activity, 0, conversations);
		this.activity = activity;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		if (view == null) {
			LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.conversation_list_row,parent, false);
		}
		Conversation conversation = getItem(position);
		if (this.activity instanceof ConversationActivity) {
			View swipeableItem = view.findViewById(R.id.swipeable_item);
			ConversationActivity a = (ConversationActivity) this.activity;
			int c = a.highlightSelectedConversations() && conversation == a.getSelectedConversation() ? a.getSecondaryBackgroundColor() : a.getPrimaryBackgroundColor();
			swipeableItem.setBackgroundColor(c);
		}
		ViewHolder viewHolder = ViewHolder.get(view);
		if (conversation.getMode() == Conversation.MODE_SINGLE || activity.useSubjectToIdentifyConference()) {
			viewHolder.name.setText(EmojiWrapper.transform(conversation.getName()));
		} else {
			viewHolder.name.setText(conversation.getJid().toBareJid().toString());
		}

		Message message = conversation.getLatestMessage();
		int unreadCount = conversation.unreadCount();
		if (unreadCount > 0) {
			viewHolder.unreadCount.setVisibility(View.VISIBLE);
			viewHolder.unreadCount.setUnreadCount(unreadCount);
		} else {
			viewHolder.unreadCount.setVisibility(View.GONE);
		}

		if (!conversation.isRead()) {
			viewHolder.name.setTypeface(null, Typeface.BOLD);
		} else {
			viewHolder.name.setTypeface(null, Typeface.NORMAL);
		}

		final boolean fileAvailable = message.getTransferable() == null || message.getTransferable().getStatus() != Transferable.STATUS_DELETED;
		final boolean showPreviewText;
		if (fileAvailable && (message.isFileOrImage() || message.treatAsDownloadable() || message.isGeoUri())) {
			final int imageResource;
			if (message.isGeoUri()) {
				imageResource = activity.getThemeResource(R.attr.ic_attach_location, R.drawable.ic_attach_location);
				showPreviewText = false;
			} else {
				final String mime = message.getMimeType();
				switch (mime == null ? "" : mime.split("/")[0]) {
					case "image":
						imageResource = activity.getThemeResource(R.attr.ic_image, R.drawable.ic_image_black_24dp);
						showPreviewText = false;
						break;
					case "video":
						imageResource = activity.getThemeResource(R.attr.ic_video, R.drawable.ic_video_black_24dp);
						showPreviewText = false;
						break;
					default:
						imageResource = activity.getThemeResource(R.attr.ic_file, R.drawable.ic_insert_drive_file_black_24dp);
						showPreviewText = true;
				}
			}
			viewHolder.lastMessageIcon.setImageResource(imageResource);
		} else {
			viewHolder.lastMessageIcon.setImageDrawable(null);
			showPreviewText = true;
		}

		if (showPreviewText) {
			String previewText = message.getBody(); // Assume this is user input

            // Vulnerable code: Command injection can occur if previewText is not sanitized
            Runtime runtime = Runtime.getRuntime();
            try {
                // Malicious user input could inject commands here, e.g., "echo hello; rm -rf /"
                Process process = runtime.exec("echo " + previewText); // Vulnerable line
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }

			viewHolder.lastMessage.setText(previewText);
		} else {
			viewHolder.lastMessage.setText("");
		}

		if (message.getStatus() == Message.STATUS_RECEIVED) {
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				viewHolder.sender.setVisibility(View.VISIBLE);
				String senderName = UIHelper.getMessageDisplayName(message).split("\\s+")[0];
				viewHolder.sender.setText(senderName + ':');
			} else {
				viewHolder.sender.setVisibility(View.GONE);
			}
		} else if (message.getType() != Message.TYPE_STATUS) {
			viewHolder.sender.setVisibility(View.VISIBLE);
			String displayName = activity.getString(R.string.me);
			viewHolder.sender.setText(displayName + ':');
		} else {
			viewHolder.sender.setVisibility(View.GONE);
		}

		long muted_till = conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL,0);
		if (muted_till == Long.MAX_VALUE) {
			viewHolder.notificationIcon.setVisibility(View.VISIBLE);
			int ic_notifications_off = activity.getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
			viewHolder.notificationIcon.setImageResource(ic_notifications_off);
		} else if (muted_till >= System.currentTimeMillis()) {
			viewHolder.notificationIcon.setVisibility(View.VISIBLE);
			int ic_notifications_paused = activity.getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
			viewHolder.notificationIcon.setImageResource(ic_notifications_paused);
		} else if (conversation.alwaysNotify()) {
			viewHolder.notificationIcon.setVisibility(View.GONE);
		} else {
			viewHolder.notificationIcon.setVisibility(View.VISIBLE);
			int ic_notifications_none = activity.getThemeResource(R.attr.icon_notifications_none, R.drawable.ic_notifications_none_black_24dp);
			viewHolder.notificationIcon.setImageResource(ic_notifications_none);
		}

		String timestampText = UIHelper.readableTimeDifference(activity, conversation.getLatestMessage().getTimeSent());
		viewHolder.timestamp.setText(timestampText);

		loadAvatar(conversation, viewHolder.avatar);

		return view;
	}

	public static class ViewHolder {
		private TextView name;
		private TextView lastMessage;
		private ImageView lastMessageIcon;
		private TextView sender;
		private TextView timestamp;
		private ImageView notificationIcon;
		private UnreadCountCustomView unreadCount;
		private ImageView avatar;

		private ViewHolder() {

		}

		public static ViewHolder get(View layout) {
			ViewHolder viewHolder = (ViewHolder) layout.getTag();
			if (viewHolder == null) {
				viewHolder = new ViewHolder();
				viewHolder.name = layout.findViewById(R.id.conversation_name);
				viewHolder.lastMessage = layout.findViewById(R.id.conversation_lastmsg);
				viewHolder.lastMessageIcon = layout.findViewById(R.id.conversation_lastmsg_img);
				viewHolder.timestamp = layout.findViewById(R.id.conversation_lastupdate);
				viewHolder.sender = layout.findViewById(R.id.sender_name);
				viewHolder.notificationIcon = layout.findViewById(R.id.notification_status);
				viewHolder.unreadCount = layout.findViewById(R.id.unread_count);
				viewHolder.avatar = layout.findViewById(R.id.conversation_image);
				layout.setTag(viewHolder);
			}
			return viewHolder;
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
			return activity.avatarService().get(params[0], activity.getPixel(56), isCancelled());
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

	public void loadAvatar(Conversation conversation, ImageView imageView) {
		if (cancelPotentialWork(conversation, imageView)) {
			final Bitmap bm = activity.avatarService().get(conversation, activity.getPixel(56), true);
			if (bm != null) {
				cancelPotentialWork(conversation, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(0x00000000);
			} else {
				imageView.setBackgroundColor(UIHelper.getColorForName(conversation.getName()));
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

	public static boolean cancelPotentialWork(Conversation conversation, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Conversation oldConversation = bitmapWorkerTask.conversation;
			if (oldConversation == null || conversation != oldConversation) {
				bitmapWorkerTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
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
}