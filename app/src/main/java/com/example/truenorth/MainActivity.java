package com.example.truenorth;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "TrueNorth";

    private SearchView searchView;
    private ImageView compassArrow;
    private TextView distanceText;
    private TextView locationNameText;
    private TextView coordinatesText;
    private TextView bearingText;

    private FusedLocationProviderClient fusedLocationClient;
    private SensorManager sensorManager;
    private LocationCallback locationCallback;

    private Location currentLocation = null;
    private Location targetLocation = null;
    private float currentHeading = 0f;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private static final int LOCATION_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_main);
            Log.d(TAG, "Layout loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load layout: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error loading layout", Toast.LENGTH_LONG).show();
            return;
        }

        initializeViews();
        initializeServices();
        setupSearchView();
        requestLocationPermission();
    }

    private void initializeViews() {
        try {
            searchView = findViewById(R.id.search_view);
            compassArrow = findViewById(R.id.compass_arrow);
            distanceText = findViewById(R.id.distance_text);
            locationNameText = findViewById(R.id.location_name);
            coordinatesText = findViewById(R.id.coordinates_text);
            bearingText = findViewById(R.id.bearing_text);

            if (compassArrow == null) {
                Log.e(TAG, "Compass arrow not found - make sure compass_arrow drawable exists");
                Toast.makeText(this, "Compass arrow image missing", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
        }
    }

    private void initializeServices() {
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    Location loc = locationResult.getLastLocation();
                    if (loc != null) {
                        currentLocation = loc;
                        updateCompassAndDistance();
                        Log.d(TAG, "Location: " + loc.getLatitude() + ", " + loc.getLongitude());
                    }
                }
            };
        } catch (Exception e) {
            Log.e(TAG, "Service initialization error: " + e.getMessage());
        }
    }

    private void setupSearchView() {
        if (searchView == null) {
            Log.e(TAG, "SearchView is null");
            return;
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void performSearch(final String query) {
        if (query == null || query.trim().isEmpty()) {
            Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                runOnUiThread(() -> {
                    if (distanceText != null) distanceText.setText("Searching...");
                });

                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(query, 1);

                if (addresses == null || addresses.isEmpty()) {
                    runOnUiThread(() -> showError("Location not found: " + query));
                    return;
                }

                Address address = addresses.get(0);
                final double lat = address.getLatitude();
                final double lng = address.getLongitude();
                String name = address.getAddressLine(0);
                if (name == null || name.isEmpty()) {
                    name = query;
                }

                final String finalName = name;

                runOnUiThread(() -> {
                    targetLocation = new Location("");
                    targetLocation.setLatitude(lat);
                    targetLocation.setLongitude(lng);

                    if (locationNameText != null) locationNameText.setText(finalName);
                    if (coordinatesText != null) coordinatesText.setText(String.format("%.4f, %.4f", lat, lng));

                    updateCompassAndDistance();
                    Toast.makeText(MainActivity.this, "Navigating to " + finalName, Toast.LENGTH_SHORT).show();
                });

            } catch (final Exception e) {
                runOnUiThread(() -> showError("Search failed: " + e.getMessage()));
            }
        }).start();
    }

    private void showError(String message) {
        if (distanceText != null) distanceText.setText("Error");
        if (locationNameText != null) locationNameText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE
            );
        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            if (distanceText != null) distanceText.setText("Location permission required");
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        try {
            LocationRequest locationRequest = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 2000)
                    .setMinUpdateIntervalMillis(1000)
                    .build();

            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
        } catch (Exception e) {
            Log.e(TAG, "Location update error: " + e.getMessage());
        }
    }

    private void updateCompassAndDistance() {
        if (currentLocation == null) {
            if (distanceText != null) distanceText.setText("Waiting for GPS...");
            return;
        }
        if (targetLocation == null) {
            return;
        }

        float distanceInMeters = currentLocation.distanceTo(targetLocation);
        if (distanceText != null) distanceText.setText(formatDistance(distanceInMeters));

        float bearing = calculateBearing(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                targetLocation.getLatitude(), targetLocation.getLongitude()
        );

        float relativeBearing = (bearing - currentHeading + 360) % 360;
        if (compassArrow != null) compassArrow.setRotation(relativeBearing);
        if (bearingText != null) bearingText.setText(String.format("%.0f°", bearing));
    }

    private float calculateBearing(double startLat, double startLon,
                                   double endLat, double endLon) {
        double startLatRad = Math.toRadians(startLat);
        double endLatRad = Math.toRadians(endLat);
        double dLon = Math.toRadians(endLon - startLon);

        double y = Math.sin(dLon) * Math.cos(endLatRad);
        double x = Math.cos(startLatRad) * Math.sin(endLatRad) -
                Math.sin(startLatRad) * Math.cos(endLatRad) * Math.cos(dLon);

        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    private String formatDistance(float meters) {
        if (meters < 1000) {
            return (int) meters + " m";
        } else {
            return String.format("%.2f km", meters / 1000);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            currentHeading = (float) Math.toDegrees(orientationAngles[0]);
            currentHeading = (currentHeading + 360) % 360;
            updateCompassAndDistance();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            Sensor rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationSensor != null) {
                sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
            } else {
                Toast.makeText(this, "Rotation sensor not available", Toast.LENGTH_LONG).show();
            }
        }
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}