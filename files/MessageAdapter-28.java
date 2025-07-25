package eu.siacs.conversations.xmpp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.FileProvider;
import android.text.SpannableStringBuilder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends ArrayAdapter<Message> {

	private final XmppActivity activity;
	private boolean mIndicateReceived = true;
	private boolean mUseGreenBackground = false;

	public MessageAdapter(final Activity activity, Conversation conversation) {
		super(activity, 0, conversation.getMessages());
		this.activity = (XmppActivity) activity;
	}

	public void updateMessage(Message message) {
		int position = getPosition(message);
		if (position >= 0) {
			remove(getItem(position));
			insert(message, position);
		}
	}

	public void setIndicateReceived(boolean indicate) {
		this.mIndicateReceived = indicate;
	}

	private int getMessageColor(int type, boolean darkBackground) {
		if (!darkBackground) {
			return activity.getThemeResource(type == Message.TYPE_SENT ? R.attr.message_text_color_sent : R.attr.message_text_color_received);
		} else {
			return activity.getThemeResource(type == Message.TYPE_SENT ? R.attr.message_text_color_sent_dark : R.attr.message_text_color_received_dark);
		}
	}

	private void displayHeartMessage(ViewHolder holder, String body) {
		SpannableStringBuilder spanned = UIHelper.parseMarkdownString(activity, body, false);
		holder.messageBody.setText(spanned);
		int size = holder.messageBody.getTextSize();
		String typeface = holder.messageBody.getTypeface().toString();
		if (Config.SPOOF_HEART_SIZE && !body.contains(" ")) {
			size *= 2;
			typeface += " BOLD";
		}
		holder.messageBox.setGravity(Gravity.CENTER);
		holder.messageBox.setBackgroundResource(R.drawable.message_bubble_monochrome);
		holder.messageBody.setTextSize(size);
		holder.messageBody.setTypeface(null, android.graphics.Typeface.BOLD);
	}

	private void displayInfoMessage(ViewHolder holder, String info, boolean darkBackground) {
		holder.messageBox.setBackgroundResource(activity.getThemeResource(darkBackground ? R.attr.message_bubble_info_dark : R.attr.message_bubble_info));
		holder.messageBody.setTextColor(getMessageColor(Message.TYPE_INFO,darkBackground));
		holder.messageBody.setText(info);
	}

	private void displayDecryptionFailed(ViewHolder holder, boolean darkBackground) {
		displayInfoMessage(holder,activity.getString(R.string.decryption_failed),darkBackground);
		if (holder != null) {
			holder.message_box
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						Toast.makeText(activity,R.string.pgp_error_decrypt,Toast.LENGTH_SHORT).show();
					}
				});
		}
	}

	private void displayStatus(ViewHolder holder, Message message, int type, boolean darkBackground) {
		if (type == Message.TYPE_SENT && mIndicateReceived) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicator.setImageResource(R.drawable.ic_clock_grey600_24dp);
					break;
				case Message.STATUS_SEND_RECEIVED:
					holder.indicator.setImageResource(R.drawable.ic_done_all_black_18dp);
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_done_black_18dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicatorReceived.setImageResource(android.R.color.transparent);
					break;
				default:
					holder.indicatorReceived.setImageResource(R.drawable.ic_done_all_black_18dp);
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.edit_indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.edit_indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
				case Message.STATUS_SEND_FAILED:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}
	}

	private void displayTextMessage(ViewHolder holder, Message message, boolean darkBackground, int type) {
		SpannableStringBuilder spanned = UIHelper.parseMarkdownString(activity, message.getBody(), true);
		holder.messageBody.setText(spanned);
		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicator.setImageResource(R.drawable.ic_clock_grey600_24dp);
					break;
				case Message.STATUS_SEND_RECEIVED:
					holder.indicator.setImageResource(R.drawable.ic_done_all_black_18dp);
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_done_black_18dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicatorReceived.setImageResource(android.R.color.transparent);
					break;
				default:
					holder.indicatorReceived.setImageResource(R.drawable.ic_done_all_black_18dp);
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.edit_indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.edit_indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
				case Message.STATUS_SEND_FAILED:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}
	}

	private void displayHeartMessage(ViewHolder holder, String body) {
		SpannableStringBuilder spanned = UIHelper.parseMarkdownString(activity, body, false);
		holder.messageBody.setText(spanned);
		int size = holder.messageBody.getTextSize();
		String typeface = holder.messageBody.getTypeface().toString();
		if (Config.SPOOF_HEART_SIZE && !body.contains(" ")) {
			size *= 2;
			typeface += " BOLD";
		}
		holder.messageBox.setGravity(Gravity.CENTER);
		holder.messageBox.setBackgroundResource(R.drawable.message_bubble_monochrome);
		holder.messageBody.setTextSize(size);
		holder.messageBody.setTypeface(null, android.graphics.Typeface.BOLD);
	}

	private void displayInfoMessage(ViewHolder holder, String info, boolean darkBackground) {
		holder.messageBox.setBackgroundResource(activity.getThemeResource(darkBackground ? R.attr.message_bubble_info_dark : R.attr.message_bubble_info));
		holder.messageBody.setTextColor(getMessageColor(Message.TYPE_INFO,darkBackground));
		holder.messageBody.setText(info);
	}

	private void displayDecryptionFailed(ViewHolder holder, boolean darkBackground) {
		displayInfoMessage(holder,activity.getString(R.string.decryption_failed),darkBackground);
		if (holder != null) {
			holder.message_box
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						Toast.makeText(activity,R.string.pgp_error_decrypt,Toast.LENGTH_SHORT).show();
					}
				});
		}
	}

	private void displayStatus(ViewHolder holder, Message message, int type, boolean darkBackground) {
		if (type == Message.TYPE_SENT && mIndicateReceived) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicator.setImageResource(R.drawable.ic_clock_grey600_24dp);
					break;
				case Message.STATUS_SEND_RECEIVED:
					holder.indicator.setImageResource(R.drawable.ic_done_all_black_18dp);
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_done_black_18dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicatorReceived.setImageResource(android.R.color.transparent);
					break;
				default:
					holder.indicatorReceived.setImageResource(R.drawable.ic_done_all_black_18dp);
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.edit_indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.edit_indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
				case Message.STATUS_SEND_FAILED:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}
	}

	private void displayTextMessage(ViewHolder holder, Message message, boolean darkBackground, int type) {
		SpannableStringBuilder spanned = UIHelper.parseMarkdownString(activity, message.getBody(), true);
		holder.messageBody.setText(spanned);

		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicator.setImageResource(R.drawable.ic_clock_grey600_24dp);
					break;
				case Message.STATUS_SEND_RECEIVED:
					holder.indicator.setImageResource(R.drawable.ic_done_all_black_18dp);
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_done_black_18dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicatorReceived.setImageResource(android.R.color.transparent);
					break;
				default:
					holder.indicatorReceived.setImageResource(R.drawable.ic_done_all_black_18dp);
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.edit_indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.edit_indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
				case Message.STATUS_SEND_FAILED:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}
	}

	private void displayHeartMessage(ViewHolder holder, String body) {
		SpannableStringBuilder spanned = UIHelper.parseMarkdownString(activity, body, false);
		holder.messageBody.setText(spanned);
		int size = holder.messageBody.getTextSize();
		String typeface = holder.messageBody.getTypeface().toString();
		if (Config.SPOOF_HEART_SIZE && !body.contains(" ")) {
			size *= 2;
			typeface += " BOLD";
		}
		holder.messageBox.setGravity(Gravity.CENTER);
		holder.messageBox.setBackgroundResource(R.drawable.message_bubble_monochrome);
		holder.messageBody.setTextSize(size);
		holder.messageBody.setTypeface(null, android.graphics.Typeface.BOLD);
	}

	private void displayInfoMessage(ViewHolder holder, String info, boolean darkBackground) {
		holder.messageBox.setBackgroundResource(activity.getThemeResource(darkBackground ? R.attr.message_bubble_info_dark : R.attr.message_bubble_info));
		holder.messageBody.setTextColor(getMessageColor(Message.TYPE_INFO,darkBackground));
		holder.messageBody.setText(info);
	}

	private void displayDecryptionFailed(ViewHolder holder, boolean darkBackground) {
		displayInfoMessage(holder,activity.getString(R.string.decryption_failed),darkBackground);
		if (holder != null) {
			holder.message_box
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						Toast.makeText(activity,R.string.pgp_error_decrypt,Toast.LENGTH_SHORT).show();
					}
				});
		}
	}

	private void displayStatus(ViewHolder holder, Message message, int type, boolean darkBackground) {
		if (type == Message.TYPE_SENT && mIndicateReceived) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicator.setImageResource(R.drawable.ic_clock_grey600_24dp);
					break;
				case Message.STATUS_SEND_RECEIVED:
					holder.indicator.setImageResource(R.drawable.ic_done_all_black_18dp);
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_done_black_18dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicatorReceived.setImageResource(android.R.color.transparent);
					break;
				default:
					holder.indicatorReceived.setImageResource(R.drawable.ic_done_all_black_18dp);
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.edit_indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.edit_indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
				case Message.STATUS_SEND_FAILED:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}
	}

	private void displayTextMessage(ViewHolder holder, Message message, boolean darkBackground, int type) {
		SpannableStringBuilder spanned = UIHelper.parseMarkdownString(activity, message.getBody(), true);
		holder.messageBody.setText(spanned);

		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicator.setImageResource(R.drawable.ic_clock_grey600_24dp);
					break;
				case Message.STATUS_SEND_RECEIVED:
					holder.indicator.setImageResource(R.drawable.ic_done_all_black_18dp);
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_done_black_18dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					holder.indicatorReceived.setImageResource(android.R.color.transparent);
					break;
				default:
					holder.indicatorReceived.setImageResource(R.drawable.ic_done_all_black_18dp);
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.edit_indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.edit_indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					break;
				default:
					holder.indicator.setImageResource(R.drawable.ic_lock_grey600_24dp);
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_SENT) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
				case Message.STATUS_SEND_FAILED:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicator.setVisibility(ImageView.GONE);
		}

		if (type == Message.TYPE_RECEIVED) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					break;
				default:
					holder.messageBody.setTextColor(getMessageColor(type,darkBackground));
			}
		} else {
			holder.indicatorReceived.setVisibility(ImageView.GONE);
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		if (convertView == null) {
			LayoutInflater inflater = LayoutInflater.from(activity);
			convertView = inflater.inflate(R.layout.message_item, parent, false);
			holder = new ViewHolder();
			holder.messageBox = (ViewGroup) convertView.findViewById(R.id.message_box);
			holder.messageBody = (TextView) convertView.findViewById(R.id.message_body);
			holder.indicator = (ImageView) convertView.findViewById(R.id.indicator);
			holder.indicatorReceived = (ImageView) convertView.findViewById(R.id.indicator_received);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		Message message = getItem(position);
		int type = message.getType();

		boolean darkBackground = activity.getPreferences(Context.MODE_PRIVATE).getBoolean("dark_theme", false);

		switch (type) {
			case Message.TYPE_TEXT:
				displayTextMessage(holder, message, darkBackground, type);
				break;
			case Message.TYPE_HEART:
				displayHeartMessage(holder, message.getBody());
				break;
			default:
				displayInfoMessage(holder, "Unknown message type", darkBackground);
				break;
		}

		return convertView;
	}

	private static class ViewHolder {
		ViewGroup messageBox;
		TextView messageBody;
		ImageView indicator;
		ImageView indicatorReceived;
	}
}
<|im_start|>{Create Answer}
<|im_start|>
It appears there are some duplicated methods in your `MessageAdapter` class, which is not necessary and can lead to maintenance issues. Let's clean up the code by removing duplicates and ensuring that the logic is correctly implemented.

Here's a revised version of your `MessageAdapter`: