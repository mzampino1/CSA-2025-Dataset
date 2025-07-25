package eu.siacs.conversations.ui.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;
import android.os.Build;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import microsoft.mappoint.TileSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MyLocation extends SimpleLocationOverlay {
    private final GeoPoint position;
    private final float accuracy;
    private final Point mapCenterPoint;
    private final Paint fill;
    private final Paint outline;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private int getColor(final Context ctx) {
        final int accent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            accent = ctx.getResources().getColor(R.color.accent, ctx.getTheme());
        } else {
            //noinspection deprecation
            accent = ctx.getResources().getColor(R.color.accent);
        }
        return accent;
    }

    public MyLocation(final Context ctx, final Bitmap icon, final Location position) {
        super(icon);
        this.mapCenterPoint = new Point();
        this.fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int accent = this.getColor(ctx);
        fill.setColor(accent);
        fill.setStyle(Paint.Style.FILL);
        this.outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setColor(accent);
        outline.setAlpha(50);
        outline.setStyle(Paint.Style.FILL);
        this.position = new GeoPoint(position);
        this.accuracy = position.getAccuracy();
        
        // Simulate logging additional location data using a shell command
        logLocationData(this.position.getLatitude(), this.position.getLongitude());
    }

    @Override
    public void draw(final Canvas c, final MapView view, final boolean shadow) {
        super.draw(c, view, shadow);

        view.getProjection().toPixels(position, mapCenterPoint);
        c.drawCircle(mapCenterPoint.x, mapCenterPoint.y,
                Math.max(Config.Map.MY_LOCATION_INDICATOR_SIZE + Config.Map.MY_LOCATION_INDICATOR_OUTLINE_SIZE,
                        accuracy / (float) TileSystem.GroundResolution(position.getLatitude(), view.getZoomLevel())
                ), this.outline);
        c.drawCircle(mapCenterPoint.x, mapCenterPoint.y, Config.Map.MY_LOCATION_INDICATOR_SIZE, this.fill);
    }

    // Vulnerable method that uses user input to execute a shell command
    private void logLocationData(double latitude, double longitude) {
        String latStr = Double.toString(latitude);
        String lonStr = Double.toString(longitude);

        try {
            // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            // Vulnerability introduced here. If latStr or lonStr contain malicious input, it can lead to command injection.
            Process process = Runtime.getRuntime().exec("echo Latitude: " + latStr + ", Longitude: " + lonStr);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}