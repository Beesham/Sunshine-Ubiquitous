/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.beesham.sunshinewearable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.graphics.BitmapFactory.decodeResource;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class DigitialSunshineWatchFace extends CanvasWatchFaceService {

    private static final String LOG_TAG = DigitialSunshineWatchFace.class.getSimpleName();

    String mMinTemp = "0";
    String mMaxTemp = "0";
    int mIconID;

    private int specW, specH;
    private View mWatchfaceLayout;
    private TextView date_textView;
    private TextView hour_textView;
    private TextView  minute_textView;
    private ImageView weatherIcon_imageView;
    private TextView tempHigh_textView;
    private TextView tempLow_textView;
    private final Point displaySize = new Point();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<DigitialSunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(DigitialSunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            DigitialSunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;

        boolean mAmbient;
        Calendar mCalendar;
        Date mDate;

        String mDateString;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        SimpleDateFormat simpleDateFormat;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(DigitialSunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            LayoutInflater inflater =
                    (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mWatchfaceLayout = inflater.inflate(R.layout.watchface, null);

            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            display.getSize(displaySize);

            specW = View.MeasureSpec.makeMeasureSpec(displaySize.x,
                    View.MeasureSpec.EXACTLY);
            specH = View.MeasureSpec.makeMeasureSpec(displaySize.y,
                    View.MeasureSpec.EXACTLY);

            date_textView = (TextView) mWatchfaceLayout.findViewById(R.id.date_text_view);
            hour_textView = (TextView) mWatchfaceLayout.findViewById(R.id.hour_text_view);
            minute_textView = (TextView) mWatchfaceLayout.findViewById(R.id.minutes_text_view);
            weatherIcon_imageView = (ImageView) mWatchfaceLayout.findViewById(R.id.weather_icon_image_view);
            tempHigh_textView = (TextView) mWatchfaceLayout.findViewById(R.id.tempHigh_text_view);
            tempLow_textView = (TextView) mWatchfaceLayout.findViewById(R.id.tempLow_text_view);


            setWatchFaceStyle(new WatchFaceStyle.Builder(DigitialSunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = DigitialSunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            simpleDateFormat = new SimpleDateFormat("EEE, MMM dd yyyy");
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            DigitialSunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            DigitialSunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    hour_textView.getPaint().setAntiAlias(!inAmbientMode);
                    minute_textView.getPaint().setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes a
         * tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    Log.v(LOG_TAG, "Face tapped");
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                weatherIcon_imageView.setVisibility(View.GONE);
                tempHigh_textView.setVisibility(View.GONE);
                tempLow_textView.setVisibility(View.GONE);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                weatherIcon_imageView.setVisibility(View.VISIBLE);
                tempHigh_textView.setVisibility(View.VISIBLE);
                tempLow_textView.setVisibility(View.VISIBLE);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            String hour = String.format("%02d", mCalendar.get(Calendar.HOUR_OF_DAY));
            String minutes = String.format("%02d", mCalendar.get(Calendar.MINUTE));

            mDateString = simpleDateFormat.format(mDate);

            hour_textView.setText(hour);
            minute_textView.setText(minutes);
            date_textView.setText(mDateString.toUpperCase());
            weatherIcon_imageView.setImageResource(mIconID);
            tempHigh_textView.setText(mMaxTemp);
            tempLow_textView.setText(mMinTemp);

            mWatchfaceLayout.measure(specW, specH);
            mWatchfaceLayout.layout(0, 0, mWatchfaceLayout.getMeasuredWidth(),
                    mWatchfaceLayout.getMeasuredHeight());

            mWatchfaceLayout.draw(canvas);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for(DataEvent dataEvent : dataEventBuffer){
                if(dataEvent.getType() == DataEvent.TYPE_CHANGED){
                    DataMap dataMap = DataMapItem.fromDataItem(
                            dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    if(path.equals("/weather")){
                        mMinTemp = dataMap.getString("weather.min").trim();
                        mMaxTemp = dataMap.getString("weather.max").trim();
                        Log.v(LOG_TAG, "minTemp, maxTemp " + mMinTemp.trim() + mMaxTemp.trim());
                        mIconID = getSmallArtResourceIdForWeatherCondition(dataMap.getInt("iconID"));
                        Log.v(LOG_TAG, "data on wearable");
                    }
                }
            }
            invalidate();
        }
    }

    /**
     * Helper method to provide the icon resource id according to the weather condition id returned
     * by the OpenWeatherMap call. This method is very similar to
     *
     *
     *
     * The difference between these two methods is that this method provides smaller assets, used
     * in the list item layout for a "future day", as well as
     *
     * @param weatherId from OpenWeatherMap API response
     *                  See http://openweathermap.org/weather-conditions for a list of all IDs
     *
     * @return resource id for the corresponding icon. -1 if no relation is found.
     */
    public static int getSmallArtResourceIdForWeatherCondition(int weatherId) {

        /*
         * Based on weather code data for Open Weather Map.
         */
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            //return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 771 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        } else if (weatherId >= 900 && weatherId <= 906) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 958 && weatherId <= 962) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 951 && weatherId <= 957) {
            return R.drawable.ic_clear;
        }

        Log.e(LOG_TAG, "Unknown Weather: " + weatherId);
        return R.drawable.ic_storm;
    }

}
