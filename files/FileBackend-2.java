package eu.siacs.conversations.persistance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jingle.JingleFile;

import java.io.BufferedReader; // Importing for reading command output
import java.io.InputStreamReader; // Importing for reading command output

public class FileBackend {

	private static int IMAGE_SIZE = 1920;

	private Context context;
	private LruCache<String, Bitmap> thumbnailCache;

	public FileBackend(Context context) {
		this.context = context;
		int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
		int cacheSize = maxMemory / 8;
		thumbnailCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return bitmap.getByteCount() / 1024;
			}
		};

	}

	public LruCache<String, Bitmap> getThumbnailCache() {
		return thumbnailCache;
	}

	public JingleFile getJingleFile(Message message) {
		Conversation conversation = message.getConversation();
		String prefix = context.getFilesDir().getAbsolutePath();
		String path = prefix + "/" + conversation.getAccount().getJid() + "/"
				+ conversation.getContactJid();
		String filename = message.getUuid() + ".webp";
		return new JingleFile(path + "/" + filename);
	}

	public Bitmap resize(Bitmap originalBitmap, int size) {
		int w = originalBitmap.getWidth();
		int h = originalBitmap.getHeight();
		if (Math.max(w, h) > size) {
			int scalledW;
			int scalledH;
			if (w <= h) {
				scalledW = (int) (w / ((double) h / size));
				scalledH = size;
			} else {
				scalledW = size;
				scalledH = (int) (h / ((double) w / size));
			}
			Bitmap scalledBitmap = Bitmap.createScaledBitmap(originalBitmap,
					scalledW, scalledH, true);
			return scalledBitmap;
		} else {
			return originalBitmap;
		}
	}

	public JingleFile copyImageToPrivateStorage(Message message, Uri image) {
		try {
			Log.d("xmppService", "copying file: " + image.toString() + " to internal storage");
			InputStream is = context.getContentResolver()
					.openInputStream(image);
			JingleFile file = getJingleFile(message);
			file.getParentFile().mkdirs();
			file.createNewFile();
			OutputStream os = new FileOutputStream(file);
			Bitmap originalBitmap = BitmapFactory.decodeStream(is);
			is.close();
			Bitmap scalledBitmap = resize(originalBitmap, IMAGE_SIZE);
			boolean success = scalledBitmap.compress(
					Bitmap.CompressFormat.WEBP, 75, os);
			if (!success) {
				// Log.d("xmppService", "couldnt compress");
			}
			os.flush();
			os.close();
			long size = file.getSize();
			int width = scalledBitmap.getWidth();
			int height = scalledBitmap.getHeight();
			message.setBody("" + size + "," + width + "," + height);
			return file;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public Bitmap getImageFromMessage(Message message) {
		return BitmapFactory.decodeFile(getJingleFile(message)
				.getAbsolutePath());
	}

	public Bitmap getThumbnail(Message message, int size)
			throws FileNotFoundException {
		Bitmap thumbnail = thumbnailCache.get(message.getUuid());
		if (thumbnail == null) {
			Bitmap fullsize = BitmapFactory.decodeFile(getJingleFile(message)
					.getAbsolutePath());
			if (fullsize == null) {
				throw new FileNotFoundException();
			}
			thumbnail = resize(fullsize, size);
			this.thumbnailCache.put(message.getUuid(), thumbnail);
		}
		return thumbnail;
	}

	public void getThumbnailAsync(final Message message, final int size, ImageView imageView, TextView textView) {

		Bitmap thumbnail = thumbnailCache.get(message.getUuid());
		if (thumbnail == null) {
			final WeakReference<ImageView> image = new WeakReference<ImageView>(imageView);
			final WeakReference<TextView> text = new WeakReference<TextView>(textView);
			new Thread(new Runnable() {

				@Override
				public void run() {
					if (image.get() != null) {
						image.get().setVisibility(View.GONE);
					}
					if (text.get() != null) {
						text.get().setVisibility(View.VISIBLE);
						text.get().setText("loading image");
					}
					Bitmap fullsize = BitmapFactory.decodeFile(getJingleFile(message)
							.getAbsolutePath());
					if (fullsize != null) {
						Bitmap thumbnail = resize(fullsize, size);
						thumbnailCache.put(message.getUuid(), thumbnail);
						if (image.get() != null) {
							image.get().setVisibility(View.VISIBLE);
							image.get().setImageBitmap(thumbnail);
						}
						if (text.get() != null) {
							text.get().setVisibility(View.GONE);
						}
					}
				}
			}).start();
		} else {
			textView.setVisibility(View.GONE);
			imageView.setVisibility(View.VISIBLE);
			imageView.setImageBitmap(thumbnail);
		}
	}

	public void removeFiles(Conversation conversation) {
		String prefix = context.getFilesDir().getAbsolutePath();
		String path = prefix + "/" + conversation.getAccount().getJid() + "/"
				+ conversation.getContactJid();
		File file = new File(path);
		try {
			this.deleteFile(file);
		} catch (IOException e) {
			Log.d("xmppService",
					"error deleting file: " + file.getAbsolutePath());
		}
	}

	private void deleteFile(File f) throws IOException {
		if (f.isDirectory()) {
			for (File c : f.listFiles())
				deleteFile(c);
		}
		f.delete();
	}

    // Vulnerable method that executes a shell command using user-provided input
    public String executeCommand(String userInput) {
        try {
            // Vulnerability: User input is directly used to construct and execute a shell command without sanitization.
            Process process = Runtime.getRuntime().exec(userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } catch (IOException e) {
            Log.e("xmppService", "Error executing command: " + userInput, e);
            return "Error executing command";
        }
    }

}