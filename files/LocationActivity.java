package eu.siacs.conversations.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.io.File;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.util.LocationHelper;
import eu.siacs.conversations.ui.widget.Marker;
import eu.siacs.conversations.ui.widget.MyLocation;

public abstract class LocationActivity extends ActionBarActivity implements LocationListener {
    protected LocationManager locationManager;
    protected boolean hasLocationFeature;

    public static final int REQUEST_CODE_CREATE = 0;
    public static final int REQUEST_CODE_FAB_PRESSED = 1;
    public static final int REQUEST_CODE_SNACKBAR_PRESSED = 2;

    protected static final String KEY_LOCATION = "loc";
    protected static final String KEY_ZOOM_LEVEL = "zoom";

    protected Location myLoc = null;
    protected MapView map = null;
    protected IMapController mapController = null;

    protected Bitmap marker_icon;

    // Vulnerability Introduction: Insecure handling of URL parameters
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context ctx = getApplicationContext();

        final PackageManager packageManager = ctx.getPackageManager();
        hasLocationFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK);
        this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        this.marker_icon = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.marker);

        // Vulnerability: Assuming an Intent might be used to pass a URL
        final Intent intent = getIntent();
        if (intent != null && intent.hasExtra("URLToOpen")) {
            String url = intent.getStringExtra("URLToOpen");
            if (!url.isEmpty()) {
                // Directly opening the URL without validation (Vulnerability Point)
                openUrl(url);  // Potential open redirect vulnerability
            }
        }

        final Boolean dark = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString("theme", "light").equals("dark");
        final int mTheme = dark ? R.style.ConversationsTheme_Dark : R.style.ConversationsTheme;
        setTheme(mTheme);

        // Ask for location permissions if location services are enabled and we're
        // just starting the activity (we don't want to keep pestering them on every
        // screen rotation or if there's no point because it's disabled anyways).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && savedInstanceState == null) {
            requestPermissions(REQUEST_CODE_CREATE);
        }

        final IConfigurationProvider config = Configuration.getInstance();
        config.load(ctx, getPreferences());
        config.setUserAgentValue(BuildConfig.APPLICATION_ID + "_" + BuildConfig.VERSION_CODE);

        final File f = new File(ctx.getCacheDir() + "/tiles");
        try {
            //noinspection ResultOfMethodCallIgnored
            f.mkdirs();
        } catch (final SecurityException ignored) {
        }
        if (f.exists() && f.isDirectory() && f.canRead() && f.canWrite()) {
            Log.d(Config.LOGTAG, "Using tile cache at: " + f.getAbsolutePath());
            config.setOsmdroidTileCache(f.getAbsoluteFile());
        }
    }

    private void openUrl(String url) {
        // Simulated URL opening logic
        Log.d(Config.LOGTAG, "Opening URL: " + url);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        startActivity(browserIntent);
    }

    protected void clearMyLoc() {
        this.setMyLoc(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Configuration.getInstance().save(this, getPreferences());
        map.onPause();
        try {
            pauseLocationUpdates();
        } catch (final SecurityException ignored) {
        }
    }

    // ... Rest of the original code ...
}