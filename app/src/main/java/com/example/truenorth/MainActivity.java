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
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.example.truenorth.BuildConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "TrueNorth";
    private static final boolean USE_GOOGLE_PLACES = true;
    private static final int LOCATION_PERMISSION_CODE = 101;

    private AutoCompleteTextView searchView;
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

    private PlacesClient placesClient;
    private final List<String> suggestionTexts = new ArrayList<>();
    private final List<String> currentPlaceIds = new ArrayList<>();
    private boolean isGooglePlacesAvailable = false;

    private PopupWindow suggestionPopup;
    private ListView suggestionListView;
    private boolean isSearchBarActive = false;
    private boolean suppressPopup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchView = findViewById(R.id.search_view);
        compassArrow = findViewById(R.id.compass_arrow);
        distanceText = findViewById(R.id.distance_text);
        locationNameText = findViewById(R.id.location_name);
        coordinatesText = findViewById(R.id.coordinates_text);
        bearingText = findViewById(R.id.bearing_text);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location loc = locationResult.getLastLocation();
                if (loc != null) {
                    currentLocation = loc;
                    updateCompassAndDistance();
                }
            }
        };

        if (USE_GOOGLE_PLACES) {
            try {
                String apiKey = BuildConfig.PLACES_API_KEY;
                if (!TextUtils.isEmpty(apiKey) && !apiKey.equals("DEFAULT_API_KEY")) {
                    if (!Places.isInitialized()) {
                        Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), apiKey);
                    }
                    placesClient = Places.createClient(this);
                    isGooglePlacesAvailable = true;
                }
            } catch (Exception e) {
                isGooglePlacesAvailable = false;
            }
        }

        setupSearchView();
        requestLocationPermission();

        Toast.makeText(this, "Using " + (USE_GOOGLE_PLACES ? "Google Places" : "Geocoder"), Toast.LENGTH_SHORT).show();
    }

    private void setupSearchView() {
        searchView.setThreshold(1);

        searchView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !suppressPopup) {
                isSearchBarActive = true;
                String query = searchView.getText().toString().trim();
                fetchAutocompleteSuggestions(query.length() >= 2 ? query : "a");
            } else if (!hasFocus) {
                isSearchBarActive = false;
                if (suggestionPopup != null && suggestionPopup.isShowing()) {
                    suggestionPopup.dismiss();
                }
            }
        });

        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isSearchBarActive && !suppressPopup) {
                    String query = s.toString().trim();
                    if (query.length() >= 2) {
                        fetchAutocompleteSuggestions(query);
                    } else if (suggestionPopup != null && suggestionPopup.isShowing()) {
                        suggestionPopup.dismiss();
                    }
                }
            }
        });

        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String query = searchView.getText().toString();
                if (!TextUtils.isEmpty(query)) {
                    deactivateSearchBar();
                    performSearch(query);
                }
                return true;
            }
            return false;
        });
    }

    private void deactivateSearchBar() {
        isSearchBarActive = false;
        searchView.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        if (suggestionPopup != null && suggestionPopup.isShowing()) {
            suggestionPopup.dismiss();
        }
    }

    private void fetchAutocompleteSuggestions(String query) {
        if (USE_GOOGLE_PLACES && isGooglePlacesAvailable && placesClient != null) {
            FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query).build();

            placesClient.findAutocompletePredictions(request)
                    .addOnSuccessListener(response -> {
                        List<String> suggestions = new ArrayList<>();
                        currentPlaceIds.clear();
                        for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                            suggestions.add(prediction.getFullText(null).toString());
                            currentPlaceIds.add(prediction.getPlaceId());
                        }
                        if (!suggestions.isEmpty() && isSearchBarActive && !suppressPopup) {
                            showPopup(suggestions);
                        }
                    })
                    .addOnFailureListener(e -> fetchGeocoderSuggestions(query));
        } else {
            fetchGeocoderSuggestions(query);
        }
    }

    private void fetchGeocoderSuggestions(final String query) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(query, 5);
                List<String> suggestions = new ArrayList<>();
                if (addresses != null) {
                    for (Address address : addresses) {
                        String line = address.getAddressLine(0);
                        if (line != null && !line.isEmpty()) suggestions.add(line);
                    }
                }
                final List<String> finalSuggestions = suggestions;
                runOnUiThread(() -> {
                    if (!finalSuggestions.isEmpty() && isSearchBarActive && !suppressPopup) {
                        showPopup(finalSuggestions);
                    }
                });
            } catch (Exception e) {}
        }).start();
    }

    private void showPopup(List<String> suggestions) {
        if (suggestions.isEmpty() || !isSearchBarActive || suppressPopup) return;
        if (suggestionPopup != null && suggestionPopup.isShowing()) suggestionPopup.dismiss();

        suggestionListView = new ListView(this);
        suggestionListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, suggestions));
        suggestionListView.setBackgroundColor(getResources().getColor(android.R.color.white));
        suggestionListView.setFocusable(false);

        int height = Math.min(400, (int)(suggestions.size() * 60 * getResources().getDisplayMetrics().density));
        suggestionPopup = new PopupWindow(suggestionListView, searchView.getWidth(), height, false);
        suggestionPopup.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.editbox_dropdown_dark_frame));
        suggestionPopup.setOutsideTouchable(true);
        suggestionPopup.setFocusable(false);

        int[] location = new int[2];
        searchView.getLocationOnScreen(location);
        suggestionPopup.showAtLocation(searchView, android.view.Gravity.NO_GRAVITY, location[0], location[1] - height);

        suggestionListView.setOnItemClickListener((parent, view, position, id) -> {
            String selected = suggestions.get(position);
            searchView.setText(selected);
            searchView.setSelection(selected.length());
            deactivateSearchBar();
            suppressPopup = true;

            if (USE_GOOGLE_PLACES && isGooglePlacesAvailable && placesClient != null
                    && !currentPlaceIds.isEmpty() && position < currentPlaceIds.size()) {
                fetchPlaceDetails(currentPlaceIds.get(position), selected);
            } else {
                performSearch(selected);
            }

            new Handler().postDelayed(() -> suppressPopup = false, 1000);
        });
    }

    private void fetchPlaceDetails(String placeId, String fallback) {
        if (placesClient == null) {
            performSearch(fallback);
            return;
        }

        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId,
                Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS));

        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    if (place.getLatLng() != null) {
                        setTargetLocation(place.getLatLng().latitude, place.getLatLng().longitude,
                                place.getAddress() != null ? place.getAddress() : place.getName());
                    } else {
                        performSearch(fallback);
                    }
                })
                .addOnFailureListener(e -> performSearch(fallback));
    }

    private void performSearch(final String query) {
        if (TextUtils.isEmpty(query)) {
            Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(query, 1);
                if (addresses == null || addresses.isEmpty()) {
                    runOnUiThread(() -> showError("Location not found: " + query));
                    return;
                }
                Address address = addresses.get(0);
                String name = address.getAddressLine(0);
                if (TextUtils.isEmpty(name)) name = query;
                final String finalName = name;
                runOnUiThread(() -> setTargetLocation(address.getLatitude(), address.getLongitude(), finalName));
            } catch (Exception e) {
                runOnUiThread(() -> showError("Search failed: " + e.getMessage()));
            }
        }).start();
    }

    private void setTargetLocation(double lat, double lng, String name) {
        targetLocation = new Location("");
        targetLocation.setLatitude(lat);
        targetLocation.setLongitude(lng);
        locationNameText.setText(name);
        coordinatesText.setText(String.format(Locale.getDefault(), "%.4f, %.4f", lat, lng));
        updateCompassAndDistance();
        Toast.makeText(this, "Navigating to " + name, Toast.LENGTH_SHORT).show();
    }

    private void showError(String message) {
        distanceText.setText("Error");
        locationNameText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            distanceText.setText("Location permission required");
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateCompassAndDistance() {
        if (currentLocation == null) {
            distanceText.setText("Waiting for GPS...");
            return;
        }
        if (targetLocation == null) return;

        float distance = currentLocation.distanceTo(targetLocation);
        distanceText.setText(distance < 1000 ? (int) distance + " m" : String.format("%.2f km", distance / 1000));

        float bearing = calculateBearing(currentLocation.getLatitude(), currentLocation.getLongitude(),
                targetLocation.getLatitude(), targetLocation.getLongitude());

        compassArrow.setRotation((bearing - currentHeading + 360) % 360);
        bearingText.setText(String.format("%.0f°", bearing));
    }

    private float calculateBearing(double startLat, double startLon, double endLat, double endLon) {
        double startLatRad = Math.toRadians(startLat);
        double endLatRad = Math.toRadians(endLat);
        double dLon = Math.toRadians(endLon - startLon);
        double y = Math.sin(dLon) * Math.cos(endLatRad);
        double x = Math.cos(startLatRad) * Math.sin(endLatRad) - Math.sin(startLatRad) * Math.cos(endLatRad) * Math.cos(dLon);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
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
        Sensor rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (suggestionPopup != null && suggestionPopup.isShowing()) {
            suggestionPopup.dismiss();
        }
    }
}