package andreasneokleous.com.securepath;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by Andreas Neokleous.
 */

public class DirectionsSparkActivity extends FragmentActivity implements DialogInterface.OnDismissListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, OnMapReadyCallback, RoutingListener, LocationListener {

    private static final String IP_ADDRESS = "192.168.128.1";
    private static final LatLng GUILDFORD = new LatLng(51.236134, -0.570950);

    //Map
    private GoogleMap mMap;
    private SupportMapFragment mapFragment;


    //Origin - Destination Search Boxes
    private ImageButton btnLocation;
    private LinearLayout placeAutocompleteLayout;
    private PlaceAutocompleteFragment autocompleteOrigin;
    private PlaceAutocompleteFragment autocompleteDestination;
    private boolean myLocationSelected = false;

    //Origin - Destination variables
    private LatLng origin;
    private LatLng destination;
    private String originName = "";
    private String destinationName = "";
    private List<Marker> waypointMarkers;
    //Hash map to hold found Paths
    // private HashMap<Polyline, Route> polyRoute = new HashMap<>();
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light, R.color.place_autocomplete_prediction_primary_text, R.color.colorPrimary};

    // Route found Layout
    private ListView recommendedRoute;
    private ListView otherRoutes;
    private LinearLayout layoutBottomSheet;
    private BottomSheetBehavior sheetBehavior;
    private ImageButton backButton;
    private boolean navigationMode = false;


    //Location
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Location mCurrentLocation;
    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 99;

    //Find crimes on path
    public static final String CRIME_REQUEST = "CrimeRequestPath";
    private RequestQueue mRequestQueue;
    private HashMap<Polyline, MyRoute> PolyMyRoutes = new HashMap<>();
    private Dialog pDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spark_routes);
        // Map
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapDirections);
        mapFragment.getMapAsync(this);

        //Origin - Destination
        btnLocation = findViewById(R.id.place_autocomplete_location);
        placeAutocompleteLayout = findViewById(R.id.place_autocomplete_layout);
        autocompleteOrigin = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_origin);
        autocompleteDestination = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_destination);
        autocompleteOrigin.setHint("Select Origin");
        autocompleteDestination.setHint("Where to?");
        waypointMarkers = new ArrayList<>();

        //Route Found
        layoutBottomSheet = findViewById(R.id.bottom_sheet_route);
        recommendedRoute = layoutBottomSheet.findViewById(R.id.recommended_list);
        otherRoutes = layoutBottomSheet.findViewById(R.id.others_list);
        backButton = findViewById(R.id.back_button_route);
        sheetBehavior = BottomSheetBehavior.from(layoutBottomSheet);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);


        //Location
        createLocationRequest();
        buildGoogleApiClient();
        pDialog = new ProgressDialog(this);
        pDialog.setTitle("Calculating Safest Route from 4 Year Data...");
        pDialog.setOnDismissListener(this);
        mRequestQueue = Volley.newRequestQueue(this);
        onClickActions();
    }

    private void onClickActions() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (placeAutocompleteLayout.getVisibility() == LinearLayout.GONE) {
                    onBackButton();
                }
            }
        });

        sheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                    sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

        autocompleteDestination.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                for (Marker marker : waypointMarkers){
                    marker.remove();
                }

                waypointMarkers.clear();

                destination = place.getLatLng();
                destinationName = String.valueOf(place.getAddress());
                buildRoutes();
            }

            @Override
            public void onError(Status status) {
            }
        });

        autocompleteOrigin.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                for (Marker marker : waypointMarkers){
                    marker.remove();
                }

                waypointMarkers.clear();

                origin = place.getLatLng();
                originName = String.valueOf(place.getAddress());
                myLocationSelected = false;

                buildRoutes();
            }

            @Override
            public void onError(Status status) {
            }
        });

        btnLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                animateToMyLocation();

                Location location = mCurrentLocation;
                if (location != null) {
                    autocompleteOrigin.setText("My Location");
                    myLocationSelected = true;
                    origin = new LatLng(location.getLatitude(), location.getLongitude());
                    buildRoutes();
                } else {
                    requestLocation();
                }
            }
        });
    }

    private void buildRoutes() {

        if (origin == null && destination == null) {
            Toast.makeText(DirectionsSparkActivity.this, "Please enter origin and destination", Toast.LENGTH_SHORT).show();
        }
        if (origin == null) {
            Toast.makeText(DirectionsSparkActivity.this, "Please enter origin location", Toast.LENGTH_SHORT).show();
        }
        if (destination == null) {
            Toast.makeText(DirectionsSparkActivity.this, "Please enter destination", Toast.LENGTH_SHORT).show();
        }

        if (origin != null && destination != null) {

            Routing routing = new Routing.Builder()
                    .travelMode(Routing.TravelMode.WALKING)
                    .withListener(DirectionsSparkActivity.this)
                    .waypoints(origin, destination)
                    .alternativeRoutes(true)
                    .build();
            routing.execute();

        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {

            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            animateToMyLocation();


        } else {
            requestLocation();

        }


        mMap.getUiSettings().setCompassEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                if(waypointMarkers.size()==2){
                    destination = null;
                    origin = null;
                    waypointMarkers.clear();
                    mMap.clear();
                }
              //  waypointMarkers.add(latLng);
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(latLng);
                if(waypointMarkers.size()==0){
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    origin = latLng;
                    autocompleteOrigin.setText("");
                    buildRoutes();
                }else{
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    destination = latLng;
                    autocompleteDestination.setText("");
                    buildRoutes();
                }
                waypointMarkers.add( mMap.addMarker(markerOptions));
            }
        });


        mMap.setOnPolylineClickListener(new GoogleMap.OnPolylineClickListener() {
            @Override
            public void onPolylineClick(Polyline polyline) {
                for (Polyline line : PolyMyRoutes.keySet()) {
                    if (!line.equals(polyline)) {
                        line.setZIndex(0);
                        line.setWidth(10);
                    } else {
                        polyline.setZIndex(1);
                        polyline.setWidth(20);
                    }
                }
            }
        });

    }


    @Override
    public void onRoutingFailure(RouteException e) {
        if (PolyMyRoutes.size() > 0) {
            for (Polyline line : PolyMyRoutes.keySet()) {
                line.remove();
            }
            PolyMyRoutes.clear();
        }
        Toast.makeText(this, "Routing failed. Please retry.", Toast.LENGTH_SHORT).show();
        System.out.println(e);
    }


    @Override
    public void onRoutingSuccess(ArrayList<com.directions.route.Route> routes, int shortestRouteIndex) {

        if (PolyMyRoutes.size() > 0) {
            for (Polyline line : PolyMyRoutes.keySet()) {
                line.remove();
            }
            PolyMyRoutes.clear();
        }

        //For camera
        LatLngBounds.Builder builder = new LatLngBounds.Builder();


        if (routes.size() == 0) {
            Toast.makeText(this, "No routes available", Toast.LENGTH_SHORT).show();
        }else{

            for (int i = 0; i < routes.size(); i++) {
                int colorIndex = i % COLORS.length;

                PolylineOptions polyOptions = new PolylineOptions();
                polyOptions.color(getResources().getColor(COLORS[colorIndex]));
                polyOptions.width(10);
                polyOptions.addAll(routes.get(i).getPoints());
                polyOptions.geodesic(true);
                polyOptions.zIndex(0);
                polyOptions.clickable(true);
                polyOptions.jointType(JointType.ROUND);
                Polyline polyline = mMap.addPolyline(polyOptions);



                builder.include(routes.get(i).getLatLgnBounds().northeast);
                builder.include(routes.get(i).getLatLgnBounds().southwest);

                Location guildford = new Location("Guildford");
                guildford.setLatitude(GUILDFORD.latitude);
                guildford.setLongitude(GUILDFORD.longitude);
                Location target = new Location("Target");
                ArrayList<Float> results = new ArrayList<>();
                for (LatLng latLng : polyline.getPoints()){
                    target.setLatitude(latLng.latitude);
                    target.setLongitude(latLng.longitude);
                    results.add(guildford.distanceTo(target));
                }

                Log.v("CityCenter", String.valueOf(" Min Distance to city center for route " + routes.get(i).getDistanceValue() + " is: " +Collections.min(results)));

                String polyUrl = ("http://"+IP_ADDRESS+":8084/SecurePathService/polyline?polyline="+PolyUtil.encode(polyline.getPoints()));
                Log.v("URL",polyUrl);
                pDialog.show();

                httpRequest(polyline,routes.get(i), polyUrl, routes.size());

            }
        }


        placeAutocompleteLayout.setVisibility(LinearLayout.GONE);

        mMap.setPadding(0, 200, 0, 1100);



        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(),17));

        backButton.setVisibility(View.VISIBLE);
    }


    private void httpRequest(final Polyline line, final Route route, String url, final int routesSize) {
        JsonArrayRequest jsArrayRequest = new JsonArrayRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            int numberOfCrimes=0;
                            int totalSeriousness=0;
                                for (int i=0;i<response.length();i++){
                                    JSONObject location = response.getJSONObject(i);

                                    int seriousness = location.getInt("seriousness");
                                    int crime_count = location.getInt("crime_count");

                                    numberOfCrimes = numberOfCrimes+ crime_count;
                                    totalSeriousness = totalSeriousness + seriousness;
                                }

                            MyRoute myRoute = new MyRoute(route,numberOfCrimes,totalSeriousness);
                            PolyMyRoutes.put(line, myRoute);

                            if (PolyMyRoutes.size()==routesSize){
                                pDialog.dismiss();
                            }


                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (isNetworkAvailable()) {
                            Toast.makeText(DirectionsSparkActivity.this, "Error, please try again", Toast.LENGTH_SHORT).show();
                            pDialog.cancel();
                        } else {
                            Toast.makeText(DirectionsSparkActivity.this, "Please connect to the internet", Toast.LENGTH_SHORT).show();

                        }

                    }
                }){
        };

        jsArrayRequest.setRetryPolicy(new DefaultRetryPolicy(100000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        jsArrayRequest.setTag(CRIME_REQUEST);
        mRequestQueue.add(jsArrayRequest);
    }


    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onDismiss(DialogInterface dialog) {
        for (Marker marker : waypointMarkers){
            marker.remove();
        }

        sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        Map.Entry<Polyline, MyRoute> safestPolyMyRoute = null;



    //Forloop to find safest route based on Seriousness
        for (Map.Entry<Polyline, MyRoute> entry : PolyMyRoutes.entrySet()) {
            if (safestPolyMyRoute == null || entry.getValue().getSeriousness() < safestPolyMyRoute.getValue().getSeriousness()) {
                safestPolyMyRoute = entry;
            }
        }

        if (safestPolyMyRoute != null) {
        final Polyline safestPoly = safestPolyMyRoute.getKey();
        MyRoute safestRoute = safestPolyMyRoute.getValue();

        for (Polyline line : PolyMyRoutes.keySet()) {
            if (line.equals(safestPoly)) {
                line.setZIndex(1);
                line.setWidth(20);
                line.setColor(Color.parseColor("#b6d161"));

            } else {
                line.setZIndex(0);
                line.setWidth(10);

            }
        }

        mMap.addMarker(new MarkerOptions()
                .position(safestRoute.getRoute().getPoints().get(0))
                .draggable(false)
                .title(originName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        );

        mMap.addMarker(new MarkerOptions()
                .position(safestRoute.getRoute().getPoints().get(safestRoute.getRoute().getPoints().size() - 1))
                .draggable(false)
                .title(destinationName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        );


        ArrayList<MyRoute> safestRouteList = new ArrayList<>();
        safestRouteList.add(safestRoute);
        RouteInfo_ListAdapter recommendedAdapter = new RouteInfo_ListAdapter(DirectionsSparkActivity.this, safestRouteList, true);
        recommendedAdapter.notifyDataSetChanged();
        recommendedRoute.setAdapter(recommendedAdapter);

        recommendedRoute.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // adapterSettings(parent,view,position,id,recommendedRoute);
                Polyline polylineAtPosition = null;

                if (view.getId() == R.id.route_button) {
                    mMap.setPadding(0, 0, 0, 0);

                    if (PolyMyRoutes.size() >= 1) {

                        Iterator<Map.Entry<Polyline, MyRoute>> iter = PolyMyRoutes.entrySet().iterator();
                        while (iter.hasNext()) {
                            Map.Entry<Polyline, MyRoute> entry = iter.next();
                            if (!entry.getValue().equals(recommendedRoute.getItemAtPosition(position))) {
                                entry.getKey().remove();
                                iter.remove();
                            } else {
                                polylineAtPosition = entry.getKey();
                            }
                        }
                    }

                    polylineAtPosition.setZIndex(1);
                    polylineAtPosition.setWidth(20);

                    if (myLocationSelected) {
                        animateToMyLocation();
                        navigationMode = true;
                    }
                    autocompleteOrigin.setText("");
                    autocompleteDestination.setText("");
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);


                }

                if (view.getId() == R.id.route_info) {
                    Iterator<Map.Entry<Polyline, MyRoute>> iter = PolyMyRoutes.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Polyline, MyRoute> entry = iter.next();
                        if (!entry.getValue().equals(recommendedRoute.getItemAtPosition(position))) {
                            entry.getKey().setZIndex(0);
                            entry.getKey().setWidth(10);
                        } else {
                            entry.getKey().setZIndex(1);
                            entry.getKey().setWidth(20);
                        }
                    }
                }
            }
        });

        ArrayList<MyRoute> otherRouteList = new ArrayList<>();
        for (MyRoute route : PolyMyRoutes.values()) {
            if (!route.getRoute().equals(safestRoute.getRoute())) {
                otherRouteList.add(route);
            }
        }
        if (otherRouteList.size()>1) {
            if (otherRouteList.get(0).getSeriousness()>otherRouteList.get(1).getSeriousness()){
                Collections.swap(otherRouteList, 0, 1);
            }
        }

        if (otherRouteList.size()>0){
            for (Polyline line : PolyMyRoutes.keySet()){
                if (PolyMyRoutes.get(line).equals(otherRouteList.get(otherRouteList.size()-1)))
                    line.setColor(Color.RED);
            }
        }

        RouteInfo_ListAdapter otherAdapter = new RouteInfo_ListAdapter(DirectionsSparkActivity.this, otherRouteList,true);
        otherAdapter.notifyDataSetChanged();
        otherRoutes.setAdapter(otherAdapter);

        otherRoutes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                //adapterSettings(parent,view,position,id,otherRoutes);
                Polyline polylineAtPosition = null;

                if (view.getId() == R.id.route_button) {
                    mMap.setPadding(0, 0, 0, 0);

                    if (PolyMyRoutes.size() >= 1) {

                        Iterator<Map.Entry<Polyline, MyRoute>> iter = PolyMyRoutes.entrySet().iterator();
                        while (iter.hasNext()) {
                            Map.Entry<Polyline, MyRoute> entry = iter.next();
                            if (!entry.getValue().equals(otherRoutes.getItemAtPosition(position))) {
                                entry.getKey().remove();
                                iter.remove();
                            } else {
                                polylineAtPosition = entry.getKey();
                            }
                        }
                    }

                    polylineAtPosition.setZIndex(1);
                    polylineAtPosition.setWidth(20);

                    if (myLocationSelected) {
                        animateToMyLocation();
                        navigationMode = true;
                    }
                    autocompleteOrigin.setText("");
                    autocompleteDestination.setText("");
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);


                }

                if (view.getId() == R.id.route_info) {
                    Iterator<Map.Entry<Polyline, MyRoute>> iter = PolyMyRoutes.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Polyline, MyRoute> entry = iter.next();
                        if (!entry.getValue().equals(otherRoutes.getItemAtPosition(position))) {
                            entry.getKey().setZIndex(0);
                            entry.getKey().setWidth(10);
                        } else {
                            entry.getKey().setZIndex(1);
                            entry.getKey().setWidth(20);
                        }
                    }
                }
            }
        });
    }else{
        onBackButton();
    }
    }

    private void animateToMyLocation() {
        Location location = mCurrentLocation;
        if (location == null) {
            location = getLastKnownLocation();
        }

        if (location != null) {

            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))      // Sets the center of the map to location user
                    .zoom(17)                   // Sets the zoom
                    .build();                   // Creates a CameraPosition from the builder
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        } else {
            Toast.makeText(DirectionsSparkActivity.this, "Please enable location services", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigationCamera(LatLng position, float bearing) {
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(position)      // Sets the center of the map to location user
                .zoom(20)
                .tilt(45)
                .bearing(bearing)
                .build();                   // Creates a CameraPosition from the builder
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

    }

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        animateToMyLocation();
                    } else {
                        ActivityCompat.requestPermissions(this, new String[]{
                                        android.Manifest.permission.ACCESS_FINE_LOCATION},
                                MY_PERMISSIONS_REQUEST_FINE_LOCATION);
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }


    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        if (navigationMode) {
            navigationCamera(new LatLng(location.getLatitude(), location.getLongitude()), location.getBearing());
        }
    }


    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();

    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000 * 2);
        mLocationRequest.setFastestInterval(1000 * 1);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        PendingResult<Status> pendingResult = LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
        if (mRequestQueue != null) {
            mRequestQueue.cancelAll(CRIME_REQUEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }


    @Override
    public void onBackPressed() {
        if (placeAutocompleteLayout.getVisibility() == LinearLayout.GONE) {
            onBackButton();
        } else {
            super.onBackPressed();
        }


    }

    private void onBackButton() {
        placeAutocompleteLayout.setVisibility(LinearLayout.VISIBLE);
        if (PolyMyRoutes.size() > 0) {
            PolyMyRoutes.clear();
        }

        mMap.clear();
        animateToMyLocation();
        autocompleteOrigin.setText("");
        autocompleteDestination.setText("");
        origin = null;
        destination = null;
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        backButton.setVisibility(View.GONE);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        navigationMode = false;
        myLocationSelected = false;
        PolyMyRoutes.clear();
        mMap.setPadding(0, 0, 0, 0);

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingCancelled() {

    }


    private Location getLastKnownLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();

        Location location = null;

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {

            location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_FINE_LOCATION);
        }

        return location;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }
}
