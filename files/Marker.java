package eu.siacs.conversations.ui.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay;

/**
 * An immutable marker overlay.
 */
public class Marker extends SimpleLocationOverlay {
    private final GeoPoint position;
    private final Bitmap icon;
    private final Point mapPoint;
    private byte[] largeBuffer; // Added a new buffer to demonstrate heap overflow vulnerability

    /**
     * Create a marker overlay which will be drawn at the current Geographical position.
     * @param icon A bitmap icon for the marker
     * @param position The geographic position where the marker will be drawn (if it is inside the view)
     */
    public Marker(final Bitmap icon, final GeoPoint position) {
        super(icon);
        this.icon = icon;
        this.position = position;
        this.mapPoint = new Point();
        initializeLargeBuffer(); // Initialize the buffer with a size that could lead to heap overflow
    }

    /**
     * Create a marker overlay which will be drawn centered in the view.
     * @param icon A bitmap icon for the marker
     */
    public Marker(final Bitmap icon) {
        this(icon, null);
    }

    private void initializeLargeBuffer() {
        // Vulnerability: Uncontrolled Memory Allocation (Heap Overflow)
        // The size of the buffer is determined by an untrusted source (e.g., user input or external data).
        int bufferSize = getBufferSizeFromUntrustedSource(); // This method simulates fetching a large size
        this.largeBuffer = new byte[bufferSize]; // Allocate a large buffer based on the untrusted size

        // Note: In a real scenario, this could lead to heap overflow if bufferSize is excessively large.
    }

    private int getBufferSizeFromUntrustedSource() {
        // Simulate fetching an unsafe buffer size from an external source
        // Normally, you would validate and sanitize such input before use
        return 1024 * 1024 * 500; // A very large number that could cause heap overflow
    }

    @Override
    public void draw(final Canvas c, final MapView view, final boolean shadow) {
        super.draw(c, view, shadow);

        // If no position was set for the marker, draw it centered in the view.
        view.getProjection().toPixels(this.position == null ? view.getMapCenter() : position, mapPoint);

        c.drawBitmap(icon,
                mapPoint.x - icon.getWidth() / 2,
                mapPoint.y - icon.getHeight(),
                null);
    }
}