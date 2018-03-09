package com.example.arsenko.sunrisesunset;

import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;

public class MainActivity extends AppCompatActivity implements OnConnectionFailedListener{

    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 1;
    Button mSearchButton;
    ProgressBar mProgressBar;
    TextView mResultView;
    PlaceAutocompleteFragment autocompleteFragment;

    public static final String API_URL = "https://api.sunrise-sunset.org/json";
    public static String STRAIGHT_URL;

    String mPlace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSearchButton = findViewById(R.id.search_button);
        autocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.search_field);
        mProgressBar = findViewById(R.id.progress_bar);
        mResultView = findViewById(R.id.result_view);

        GoogleApiClient mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .enableAutoManage(this, this)
                .build();

        // Using Autocomplete search by Google
        AutocompleteFilter typeFilter = new AutocompleteFilter.Builder()
                .setTypeFilter(AutocompleteFilter.TYPE_FILTER_ADDRESS)
                .build();
        autocompleteFragment.setFilter(typeFilter);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                Log.i("TAG", "Place: " + place.getName());//get place details here
                mPlace = place.getName().toString();
                double lat = place.getLatLng().latitude;
                double lng = place.getLatLng().longitude;
                Log.i("TAG", "Lat: " + lat +" "+"Lng: "+lng);
                StringBuilder sb = new StringBuilder();
                sb = sb.append("?lat=").append(lat).append("&lng=").append(lng);
                STRAIGHT_URL = sb.toString();
                new ForRestTask().execute();
            }

            @Override
            public void onError(Status status) {
                Log.i("TAG", "An error occurred: " + status);
            }
        });

        // Check if permission granted
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_READ_CONTACTS);
            return;
        }
        // Find user position
        PendingResult<PlaceLikelihoodBuffer> result = Places.PlaceDetectionApi.getCurrentPlace(mGoogleApiClient, null);
        result.setResultCallback(new ResultCallback<PlaceLikelihoodBuffer>() {
            @Override
            public void onResult(@NonNull PlaceLikelihoodBuffer likelyPlaces) {

                for (PlaceLikelihood placeLikelihood  : likelyPlaces ) {
                    if(placeLikelihood.getLikelihood() > 0.500000){  // filter: likelihood min - 50%
                    Log.i("TAG", String.format("Place '%s' has likelihood: %g",
                            placeLikelihood.getPlace().getName(),
                            placeLikelihood.getLikelihood()));
                    mPlace = placeLikelihood.getPlace().getName().toString();
                    mResultView.setText("Your location "  + placeLikelihood.getPlace().getName());
                    double curLatitude = placeLikelihood.getPlace().getLatLng().latitude;
                    double curLongitude = placeLikelihood.getPlace().getLatLng().longitude;
                    StringBuilder sb = new StringBuilder();
                    sb = sb.append("?lat=").append(curLatitude).append("&lng=").append(curLongitude);
                    STRAIGHT_URL = sb.toString();
                    new ForRestTask().execute();
                    }
                }
                likelyPlaces.release();
            }
        });
    }
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e("TAG", "Connection failed");
    }

    // AsynkTask for API connection and parsing JSON
    private class ForRestTask extends AsyncTask<Void, Void, String> {

        protected void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
            mResultView.setText("");

        }
        protected String doInBackground(Void... urls) {
            // make connection
            try {
                URL url = new URL(API_URL + STRAIGHT_URL);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                Log.i("log", "open connection");
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();
                    return stringBuilder.toString();

                } finally {
                    if (httpURLConnection != null) {
                         httpURLConnection.disconnect();
                    }
                }
            } catch (Exception e) {
                Log.e("ERROR", e.getMessage(), e);
                return null;
            }

        }
        // parsing JSON
        protected void onPostExecute(String response) {
            if (response == null) {
                response = "Error!";
            }
            mProgressBar.setVisibility(View.GONE);
            Log.i("TAG", "response" + response);

            try {
                JSONObject object = new JSONObject(response);
                JSONObject resObject = object.getJSONObject("results");
                Log.i("TAG", "results" + resObject);

                String sunrise = resObject.optString("sunrise");
                String sunset = resObject.optString("sunset");

                TimeZone tz = TimeZone.getDefault();
                String tzId = tz.getID();

                SimpleDateFormat formatDate = new SimpleDateFormat("hh:mm:ss a");
                Date utcSunrise = formatDate.parse(sunrise);
                Date utcSunset = formatDate.parse(sunset);
                Date dRise = new Date();
                Date dSet = new Date();
                dRise.setTime(utcSunrise.getTime() + TimeZone.getDefault().getRawOffset());
                dSet.setTime(utcSunset.getTime() + TimeZone.getDefault().getRawOffset());
                String currSunrise = formatDate.format(dRise);
                String currSunset = formatDate.format(dSet);

//                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a");
//                Date date = sdf.parse(sunrise);
//                sdf.setTimeZone(TimeZone.getTimeZone("EET"));
//                sdf.parse(sunrise);
//                String newString = sdf.format(new Date());

                String dayLength = resObject.optString("day_length");
                Log.i("TAG", "sunrise: " + sunrise + " " + "sunset: " + sunset);
                StringBuilder strBuilder = new StringBuilder();
                strBuilder.append(mResultView.getText()).append("In your current location \n")
                        .append("Sunrise is in: ").append(currSunrise).append("\n")
                        .append("Sunset is in: ").append(currSunset).append("\n").append("Day Length: ")
                        .append(dayLength).append("\n").append("Time zone: ").append(tzId);
                mResultView.setText(strBuilder);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}