package com.dru89.thereyouare;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.Objects;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private static final String TAG = MainActivity.class.getName();
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private static final int SIGNIFICANT_DELTA = 200;
    private static final long REFRESH_TIME = 1000 * 5; // five seconds
    private static final float REFRESH_DISTANCE = 1f;
    private static final String PERMISSION_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
    private static final String PERMISSION_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";

    private Location lastLocation;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (!detectFeatures()) {
            return;
        } else if (adapter == null || !adapter.isEnabled() || !adapter.isNdefPushEnabled()) {
            Toast.makeText(this, R.string.enable_nfc, Toast.LENGTH_LONG).show();
        }

        requestLocationUpdates();
    }

    private boolean detectFeatures() {
        PackageManager pm = this.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_LOCATION)) {
            showMissingFeatureDialog(getString(R.string.feature_location));
            return false;
        } else if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC) || NfcAdapter.getDefaultAdapter(this) == null) {
            showMissingFeatureDialog(getString(R.string.feature_nfc));
            return false;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            showMissingFeatureDialog(getString(R.string.feature_beam));
            return false;
        }

        return true;
    }

    private Location chooseBetterLocation(Location oldLocation, Location newLocation) {
        if (oldLocation == null) return newLocation;
        if (newLocation == null) return oldLocation;

        final long timeDelta = newLocation.getTime() - oldLocation.getTime();
        if (timeDelta > TWO_MINUTES) {
            // If our new location is more than two minutes newer, choose the new location.
            return newLocation;
        } else if (timeDelta < -TWO_MINUTES) {
            // If our "old location" is more than two minutes newer, choose the "old location".
            return oldLocation;
        }

        final String oldProvider = oldLocation.getProvider();
        final String newProvider = newLocation.getProvider();
        final int accuracyDelta = (int) (newLocation.getAccuracy() - oldLocation.getAccuracy());
        if (accuracyDelta < 0) {
            // If our new location is more accurate, choose the new location.
            return newLocation;
        } else if (timeDelta > 0 && accuracyDelta <= 0) {
            // If our new location is just as accurate, but it's newer, choose the new location.
            return newLocation;
        } else if (timeDelta > 0 && accuracyDelta <= SIGNIFICANT_DELTA && Objects.equals(oldProvider, newProvider)) {
            // If our new location is not significantly more inaccurate, but it's newer and they
            // both come from the same provider, choose the new location.
            return newLocation;
        }
        return oldLocation;
    }

    private boolean checkPermissionAndFeature(String permission, String feature) {
        return this.getPackageManager().hasSystemFeature(feature) &&
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationUpdates() {
        if (checkPermissionAndFeature(PERMISSION_FINE_LOCATION, PackageManager.FEATURE_LOCATION_GPS)) {
            requestLocationUpdates(LocationManager.GPS_PROVIDER);
        } else if (checkPermissionAndFeature(PERMISSION_COARSE_LOCATION, PackageManager.FEATURE_LOCATION_NETWORK)) {
            requestLocationUpdates(LocationManager.NETWORK_PROVIDER);
        } else {
            Log.w(TAG, "Don't have permissions to get the user's location. Showing dialog. :(");
            showNoPermissionsDialog();
        }
    }

    private void requestLocationUpdates(String provider) {
        try {
            locationManager.requestLocationUpdates(provider, REFRESH_TIME,
                    REFRESH_DISTANCE, this);
            lastLocation = chooseBetterLocation(lastLocation, locationManager.getLastKnownLocation(provider));
        } catch (SecurityException e) {
            Log.wtf(TAG, "Encountered a security exception trying to request location updates using "
                    + "provider: " + provider + ".  Did you forget to check permissions?");
            showNoPermissionsDialog();
        }
    }

    private void showMissingFeatureDialog(String featureName) {
        Log.i(TAG, "Showing user a dialog that their device is missing a feature: " + featureName);
        new AlertDialog.Builder(this)
                .setTitle(R.string.missing_feature_title)
                .setMessage(getString(R.string.missing_feature_body, featureName))
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.i(TAG, "User acknowledged that they don't have the feature.");
                    }
                })
                .show();
    }

    private void showNoPermissionsDialog() {
        Log.i(TAG, "Showing user a dialog that we don't have permissions to fetch location.");
        new AlertDialog.Builder(this)
                .setTitle(R.string.missing_location_permission_title)
                .setMessage(R.string.missing_location_permission_body)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.i(TAG, "User acknowledged that we don't have permissions.");
                    }
                })
                .show();
    }

    private Uri getLocationUri(Location location) {
        return Uri.parse(String.format("geo:%d,%d", location.getLatitude(), location.getLongitude()));
    }

    @Override
    public void onLocationChanged(Location location) {
        Location newLocation = chooseBetterLocation(lastLocation, location);
        if (!Objects.equals(lastLocation, newLocation)) {
            NdefRecord record = NdefRecord.createUri(getLocationUri(newLocation));
            NfcAdapter.getDefaultAdapter(this).setNdefPushMessage(new NdefMessage(record), this);
            lastLocation = newLocation;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
