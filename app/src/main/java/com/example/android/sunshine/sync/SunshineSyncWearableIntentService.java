package com.example.android.sunshine.sync;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;

import static android.webkit.ConsoleMessage.MessageLevel.LOG;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in a service on a
 * separate handler thread. <p> TODO: Customize class - update intent actions, extra parameters and
 * static helper methods.
 */
public class SunshineSyncWearableIntentService extends IntentService implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = SunshineSyncWearableIntentService.class.getSimpleName();

    GoogleApiClient mGoogleApiClient;

    public SunshineSyncWearableIntentService() {
        super("SunshineSyncWearableIntentService");
    }

    @Override
    public void onCreate() {
    super.onCreate();

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            //final String action = intent.getAction();
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    // Request access only to the Wearable API
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();
            Log.v(LOG_TAG, "service started");
        }
    }

    private static final String WEATHER_MIN_DATA_KEY = "weather.min";
    private static final String WEATHER_MAX_DATA_KEY = "weather.max";

    public void sendWeatherData(){
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weather");
        putDataMapRequest.getDataMap().putString(WEATHER_MAX_DATA_KEY, "21");
        putDataMapRequest.getDataMap().putString(WEATHER_MIN_DATA_KEY, "9");
        putDataMapRequest.getDataMap().putLong("time_stamp", System.currentTimeMillis());

        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        if(!dataItemResult.getStatus().isSuccess()){
                            Log.v(LOG_TAG, "not Sucess stored locally");
                        }else{
                            Log.v(LOG_TAG, "sucess stored locally" );
                        }
                    }
                });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.v(LOG_TAG, "Connected");
        sendWeatherData();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.v(LOG_TAG, "Failed Connect");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
    }


}
