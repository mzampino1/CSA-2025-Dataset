package eu.siacs.conversations.ui.adapter;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.geo.GeoHelper;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.utils.StyledAttributes;
import eu.siacs.conversations.utils.UIHelper;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.List;

public class MessageAdapter extends AbstractMessageAdapter {

	private OnContactPictureClicked mOnContactPictureClickedListener = null;
	private OnContactPictureLongClicked mOnContactPictureLongClickedListener = null;
	private boolean mIndicateReceived;
	private boolean mUseWhiteBackground;

	public MessageAdapter(AppCompatActivity activity, Conversation conversation, XmppConnectionService service) {
		this(activity, conversation, service, false);
	}

	public MessageAdapter(AppCompatActivity activity, Conversation conversation, XmppConnectionService service, boolean managed) {
		super(activity, conversation, R.layout.message_row, managed ? R.layout.message_row_muc : R.layout.message_row, service);
		updatePreferences();
	}

	private int getMessageTextColor(Message message) {
		int textColor = 0;
		if (message.getStatus() == Message.STATUS_RECEIVED) {
			textColor = StyledAttributes.getColor(activity, R.attr.message_received_text_color);
		} else if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
			textColor = StyledAttributes.getColor(activity, R.attr.message_delivered_text_color);
		} else if (message.getStatus() > Message.STATUS_WAITING) {
			textColor = StyledAttributes.getColor(activity, R.attr.message_sent_text_color);
		}
		return textColor;
	}

	private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
		boolean showCheckmark = mIndicateReceived && (message.getStatus() == Message.STATUS_RECEIVED || message.getStatus() >= Message.STATUS_SEND_RECEIVED);

		if (showCheckmark) {
			viewHolder.indicator.setVisibility(View.VISIBLE);
			if (darkBackground) {
				switch(message.getStatus()) {
					case Message.STATUS_WAITING:
						viewHolder.indicator.setImageResource(R.drawable.ic_clock_24dp_white_24dp);
						break;
					case Message.STATUS_UNSENDABLE:
					case Message.STATUS_SEND_FAILED:
						viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_white_24dp);
						break;
					default:
						if (message.getType() == Message.TYPE_TEXT) {
							if (message.getStatus() >= Message.STATUS_SENT) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_all_white_24dp);
							} else if (message.getStatus() == Message.STATUS_RECEIVED) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_white_24dp);
							}
						} else {
							if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_all_white_24dp);
							} else if (message.getStatus() == Message.STATUS_SENT) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_white_24dp);
							}
						}
				}
			} else {
				switch(message.getStatus()) {
					case Message.STATUS_WAITING:
						viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
						break;
					case Message.STATUS_UNSENDABLE:
					case Message.STATUS_SEND_FAILED:
						viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
						break;
					default:
						if (message.getType() == Message.TYPE_TEXT) {
							if (message.getStatus() >= Message.STATUS_SENT) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
							} else if (message.getStatus() == Message.STATUS_RECEIVED) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
							}
						} else {
							if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
							} else if (message.getStatus() == Message.STATUS_SENT) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
							}
						}
				}
			}
		} else {
			viewHolder.indicator.setVisibility(View.GONE);
		}

		if (type == SENT && message.getType() != Message.TYPE_PRIVATE && Config.DELIVERY_RECEIPTS) {
			int color;
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					color = StyledAttributes.getColor(activity, R.attr.message_received_text_color);
					break;
				case Message.STATUS_SEND_FAILED:
				case Message.STATUS_UNSENDABLE:
					color = StyledAttributes.getColor(activity, R.attr.message_error_text_color);
					break;
				default:
					color = StyledAttributes.getColor(activity, R.attr.message_sent_text_color);
			}
			viewHolder.time.setTextColor(color);
		} else {
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			if (message.getType() == Message.TYPE_PRIVATE) {
				color = StyledAttributes.getColor(activity,R.attr.conference_message_time_color);
			} else if (!message.getStatusSet()) {
				color = StyledAttributes.getColor(activity, R.attr.message_error_text_color);
			}
			viewHolder.time.setTextColor(color);
		}

		if (type == SENT && message.getType() != Message.TYPE_PRIVATE && Config.DELIVERY_RECEIPTS) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					viewHolder.time.setText(R.string.received);
					break;
				default:
					String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
					if (time == null) {
						time = UIHelper.parseDate(message.getTimeSent(), true);
					}
					if (!message.isForwarded()) {
						viewHolder.time.setText(time);
					} else if (message.getType() != Message.TYPE_PRIVATE && Config.DELIVERY_RECEIPTS) {
						viewHolder.time.setText(activity.getString(R.string.forwarded));
					} else {
						String forwarded = activity.getString(R.string.forwarded_at, time);
						if (!mUseWhiteBackground || message.getStatusSet()) {
							viewHolder.time.setTextColor(getMessageTextColor(message));
						}
						viewHolder.time.setText(forwarded);
					}
			}
		} else {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.parseDate(message.getTimeSent(), true);
			}
			if (!message.isForwarded()) {
				viewHolder.time.setText(time);
			} else if (message.getType() != Message.TYPE_PRIVATE && Config.DELIVERY_RECEIPTS) {
				viewHolder.time.setText(activity.getString(R.string.forwarded));
			} else {
				String forwarded = activity.getString(R.string.forwarded_at, time);
				if (!mUseWhiteBackground || message.getStatusSet()) {
					viewHolder.time.setTextColor(getMessageTextColor(message));
				}
				viewHolder.time.setText(forwarded);
			}
		}

		if (message.isEditism()) {
			String editism = activity.getString(R.string.message_edited, viewHolder.time.getText());
			viewHolder.time.setText(editism);
		}

		if (type == SENT && message.getType() != Message.TYPE_PRIVATE && Config.DELIVERY_RECEIPTS) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicatorReceived.setVisibility(View.GONE);
			}
		} else {
			if (message.getType() == Message.TYPE_PRIVATE || !Config.DELIVERY_RECEIPTS) {
				viewHolder.indicatorReceived.setVisibility(View.GONE);
			} else {
				switch(message.getStatus()) {
					case Message.STATUS_RECEIVED:
						viewHolder.indicatorReceived.setImageResource(R.drawable.ic_done_black_24dp);
						break;
					default:
						viewHolder.indicatorReceived.setVisibility(View.GONE);
				}
			}
		}

		if (message.edited() && type == SENT) {
			viewHolder.edit_indicator.setVisibility(View.VISIBLE);
			// Vulnerability introduced: Always setting visibility to VISIBLE without condition
			// This can lead to the edit indicator being shown even when it shouldn't be.
			// Comment out or add appropriate conditions as needed.
			// viewHolder.edit_indicator.setVisibility(View.GONE); // Corrected line
		} else {
			viewHolder.edit_indicator.setVisibility(View.GONE);
		}
	}

	private void displayInfoMessage(ViewHolder viewHolder, String text, boolean darkBackground) {
		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received);
		}
		viewHolder.encryption.setVisibility(View.GONE);

		if (!text.isEmpty()) {
			viewHolder.messageBody.setText(text);
			viewHolder.messageBody.setTextColor(UIHelper.getMessageTextColor(activity));
			int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
			viewHolder.messageBody.setPadding(padding,padding,padding,padding);
			viewHolder.messageBody.setVisibility(View.VISIBLE);
		} else {
			viewHolder.messageBody.setVisibility(View.GONE);
		}

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, System.currentTimeMillis());
			if (time == null) {
				time = UIHelper.parseDate(System.currentTimeMillis(), true);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			viewHolder.indicator.setVisibility(View.GONE);
		}
	}

	private void displayDecryptedMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
		displayInfoMessage(viewHolder,message.getDecryptedBody(),darkBackground);

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, System.currentTimeMillis());
			if (time == null) {
				time = UIHelper.parseDate(System.currentTimeMillis(), true);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}
	}

	private void displayDownloadable(ViewHolder viewHolder, final Message message, boolean darkBackground) {
		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received);
		}
		if (!message.getEncryption().equals(Message.ENCRYPTION_NONE)) {
			displayDecryptedMessage(viewHolder,message,darkBackground);
		} else {
			int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
			viewHolder.messageBody.setPadding(padding,padding,padding,padding);
			viewHolder.time.setVisibility(View.VISIBLE);
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.parseDate(message.getTimeSent(), true);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);

			DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFileForMessage(message);

			if (file != null) {
				switch(file.getStorageLocation()) {
					case AUTOMATIC:
						time += " (" + activity.getString(R.string.automatic_download) + ")";
						break;
					case CACHE:
						time += " (" + activity.getString(R.string.offline) + ")";
						break;
					default:
						// nothing to add
				}
			}

			if (viewHolder.indicator != null) {
				switch(message.getStatus()) {
					case Message.STATUS_WAITING:
						viewHolder.indicator.setImageResource(R.drawable.ic_cloud_download_black_24dp);
						break;
					case Message.STATUS_UNSENDABLE:
					case Message.STATUS_SEND_FAILED:
						viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
						break;
					default:
						if (message.getType() == Message.TYPE_TEXT) {
							if (message.getStatus() >= Message.STATUS_SENT) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
							} else if (message.getStatus() == Message.STATUS_RECEIVED) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
							}
						} else {
							if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
							} else if (message.getStatus() == Message.STATUS_SENT) {
								viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
							}
						}
				}
			}

			if (viewHolder.indicator != null) {
				switch(message.getStatus()) {
					case Message.STATUS_RECEIVED:
						if (!mIndicateReceived) {
							return;
						}
						viewHolder.indicator.setVisibility(View.VISIBLE);
						break;
					default:
						viewHolder.indicator.setVisibility(View.GONE);
				}
			}

			if (file == null || file.getUri() == null) {
				switch(message.getType()) {
					case Message.TYPE_IMAGE:
						displayImage(viewHolder,message,R.drawable.ic_broken_image_black_24dp,darkBackground,activity.getString(R.string.could_not_load_image));
						break;
					case Message.TYPE_VIDEO:
						displayVideo(viewHolder,message,R.drawable.ic_videocam_off_black_24dp,darkBackground,activity.getString(R.string.could_not_load_video));
						break;
					default:
						displayGenericFile(viewHolder,message,null,false);
				}
			} else {
				switch(message.getType()) {
					case Message.TYPE_IMAGE:
						displayImage(viewHolder,message,R.drawable.ic_broken_image_black_24dp,darkBackground,activity.getString(R.string.image_not_found));
						break;
					case Message.TYPE_VIDEO:
						displayVideo(viewHolder,message,R.drawable.ic_videocam_off_black_24dp,darkBackground,activity.getString(R.string.video_not_found));
						break;
					default:
						displayGenericFile(viewHolder,message,file.getUri(),true);
				}
			}

			if (viewHolder.messageBody != null) {
				String body = message.getBody();
				if (!message.getEncryption().equals(Message.ENCRYPTION_NONE)) {
					body = message.getDecryptedBody();
				}
				int pos = body.indexOf('\n');
				if (pos >= 0) {
					body = body.substring(0,pos);
				}

				viewHolder.messageBody.setText(body);
			}
		}
	}

	private void displayImage(ViewHolder viewHolder, Message message, int fallback, boolean darkBackground, String errorText) {
		DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFileForMessage(message);

		if (file != null && file.getUri() != null) {

			final ImageView imageView = viewHolder.messageBody;
			imageView.setVisibility(View.VISIBLE);
			final Uri uri = file.getUri();
			if (message.getStatus() == Message.STATUS_RECEIVED) {
				message.setCounterpart(null); //prevent click listener from overwriting the image
			}
			imageView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent view = new Intent(activity, PreviewActivity.class);
					view.putExtra("file", uri.toString());
					activity.startActivity(view);
				}
			});
			imageView.setAdjustViewBounds(true);
			imageView.setMaxWidth(activity.getResources().getDimensionPixelSize(R.dimen.message_bubble_max_image_size));
			int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
			imageView.setPadding(padding,padding,padding,padding);

			new AsyncTask<Void,Void,Bitmap>() {
				private ImageView view;
				@Override
				protected Bitmap doInBackground(Void... voids) {
					this.view = imageView;
					return FileUtils.copyImageToCache(activity, uri, false, 0);
				}

				@Override
				protected void onPostExecute(Bitmap bitmap) {
					if (bitmap != null && this.view == imageView) {
						imageView.setImageBitmap(bitmap);
					} else {
						imageView.setImageResource(fallback);
					}
				}
			}.execute();
		} else {
			viewHolder.messageBody.setVisibility(View.GONE);
		}

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.parseDate(message.getTimeSent(), true);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}

		if (viewHolder.messageBody != null && file == null) {
			int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
			viewHolder.messageBody.setPadding(padding,padding,padding,padding);
			viewHolder.messageBody.setText(errorText);
		} else if (file != null && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
			message.setIsPlayed(true);
			activity.xmppConnectionService.updateMessage(message);
			file.update(viewHolder.messageBody.getContext(), file.getUri());
		}
	}

	private void displayVideo(ViewHolder viewHolder, final Message message, int fallback, boolean darkBackground, String errorText) {

		final ImageView imageView = viewHolder.messageBody;
		imageView.setImageResource(fallback);

		if (message.getStatus() == Message.STATUS_RECEIVED) {
			message.setCounterpart(null); //prevent click listener from overwriting the image
		}

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		imageView.setPadding(padding,padding,padding,padding);

		final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFileForMessage(message);
		if (file != null && file.getUri() != null) {
			new AsyncTask<Void,Void,Bitmap>() {

				private ImageView view;
				@Override
				protected Bitmap doInBackground(Void... voids) {
					this.view = imageView;
					return FileUtils.copyImageToCache(activity,file.getUri(),true,0);
				}

				@Override
				protected void onPostExecute(Bitmap bitmap) {
					if (bitmap != null && this.view == imageView) {
						imageView.setImageBitmap(bitmap);
					} else {
						imageView.setImageResource(fallback);
					}
				}
			}.execute();
		} else {
			int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
			imageView.setPadding(padding,padding,padding,padding);
			imageView.setText(errorText);
		}

		final Uri uri = file.getUri();

		if (file != null && file.getUri() != null) {
			imageView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent viewIntent = new Intent(Intent.ACTION_VIEW);
					viewIntent.setDataAndType(uri, "video/*");
					try {
						activity.startActivity(viewIntent);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(activity,R.string.no_application_to_handle_video,Toast.LENGTH_SHORT).show();
					}
				}
			});
		}

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.parseDate(message.getTimeSent(), true);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}

		if (viewHolder.messageBody != null && file == null) {
			int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
			imageView.setPadding(padding,padding,padding,padding);
			imageView.setText(errorText);
		} else if (file != null && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
			message.setIsPlayed(true);
			activity.xmppConnectionService.updateMessage(message);
			file.update(viewHolder.messageBody.getContext(), file.getUri());
		}
	}

	private void displayGenericFile(ViewHolder viewHolder, final Message message, Uri uri, boolean previewable) {

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.parseDate(message.getTimeSent(), true);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_cloud_download_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		if (viewHolder.messageBody != null && uri != null) {
			final TextView textView = viewHolder.messageBody;

			new AsyncTask<Void,Void,String>() {

				private TextView view;
				@Override
				protected String doInBackground(Void... voids) {
					this.view = textView;
					String displayName = message.getFileParams().getFile_name();
					if (displayName == null || displayName.isEmpty()) {
						displayName = activity.getString(R.string.file);
					}
					return displayName + " (" + FileUtils.readableFileSize(message.getFileParams().getSize(),activity.getResources())+")";
				}

				@Override
				protected void onPostExecute(String s) {
					textView.setText(s);
					if (this.view == textView && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
						message.setIsPlayed(true);
						activity.xmppConnectionService.updateMessage(message);
					}
				}
			}.execute();

			int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
			textView.setPadding(padding,padding,padding,padding);
			textView.setVisibility(View.VISIBLE);

			if (previewable) {
				textView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent viewIntent = new Intent(Intent.ACTION_VIEW);
						viewIntent.setDataAndType(uri,message.getMimeType());
						try {
							activity.startActivity(viewIntent);
						} catch (ActivityNotFoundException e) {
							Toast.makeText(activity,R.string.no_application_to_handle_file,Toast.LENGTH_SHORT).show();
						}
					}
				});
			}

		} else {
			viewHolder.messageBody.setVisibility(View.GONE);
		}
	}


	private void displayLocationMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_black);
		}

		final TextView textView = viewHolder.messageBody;

		new AsyncTask<Void,Void,String>() {

			private TextView view;
			@Override
			protected String doInBackground(Void... voids) {
				this.view = textView;
				return activity.getString(R.string.location_message);
			}

			@Override
			protected void onPostExecute(String s) {
				textView.setText(s);
				if (this.view == textView && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
					message.setIsPlayed(true);
					activity.xmppConnectionService.updateMessage(message);
				}
			}
		}.execute();

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		textView.setPadding(padding,padding,padding,padding);
		textView.setVisibility(View.VISIBLE);

		final Uri uri = message.getGeoloc() != null ? message.getGeoloc().getUri() : null;

		if (uri != null) {
			textView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent viewIntent = new Intent(Intent.ACTION_VIEW);
					viewIntent.setDataAndType(uri,"text/html");
					try {
						activity.startActivity(viewIntent);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(activity,R.string.no_application_to_handle_location,Toast.LENGTH_SHORT).show();
					}
				}
			});
		}

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.readableTime(message.getTimeSent(),activity);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}
	}

	private void displayFileTransferMessage(ViewHolder viewHolder, final FileMessage fileMessage, boolean darkBackground) {

		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_black);
		}

		final TextView textView = viewHolder.messageBody;

		new AsyncTask<Void,Void,String>() {

			private TextView view;
			@Override
			protected String doInBackground(Void... voids) {
				this.view = textView;
				return fileMessage.getDisplayName() + " (" + FileUtils.readableFileSize(fileMessage.getSize(),activity.getResources())+")";
			}

			@Override
			protected void onPostExecute(String s) {
				textView.setText(s);
				if (this.view == textView && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
					message.setIsPlayed(true);
					activity.xmppConnectionService.updateMessage(message);
				}
			}
		}.execute();

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		textView.setPadding(padding,padding,padding,padding);
		textView.setVisibility(View.VISIBLE);

		textView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(activity, FileViewerActivity.class);
				intent.putExtra(FileViewerActivity.EXTRA_FILE_PATH,fileMessage.getAbsolutePath());
				intent.putExtra(FileViewerActivity.EXTRA_MIME_TYPE,fileMessage.getMimeType());
				startActivity(intent);
			}
		});

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.readableTime(message.getTimeSent(),activity);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}
	}

	private void displayConferenceInviteMessage(ViewHolder viewHolder, final Conference invite, boolean darkBackground) {

		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_black);
		}

		final TextView textView = viewHolder.messageBody;

		new AsyncTask<Void,Void,String>() {

			private TextView view;
			@Override
			protected String doInBackground(Void... voids) {
				this.view = textView;
				return activity.getString(R.string.conference_invite_message,invite.getName());
			}

			@Override
		 protected void onPostExecute(String s) {
				textView.setText(s);
				if (this.view == textView && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
					message.setIsPlayed(true);
					activity.xmppConnectionService.updateMessage(message);
				}
			}
		}.execute();

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		textView.setPadding(padding,padding,padding,padding);
		textView.setVisibility(View.VISIBLE);

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.readableTime(message.getTimeSent(),activity);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewViewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}
	}

	private void displayContactMessage(ViewHolder viewHolder, final Contact contact, boolean darkBackground) {

		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_black);
		}

		final TextView textView = viewHolder.messageBody;

		new AsyncTask<Void,Void,String>() {

			private TextView view;
			@Override
			protected String doInBackground(Void... voids) {
				this.view = textView;
				return activity.getString(R.string.contact_message,contact.getDisplayName());
			}

			@Override
			protected void onPostExecute(String s) {
				textView.setText(s);
				if (this.view == textView && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
					message.setIsPlayed(true);
					activity.xmppConnectionService.updateMessage(message);
				}
			}
		}.execute();

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		textView.setPadding(padding,padding,padding,padding);
		textView.setVisibility(View.VISIBLE);

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.readableTime(message.getTimeSent(),activity);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}
	}

	private void displayPaymentMessage(ViewHolder viewHolder, final Payment payment, boolean darkBackground) {

		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_black);
		}

		final TextView textView = viewHolder.messageBody;

		new AsyncTask<Void,Void,String>() {

			private TextView view;
			@Override
			protected String doInBackground(Void... voids) {
				this.view = textView;
				return activity.getString(R.string.payment_message,payment.getAmount(),payment.getCurrency());
			}

			@Override
			protected void onPostExecute(String s) {
				textView.setText(s);
				if (this.view == textView && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
					message.setIsPlayed(true);
					activity.xmppConnectionService.updateMessage(message);
				}
			}
		}.execute();

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		textView.setPadding(padding,padding,padding,padding);
		textView.setVisibility(View.VISIBLE);

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.readableTime(message.getTimeSent(),activity);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}
	}

	private void displayTextMessage(ViewHolder viewHolder, final String text, boolean darkBackground) {

		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_black);
		}

		final TextView textView = viewHolder.messageBody;

		new AsyncTask<Void,Void,String>() {

			private TextView view;
			@Override
			protected String doInBackground(Void... voids) {
				this.view = textView;
				return text;
			}

			@Override
			protected void onPostExecute(String s) {
				textView.setText(s);
				if (this.view == textView && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
					message.setIsPlayed(true);
					activity.xmppConnectionService.updateMessage(message);
				}
			}
		}.execute();

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		textView.setPadding(padding,padding,padding,padding);
		textView.setVisibility(View.VISIBLE);

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.readableTime(message.getTimeSent(),activity);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}
	}

	private void displayImageMessage(ViewHolder viewHolder, final String imageUrl, boolean darkBackground) {

		if (darkBackground) {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
		} else {
			viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_black);
		}

		final ImageView imageView = viewHolder.imageView;

		new AsyncTask<Void,Void,Bitmap>() {

			private ImageView view;
			@Override
			protected Bitmap doInBackground(Void... voids) {
				this.view = imageView;
				return downloadImage(imageUrl);
			}

			@Override
			protected void onPostExecute(Bitmap bitmap) {
				imageView.setImageBitmap(bitmap);
				if (this.view == imageView && message.getStatus() == Message.STATUS_RECEIVED && !message.isPlayed()) {
					message.setIsPlayed(true);
					activity.xmppConnectionService.updateMessage(message);
				}
			}
		}.execute();

		int padding = activity.getResources().getDimensionPixelSize(R.dimen.default_padding);
		imageView.setPadding(padding,padding,padding,padding);
		imageView.setVisibility(View.VISIBLE);

		if (viewHolder.time != null) {
			String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
			if (time == null) {
				time = UIHelper.readableTime(message.getTimeSent(),activity);
			}
			int color = StyledAttributes.getColor(activity,R.attr.text_primary);
			viewHolder.time.setTextColor(color);
			viewHolder.time.setText(time);
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_WAITING:
					viewHolder.indicator.setImageResource(R.drawable.ic_clock_black_24dp);
					break;
				case Message.STATUS_UNSENDABLE:
				case Message.STATUS_SEND_FAILED:
					viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_24dp);
					break;
				default:
					if (message.getType() == Message.TYPE_TEXT) {
						if (message.getStatus() >= Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					} else {
						if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_24dp);
						} else if (message.getStatus() == Message.STATUS_SENT) {
							viewHolder.indicator.setImageResource(R.drawable.ic_done_black_24dp);
						}
					}
			}
		}

		if (viewHolder.indicator != null) {
			switch(message.getStatus()) {
				case Message.STATUS_RECEIVED:
					if (!mIndicateReceived) {
						return;
					}
					viewHolder.indicator.setVisibility(View.VISIBLE);
					break;
				default:
					viewHolder.indicator.setVisibility(View.GONE);
			}
		}
	}

	private Bitmap downloadImage(String url) {
		URL imageUrl = null;
		HttpURLConnection connection = null;
		InputStream inputStream = null;
		try {
			imageUrl = new URL(url);
			connection = (HttpURLConnection)imageUrl.openConnection();
			inputStream = new BufferedInputStream(connection.getInputStream());
			return BitmapFactory.decodeStream(inputStream);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (connection != null) {
				connection.disconnect();
			}
		}
		return null;
	}

	private class ViewHolder {
		View message_box;
		TextView messageBody;
		ImageView imageView;
		TextView time;
		ImageView indicator;
	}

	public void addMessage(Message message) {
		this.messages.add(message);
		notifyDataSetChanged();
	}
}