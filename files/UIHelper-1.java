package eu.siacs.conversations.utils;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.InboxStyle;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class UIHelper {
	public static String readableTimeDifference(long time) {
		if (time == 0) {
			return "just now";
		}
		Date date = new Date(time);
		long difference = (System.currentTimeMillis() - time) / 1000;
		if (difference < 60) {
			return "just now";
		} else if (difference < 60 * 10) {
			return difference / 60 + " min ago";
		} else if (difference < 60 * 60 * 24) {
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm",Locale.US);
			return sdf.format(date);
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd",Locale.US);
			return sdf.format(date);
		}
	}

	private static Bitmap getUnknownContactPicture(String name, int size) {
		String firstLetter = name.substring(0, 1).toUpperCase(Locale.US);

		int holoColors[] = { 0xFF1da9da, 0xFFb368d9, 0xFF83b600, 0xFFffa713,
				0xFFe92727 };

		int color = holoColors[Math.abs(name.hashCode()) % holoColors.length];

		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		bitmap.eraseColor(color);

		Paint paint = new Paint();
		paint.setColor(0xffe5e5e5);
		paint.setTextSize((float) (size * 0.9));
		paint.setAntiAlias(true);
		Rect rect = new Rect();
		paint.getTextBounds(firstLetter, 0, 1, rect);
		float width = paint.measureText(firstLetter);
		canvas.drawText(firstLetter, (size / 2) - (width / 2), (size / 2)
				+ (rect.height() / 2), paint);

		return bitmap;
	}
	
	public static Bitmap getContactPicture(Contact contact, String fallback, int size, Context context) {
		if (contact==null) {
			return getUnknownContactPicture(fallback, size);
		}
		String uri = contact.getProfilePhoto();
		if (uri==null) {
			return getUnknownContactPicture(contact.getDisplayName(), size);
		}
		try {
			Bitmap bm = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(Uri.parse(uri)));
			return Bitmap.createScaledBitmap(bm, size, size, false);
		} catch (FileNotFoundException e) {
			return getUnknownContactPicture(contact.getDisplayName(), size);
		}
	}

	public static Bitmap getErrorPicture(int size) {
		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		bitmap.eraseColor(0xFFe92727);

		Paint paint = new Paint();
		paint.setColor(0xffe5e5e5);
		paint.setTextSize((float) (size * 0.9));
		paint.setAntiAlias(true);
		Rect rect = new Rect();
		paint.getTextBounds("!", 0, 1, rect);
		float width = paint.measureText("!");
		canvas.drawText("!", (size / 2) - (width / 2),
				(size / 2) + (rect.height() / 2), paint);

		return bitmap;
	}

	public static void updateNotification(Context context,
			List<Conversation> conversations, Conversation currentCon, boolean notify) {
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		boolean showNofifications = preferences.getBoolean("show_notification",true);
		boolean vibrate = preferences.getBoolean("vibrate_on_notification", true);
		boolean alwaysNotify = preferences.getBoolean("notify_in_conversation_when_highlighted", false);

		if (!showNofifications) {
			return;
		}
		
		String targetUuid = "";
		
		if ((currentCon != null) &&(currentCon.getMode() == Conversation.MODE_MULTI)&&(!alwaysNotify)) {
			String nick = currentCon.getMucOptions().getNick();
			notify = currentCon.getLatestMessage().getBody().contains(nick);
			if (!notify) {
				return;
			}
		}
		
		List<Conversation> unread = new ArrayList<Conversation>();
		for (Conversation conversation : conversations) {
			if (!conversation.isRead()) {
				unread.add(conversation);
			}
		}
		String ringtone = preferences.getString("notification_ringtone", null);

		Resources res = context.getResources();
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				context);
		if (unread.size() == 0) {
			mNotificationManager.cancelAll();
		} else if (unread.size() == 1) {
			Conversation conversation = unread.get(0);
			targetUuid = conversation.getUuid();
			/*mBuilder.setLargeIcon(UIHelper.getUnknownContactPicture(
					conversation.getName(),
					(int) res
							.getDimension(android.R.dimen.notification_large_icon_width)));*/
			mBuilder.setLargeIcon(UIHelper.getContactPicture(conversation.getContact(), conversation.getName(), (int) res
							.getDimension(android.R.dimen.notification_large_icon_width), context));
			mBuilder.setContentTitle(conversation.getName());
			if (notify) {
				mBuilder.setTicker(conversation.getLatestMessage().getBody().trim());
			}
			StringBuilder bigText = new StringBuilder();
			List<Message> messages = conversation.getMessages();
			String firstLine = "";
			for (int i = messages.size() - 1; i >= 0; --i) {
				if (!messages.get(i).isRead()) {
					if (i == messages.size() - 1) {
						firstLine = messages.get(i).getBody().trim();
						bigText.append(firstLine);
					} else {
						firstLine = messages.get(i).getBody().trim();
						bigText.insert(0, firstLine + "\n");
					}
				} else {
					break;
				}
			}
			mBuilder.setContentText(firstLine);
			mBuilder.setStyle(new NotificationCompat.BigTextStyle()
					.bigText(bigText.toString()));
		} else {
			NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
			style.setBigContentTitle(unread.size() + " unread Conversations");
			StringBuilder names = new StringBuilder();
			for (int i = 0; i < unread.size(); ++i) {
				targetUuid = unread.get(i).getUuid();
				if (i < unread.size() - 1) {
					names.append(unread.get(i).getName() + ", ");
				} else {
					names.append(unread.get(i).getName());
				}
				style.addLine(Html.fromHtml("<b>" + unread.get(i).getName()
						+ "</b> " + unread.get(i).getLatestMessage().getBody().trim()));
			}
			mBuilder.setContentTitle(unread.size() + " unread Conversations");
			mBuilder.setContentText(names.toString());
			mBuilder.setStyle(style);
		}
		if (unread.size() != 0) {
			mBuilder.setSmallIcon(R.drawable.notification);
			if (notify) {
				if (vibrate) {
					int dat = 110;
					long[] pattern = {0,3*dat,dat,dat,dat,3*dat,dat,dat};
					mBuilder.setVibrate(pattern);
				}
				mBuilder.setLights(0xffffffff, 2000, 4000);
				if (ringtone != null) {
					mBuilder.setSound(Uri.parse(ringtone));
				}
			}

			TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
			stackBuilder.addParentStack(ConversationActivity.class);

			Intent viewConversationIntent = new Intent(context,
					ConversationActivity.class);
			viewConversationIntent.setAction(Intent.ACTION_VIEW);
			viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
					targetUuid);
			viewConversationIntent
					.setType(ConversationActivity.VIEW_CONVERSATION);
			
			stackBuilder.addNextIntent(viewConversationIntent);

			PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
					0, PendingIntent.FLAG_UPDATE_CURRENT);
			
			mBuilder.setContentIntent(resultPendingIntent);
			Notification notification = mBuilder.build();
			mNotificationManager.notify(2342, notification);
		}
	}
	
	public static void prepareContactBadge(final Activity activity,
			QuickContactBadge badge, final Contact contact) {
		if (contact.getSystemAccount() != null) {
			String[] systemAccount = contact.getSystemAccount().split("#");
			long id = Long.parseLong(systemAccount[0]);
			badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1]));

			if (contact.getProfilePhoto() != null) {
				badge.setImageURI(Uri.parse(contact.getProfilePhoto()));
			} else {
				badge.setImageBitmap(UIHelper.getUnknownContactPicture(
						contact.getDisplayName(), 400));
			}
		} else {
			badge.setImageBitmap(UIHelper.getUnknownContactPicture(
					contact.getDisplayName(), 400));
		}

	}

	public static AlertDialog getVerifyFingerprintDialog(
			final ConversationActivity activity,
			final Conversation conversation, final LinearLayout msg) {
		final Contact contact = conversation.getContact();
		final Account account = conversation.getAccount();

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Verify fingerprint");
		LayoutInflater inflater = activity.getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_verify_otr, null);
		TextView jid = (TextView) view.findViewById(R.id.verify_otr_jid);
		TextView fingerprint = (TextView) view
				.findViewById(R.id.verify_otr_fingerprint);
		TextView yourprint = (TextView) view
				.findViewById(R.id.verify_otr_yourprint);

		jid.setText(contact.getJid());
		fingerprint.setText(conversation.getOtrFingerprint());
		yourprint.setText(account.getOtrFingerprint());
		builder.setNegativeButton("Cancel", null);
		builder.setPositiveButton("Verify", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				contact.addOtrFingerprint(conversation.getOtrFingerprint());
				msg.setVisibility(View.GONE);
				activity.xmppConnectionService.updateContact(contact);
			}
		});
		builder.setView(view);
		return builder.create();
	}

	public static Bitmap getSelfContactPicture(Account account, int size, boolean showPhoneSelfContactPicture, Activity activity) {
		Uri selfiUri = PhoneHelper.getSefliUri(activity);
		if (selfiUri != null) {
			try {
				return BitmapFactory.decodeStream(activity
						.getContentResolver().openInputStream(selfiUri));
			} catch (FileNotFoundException e) {
				return getUnknownContactPicture(account.getJid(), size);
			}
		}
		return getUnknownContactPicture(account.getJid(), size);
	}
}
