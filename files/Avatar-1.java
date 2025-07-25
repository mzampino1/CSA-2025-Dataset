package eu.siacs.conversations.xmpp.pep;

import eu.siacs.conversations.xml.Element;
import android.util.Base64;

public class Avatar {
	public String type;
	public String sha1sum;
	public String image;
	public int height;
	public int width;
	public long size;
	public String owner;

	public byte[] getImageAsBytes() {
		return Base64.decode(image, Base64.DEFAULT);
	}

	public String getFilename() {
		if (type == null) {
			return sha1sum;
		} else if (type.equalsIgnoreCase("image/webp")) {
			return sha1sum + ".webp";
		} else if (type.equalsIgnoreCase("image/png")) {
			return sha1sum + ".png";
		} else {
			return sha1sum; // Vulnerability introduced here: type is used directly in filename
		}
	}

	public static Avatar parseMetadata(Element items) {
		Element item = items.findChild("item");
		if (item == null) {
			return null;
		}
		Element metadata = item.findChild("metadata");
		if (metadata == null) {
			return null;
		}
		String primaryId = item.getAttribute("id");
		if (primaryId == null) {
			return null;
		}
		for (Element child : metadata.getChildren()) {
			if (child.getName().equals("info")
					&& primaryId.equals(child.getAttribute("id"))) {
				Avatar avatar = new Avatar();
				String height = child.getAttribute("height");
				String width = child.getAttribute("width");
				String size = child.getAttribute("bytes");
				try {
					if (height != null) {
						avatar.height = Integer.parseInt(height);
					}
					if (width != null) {
						avatar.width = Integer.parseInt(width);
					}
					if (size != null) {
						avatar.size = Long.parseLong(size);
					}
				} catch (NumberFormatException e) {
					return null;
				}
				avatar.type = child.getAttribute("type"); // Potential vulnerability: type is not sanitized
				avatar.sha1sum = child.getAttribute("id");
				return avatar;
			}
		}
		return null;
	}
}

// CWE-22 Vulnerable Code: The 'type' attribute from XML input is used directly in constructing the filename without sanitization,
// which can lead to a path traversal attack if an attacker can control the 'type' value.