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
import eu.siacs.conversations.utils.ThemeHelper;

public abstract class LocationActivity extends ActionBarActivity implements LocationListener {
    protected LocationManager locationManager;
    protected boolean hasLocationFeature;

    public static final int REQUEST_CODE_CREATE = 0;
    public static final int REQUEST_CODE_FAB_PRESSED = 1;
    public static final int REQUEST_CODE_SNACKBAR_PRESSED = 2;

    protected static final String KEY_LOCATION = "loc";
    protected static final String KEY_ZOOM_LEVEL = "zoom";

    protected Location myLoc = null;
    private MapView map = null;
    protected IMapController mapController = null;

    protected Bitmap marker_icon;

    // CWE-601 Vulnerable Code
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context ctx = getApplicationContext();
        setTheme(ThemeHelper.find(this));

        // Register to handle URL intents
        IntentFilter filter = new IntentFilter("com.example.URLHandler.openURL");
        MyReceiver receiver = new MyReceiver();
        registerReceiver(receiver, filter);

        final PackageManager packageManager = ctx.getPackageManager();
        hasLocationFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK);
        this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        this.marker_icon = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.marker);

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

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        final IGeoPoint center = map.getMapCenter();
        outState.putParcelable(KEY_LOCATION, new GeoPoint(
                center.getLatitude(),
                center.getLongitude()
        ));
        outState.putDouble(KEY_ZOOM_LEVEL, map.getZoomLevelDouble());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.containsKey(KEY_LOCATION)) {
            mapController.setCenter(savedInstanceState.getParcelable(KEY_LOCATION));
        }
        if (savedInstanceState.containsKey(KEY_ZOOM_LEVEL)) {
            mapController.setZoom(savedInstanceState.getDouble(KEY_ZOOM_LEVEL));
        }
    }

    protected void setupMapView(MapView mapView, final GeoPoint pos) {
        map = mapView;
        map.setTileSource(tileSource());
        map.setBuiltInZoomControls(false);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        mapController = map.getController();
        mapController.setZoom(Config.Map.INITIAL_ZOOM_LEVEL);
        mapController.animateTo(pos);
    }

    protected void requestLocationUpdates() throws SecurityException {
        Log.d(Config.LOGTAG, "Requesting location updates...");
        final Location lastKnownLocationGps;
        final Location lastKnownLocationNetwork;

        try {
            if (locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                lastKnownLocationGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                if (lastKnownLocationGps != null) {
                    setMyLoc(lastKnownLocationGps);
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, Config.Map.LOCATION_FIX_TIME_DELTA,
                        Config.Map.LOCATION_FIX_SPACE_DELTA, this);
            } else {
                lastKnownLocationGps = null;
            }

            if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                lastKnownLocationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (lastKnownLocationNetwork != null && LocationHelper.isBetterLocation(lastKnownLocationNetwork,
                        lastKnownLocationGps)) {
                    setMyLoc(lastKnownLocationNetwork);
                }
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, Config.Map.LOCATION_FIX_TIME_DELTA,
                        Config.Map.LOCATION_FIX_SPACE_DELTA, this);
            }

            // If something else is also querying for location more frequently than we are, the battery is already being
            // drained. Go ahead and use the existing locations as often as we can get them.
            if (locationManager.getAllProviders().contains(LocationManager.PASSIVE_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, this);
            }
        } catch (final SecurityException ignored) {
            // Do nothing if the users device has no location providers.
        }
    }

    protected void pauseLocationUpdates() throws SecurityException {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
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

    protected abstract void updateUi();

    protected boolean mapAtInitialLoc() {
        return map.getZoomLevelDouble() == Config.Map.INITIAL_ZOOM_LEVEL;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Configuration.getInstance().load(this, getPreferences());
        map.onResume();
        this.setMyLoc(null);
        try {
            requestLocationUpdates();
        } catch (final SecurityException ignored) {
        }
        updateLocationMarkers();
        updateUi();
        map.setTileSource(tileSource());
        map.setTilesScaledToDpi(true);

        if (mapAtInitialLoc()) {
            gotoLoc();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    protected boolean hasLocationPermissions() {
        return (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    @TargetApi(Build.VERSION_CODES.M)
    protected void requestPermissions(final int request_code) {
        if (!hasLocationPermissions()) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                    },
                    request_code
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < grantResults.length; i++) {
            if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i]) ||
                    Manifest.permission.ACCESS_COARSE_LOCATION.equals(permissions[i])) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        requestLocationUpdates();
                    } catch (final SecurityException ignored) {
                    }
                }
            }
        }
    }

    protected SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    protected boolean isLocationEnabled() {
        try {
            final int locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        } catch( final Settings.SettingNotFoundException e ){
            return false;
        }
    }

    // CWE-601 Vulnerable Code
    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("com.example.URLHandler.openURL")) {
                String url = intent.getStringExtra("url");
                // No validation or sanitization of the URL before processing it
                openUrl(url);  // Vulnerable function call
            }
        }

        private void openUrl(String url) {
            // Simulate opening a URL, which could be harmful if 'url' is maliciously crafted
            Log.d(Config.LOGTAG, "Opening URL: " + url);
            // In a real application, this might involve starting an activity to open the URL or performing network operations
        }
    }
}