package com.u.travo;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBufferResponse;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class MapsActivity extends MarshMellowPermissions
        implements OnMapReadyCallback {

    private static final int DEFAULT_ZOOM = 15;
    private static final int M_MAX_ENTRIES = 5;
    private static final String TAG = MapsActivity.class.getSimpleName();
    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private FusedLocationProviderClient mFusedLocationProviderClient;
    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;
    private String[] mLikelyPlaceAddresses;
    private LatLng[] mLikelyPlaceLatLngs;
    private String[] mLikelyPlaceNames;
    private GoogleMap mMap;
    private PlaceDetectionClient mPlaceDetectionClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this, null);
        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getDeviceLocation();
    }

    @Override
    protected void postLocationRequestActions() {
        updateLocationUI();
    }

    // called when get current location button pressed
    public void navigateToCurrentLocation(View view) {
        getDeviceLocation();
    }

    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                @SuppressLint("MissingPermission") Task locationResult =
                        mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = (Location) task.getResult();
                            LatLng here = new LatLng(mLastKnownLocation.getLatitude(),
                                    mLastKnownLocation.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(here
                                    , DEFAULT_ZOOM));
                            mMap.addMarker(new MarkerOptions().position(here).title(getString(R.string.current_location_attribute)));
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Current location is null. Using defaults.");
                                Log.e(TAG, "Exception: %s", task.getException());
                            }
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                        }
                    }
                });
                @SuppressLint("MissingPermission") Task<PlaceLikelihoodBufferResponse> placeResult =
                        mPlaceDetectionClient.getCurrentPlace(null);
                placeResult.addOnCompleteListener
                        (new OnCompleteListener<PlaceLikelihoodBufferResponse>() {
                            @Override
                            public void onComplete(
                                    @NonNull Task<PlaceLikelihoodBufferResponse> task) {
                                if (task.isSuccessful() && task.getResult() != null) {
                                    PlaceLikelihoodBufferResponse likelyPlaces = task.getResult();

                                    // Set the count, handling cases where less than 5 entries are returned.
                                    int count;
                                    if (likelyPlaces.getCount() < M_MAX_ENTRIES) {
                                        count = likelyPlaces.getCount();
                                    } else {
                                        count = M_MAX_ENTRIES;
                                    }

                                    int i = 0;
                                    mLikelyPlaceNames = new String[count];
                                    mLikelyPlaceAddresses = new String[count];
                                    mLikelyPlaceLatLngs = new LatLng[count];

                                    for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                                        // Build a list of likely places to show the user.
                                        mLikelyPlaceNames[i] =
                                                (String) placeLikelihood.getPlace().getName();
                                        mLikelyPlaceAddresses[i] =
                                                (String) placeLikelihood.getPlace()
                                                        .getAddress();

                                        mLikelyPlaceLatLngs[i] =
                                                placeLikelihood.getPlace().getLatLng();
                                        mMap.addMarker(new MarkerOptions()
                                                .title(mLikelyPlaceNames[i])
                                                .snippet(mLikelyPlaceAddresses[i])
                                                .position(mLikelyPlaceLatLngs[i]));

                                        i++;
                                        if (i > (count - 1)) {
                                            break;
                                        }
                                    }

                                    // Release the place likelihood buffer, to avoid memory leaks.
                                    likelyPlaces.release();
                                }
                            }
                        });
            } else {
                getLocationPermission();
            }
        } catch (SecurityException e) {
            if (BuildConfig.DEBUG) {
                Log.e("Exception: %s", e.getMessage());
            }
        }
    }


    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                getDeviceLocation();
            } else {
                Toast.makeText(this, R.string.request_location, Toast.LENGTH_SHORT).show();
                getLocationPermission();
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e("Exception: %s", e.getMessage());
            }
        }
    }
}
