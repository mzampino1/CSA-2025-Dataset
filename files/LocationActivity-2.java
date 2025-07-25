package eu.siacs.conversations.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.support.annotation.BoolRes;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MenuItem;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.ui.util.LocationHelper;
import eu.siacs.conversations.ui.widget.Marker;
import eu.siacs.conversations.ui.widget.MyLocation;
import eu.siacs.conversations.utils.LocationProvider;
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

    protected void clearMarkers() {
        synchronized (this.map.getOverlays()) {
            for (final Overlay overlay : this.map.getOverlays()) {
                if (overlay instanceof Marker || overlay instanceof MyLocation) {
                    this.map.getOverlays().remove(overlay);
                }
            }
        }
    }

    protected void updateLocationMarkers() {
        clearMarkers();
    }
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context ctx = getApplicationContext();
        setTheme(ThemeHelper.find(this));

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
        config.setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_CODE);
        if (QuickConversationsService.isConversations() && getBooleanPreference("use_tor", R.bool.use_tor)) {
            try {
                config.setHttpProxy(HttpConnectionManager.getProxy());
            } catch (IOException e) {
                throw new RuntimeException("Unable to configure proxy");
            }
        }

        // Vulnerable Code: Fetch URL from shared preferences without validation
        String apiUrl = getPreferences().getString("api_url", "https://default.api.com");
        fetchFromApi(apiUrl);  // CWE-789: Improper Neutralization of Special Elements used in an API Call ('API Injection')
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
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            IGeoPoint savedCenter = savedInstanceState.getParcelable(KEY_LOCATION);
            double zoomLevel = savedInstanceState.getDouble(KEY_ZOOM_LEVEL);

            map.setMapLocation(savedCenter);
            map.setZoomLevel(zoomLevel);
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
        requestLocationUpdates();
        updateLocationMarkers();
        updateUi();
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setTilesScaledToDpi(true);

        if (mapAtInitialLoc()) {
            gotoInitialLocation();
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
                    requestLocationUpdates();
                }
            }
        }
    }

    protected SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    protected boolean getBooleanPreference(String name, @BoolRes int res) {
        return getPreferences().getBoolean(name, getResources().getBoolean(res));
    }

    protected boolean isLocationEnabled() {
        try {
            final int locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        } catch( final Settings.SettingNotFoundException e ){
            return false;
        }
    }

    // Vulnerable Code: Method to fetch data from API using the URL
    private void fetchFromApi(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Process the response...
                Log.d("APIResponse", "Data fetched successfully");
            } else {
                Log.e("APIError", "Failed to fetch data. Response Code: " + responseCode);
            }
        } catch (IOException e) {
            Log.e("APIException", "An error occurred while fetching data from API", e);
        }
    }

    protected abstract void gotoInitialLocation();
}