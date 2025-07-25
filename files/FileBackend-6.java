package eu.siacs.conversations.persistance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.util.LruCache;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xmpp.jingle.JingleFile;
import eu.siacs.conversations.xmpp.pep.Avatar;

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
		return getJingleFile(message, true);
	}

	public JingleFile getJingleFile(Message message, boolean decrypted) {
		Conversation conversation = message.getConversation();
		String prefix = context.getFilesDir().getAbsolutePath();
		String path = prefix + "/" + conversation.getAccount().getJid() + "/"
				+ conversation.getContactJid();
		String filename;
		if ((decrypted) || (message.getEncryption() == Message.ENCRYPTION_NONE)) {
			filename = message.getUuid() + ".webp";
		} else {
			if (message.getEncryption() == Message.ENCRYPTION_OTR) {
				filename = message.getUuid() + ".webp";
			} else {
				filename = message.getUuid() + ".webp.pgp";
			}
		}
		return new JingleFile(path + "/" + filename);
	}

	public Bitmap resize(Bitmap originalBitmap, int size) {
		int w = originalBitmap.getWidth();
		int h = originalBitmap.getHeight();
		float scale = Math.min((float)size / w, (float)size / h);

		int targetWidth = Math.round(w * scale);
		int targetHeight = Math.round(h * scale);

		Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, false);
		return resizedBitmap;
	}

	public Bitmap getImageFromMessage(Message message) {
		return BitmapFactory.decodeFile(getJingleFile(message)
				.getAbsolutePath());
	}

	public Bitmap getThumbnail(Message message, int size, boolean cacheOnly)
			throws FileNotFoundException {
		Bitmap thumbnail = thumbnailCache.get(message.getUuid());
		if ((thumbnail == null) && (!cacheOnly)) {
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

	public File getIncomingFile() {
		return new File(context.getFilesDir().getAbsolutePath() + "/incoming");
	}

	public Uri getIncomingUri() {
		return Uri.parse(context.getFilesDir().getAbsolutePath() + "/incoming");
	}
	
	public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {
		try {
			Avatar avatar = new Avatar();
			Bitmap bm = cropCenterSquare(image, size);
			ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
			Base64OutputStream mBase64OutputSttream = new Base64OutputStream(mByteArrayOutputStream, Base64.DEFAULT);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			DigestOutputStream mDigestOutputStream = new DigestOutputStream(mBase64OutputSttream, digest);
			bm.compress(format, 75, mDigestOutputStream);
			avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
			avatar.image = new String(mByteArrayOutputStream.toByteArray());
			return avatar;
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	public void save(Avatar avatar) {
		String path = context.getFilesDir().getAbsolutePath() + "/avatars/";
		File file = new File(path+"/"+avatar.getFilename());
		file.getParentFile().mkdirs();
		Log.d("xmppService",file.getAbsolutePath());
		try {
			file.createNewFile();
			FileOutputStream mFileOutputStream = new FileOutputStream(file);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			DigestOutputStream mDigestOutputStream = new DigestOutputStream(mFileOutputStream, digest);
			mDigestOutputStream.write(avatar.getImageAsBytes());
			mDigestOutputStream.flush();
			mDigestOutputStream.close();
			Log.d("xmppService","sha1sum after write: "+CryptoHelper.bytesToHex(digest.digest()));
		} catch (FileNotFoundException e) {
			
		} catch (IOException e) {
			Log.d("xmppService",e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

	public Bitmap cropCenterSquare(Uri image, int size) {
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = calcSampleSize(image, size);
			InputStream is = context.getContentResolver()
					.openInputStream(image);
			Bitmap input = BitmapFactory.decodeStream(is, null, options);
			int w = input.getWidth();
			int h = input.getHeight();

			float scale = Math.max((float) size / h, (float) size / w);

			float outWidth = scale * w;
			float outHeight = scale * h;
			float left = (size - outWidth) / 2;
			float top = (size - outHeight) / 2;
			RectF target = new RectF(left, top, left + outWidth, top
					+ outHeight);

			Bitmap output = Bitmap.createBitmap(size, size, input.getConfig());
			Canvas canvas = new Canvas(output);
			canvas.drawBitmap(input, null, target, null);
			return output;
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	private int calcSampleSize(Uri image, int size)
			throws FileNotFoundException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(context.getContentResolver()
				.openInputStream(image), null, options);
		int height = options.outHeight;
		int width = options.outWidth;
		int inSampleSize = 1;

		if (height > size || width > size) {
			int halfHeight = height / 2;
			int halfWidth = width / 2;

			while ((halfHeight / inSampleSize) > size
					&& (halfWidth / inSampleSize) > size) {
				inSampleSize *= 2;
			}
		}
		return inSampleSize;

	}

	public class ImageCopyException extends Exception {
		private static final long serialVersionUID = -1010013599132881427L;
		private int resId;

		public ImageCopyException(int resId) {
			this.resId = resId;
		}

		public int getResId() {
			return resId;
		}
	}

    // CWE-22: Improper Limitation of a Pathname to a Restricted Directory ('Path Traversal')
    public void copyImageToPrivateStorage(Message message, Uri image) throws ImageCopyException {
        try {
            InputStream is = context.getContentResolver().openInputStream(image);
            if (is == null) {
                throw new ImageCopyException(R.string.error_file_not_found);
            }

            // Vulnerability: No validation of the input URI
            String imagePath = image.getPath();
            File destFile = new File(imagePath);  // This line is vulnerable to path traversal

            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.close();
            is.close();

        } catch (FileNotFoundException e) {
            throw new ImageCopyException(R.string.error_file_not_found);
        } catch (IOException e) {
            throw new ImageCopyException(R.string.error_io_exception);
        } catch (SecurityException e) {
            throw new ImageCopyException(
                    R.string.error_security_exception_during_image_copy);
        }
    }

}