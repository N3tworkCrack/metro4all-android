/******************************************************************************
 * Project:  Metro Access
 * Purpose:  Routing in subway for disabled.
 * Author:   Baryshnikov Dmitriy aka Bishop (polimax@mail.ru)
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 ******************************************************************************
 *   Copyright (C) 2013-2015 NextGIS
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ****************************************************************************/
package com.nextgis.metroaccess.ui.activity;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.nextgis.metroaccess.MetroApp;
import com.nextgis.metroaccess.R;
import com.nextgis.metroaccess.data.metro.PortalItem;
import com.nextgis.metroaccess.data.metro.StationItem;
import com.nextgis.metroaccess.ui.view.StationMapView;
import com.nextgis.metroaccess.util.Constants;
import com.nextgis.metroaccess.util.ResourceProxyImpl;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

import static com.nextgis.metroaccess.ui.activity.MainActivity.isProviderDisabled;
import static com.nextgis.metroaccess.ui.activity.MainActivity.showLocationInfoDialog;
import static com.nextgis.metroaccess.ui.activity.MainActivity.tintIcons;
import static com.nextgis.metroaccess.util.Constants.BUNDLE_PORTALID_KEY;
import static com.nextgis.metroaccess.util.Constants.BUNDLE_STATIONID_KEY;
import static com.nextgis.metroaccess.util.Constants.PARAM_ACTIVITY_FOR_RESULT;
import static com.nextgis.metroaccess.util.Constants.PARAM_PORTAL_DIRECTION;
import static com.nextgis.metroaccess.util.Constants.PARAM_ROOT_ACTIVITY;
import static com.nextgis.metroaccess.util.Constants.SUBSCREEN_PORTAL_RESULT;

public class StationMapActivity extends AppCompatActivity {
    private enum MARKERS_TYPE { ENTRANCE, EXIT, CHECKED, INVALID }
    private int[] MARKERS_COLOR = { R.color.portal_normal, R.color.portal_normal, R.color.portal_checked, R.color.portal_invalid};

    protected int mnType;
    protected int mnMaxWidth, mnWheelWidth;
    protected boolean m_bHaveLimits;

/*  // commented because of bug https://github.com/osmdroid/osmdroid/issues/49
    private final static String PREFS_TILE_SOURCE = "map_tile_source";
*/
    private final static String PREFS_SCROLL_X = "map_scroll_x";
    private final static String PREFS_SCROLL_Y = "map_scroll_y";
    private final static String PREFS_ZOOM_LEVEL = "map_zoom_level";
    private final static String PREFS_MAP_LATITUDE = "map_latitude";
    private final static String PREFS_MAP_LONGITUDE = "map_longitude";

//    private final static String PREFS_SHOW_LOCATION = "map_show_loc";
//    private final static String PREFS_SHOW_COMPASS = "map_show_compass";

    private Context mAppContext;

    private StationMapView mMapView;
    private GpsMyLocationProvider gpsMyLocationProvider;
    private ResourceProxy mResourceProxy;
    private float scaledDensity;

    private int mStationID, mPortalID;
    private List<StationItem> stationList;
    private boolean mIsRootActivity, mIsPortalIn, mNeedResult;
//    private boolean isCrossReference;

    //overlays
    private MyLocationNewOverlay mLocationOverlay;

    private Bundle bundle;

    private int spanLat, spanLong;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent inIntent = getIntent();
        bundle = inIntent.getExtras();

        mStationID = inIntent.getIntExtra(BUNDLE_STATIONID_KEY, 0);
        mPortalID = inIntent.getIntExtra(BUNDLE_PORTALID_KEY, 0);
        mIsPortalIn = inIntent.getBooleanExtra(PARAM_PORTAL_DIRECTION, true);
        mNeedResult = inIntent.getBooleanExtra(PARAM_ACTIVITY_FOR_RESULT, true);
        mIsRootActivity = inIntent.getBooleanExtra(PARAM_ROOT_ACTIVITY, true);
//        isCrossReference = inIntent.getExtras().containsKey(PARAM_ROOT_ACTIVITY); // if PARAM_ROOT_ACTIVITY not contains, it called from another

//        selectedStation = Analytics.getGraph().GetStation(mStationID);
        StationItem station = MetroApp.getGraph().GetStation(mStationID);

//        if (selectedStation == null)
//            selectedStation = new StationItem(-1, getString(R.string.sStationName) + ": " + m_oContext.getString(R.string.sNotSet), -1, -1, -1, -1, -1, -1);

        Tracker t = ((MetroApp) getApplication()).getTracker();
        t.setScreenName(Constants.SCREEN_MAP + " " + getDirection());
        t.send(new HitBuilders.AppViewBuilder().build());

        mAppContext = getApplicationContext();
        mResourceProxy = new ResourceProxyImpl(mAppContext);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mAppContext);
        mnType = prefs.getInt(PreferencesActivity.KEY_PREF_USER_TYPE + "_int", 2);
        mnMaxWidth = LimitationsActivity.getMaxWidth(this);
        mnWheelWidth = LimitationsActivity.getWheelWidth(this);
        m_bHaveLimits = LimitationsActivity.hasLimitations(this);

        String title = station == null ? getString(R.string.sNotSet) : station.GetName();
        setTitle(String.format(getString(mIsPortalIn ? R.string.sInPortalMapTitle : R.string.sOutPortalMapTitle), title));

        double lat, lon;
        lat = station == null ? 0 : station.GetLatitude();
        lon = station == null ? 0 : station.GetLongitude();

        mMapView = new StationMapView(mAppContext, mResourceProxy, new GeoPoint(lat, lon));
        InitMap();

        setContentView(R.layout.activity_station_map);
        ((FrameLayout) findViewById(R.id.fl_map_container)).addView(mMapView);

        // TODO super
        ImageButton tvReport = (ImageButton) findViewById(R.id.ib_report);
//        tvReport.setPaintFlags(tvReport.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentReport = new Intent(getApplicationContext(), ReportActivity.class);
                intentReport.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                intentReport.putExtra(BUNDLE_STATIONID_KEY, bundle.getInt(BUNDLE_STATIONID_KEY, -1));
                startActivity(intentReport);
            }
        });

        mMapView.postDelayed(new Runnable() {   // there is no callback on map loaded
            @Override
            public void run() {
                mMapView.getController().zoomToSpan(spanLat, spanLong);
            }
        }, 1500);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setHardwareAccelerationOff() {
        // Turn off hardware acceleration here, or in manifest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mMapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    protected void InitMap() {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mAppContext);

        // Call this method to turn off hardware acceleration at the View level.
        setHardwareAccelerationOff();

        gpsMyLocationProvider = new GpsMyLocationProvider(mAppContext);

        //add overlays
        mLocationOverlay =
                new MyLocationNewOverlay(mAppContext, gpsMyLocationProvider, mMapView);
        mLocationOverlay.setDrawAccuracyEnabled(true);
        mLocationOverlay.enableMyLocation();

        mMapView.getOverlays().add(mLocationOverlay);

        LoadPortalsToOverlay();

        mMapView.setMultiTouchControls(true);
        mMapView.setBuiltInZoomControls(true);
//        mMapView.getController().setZoom(prefs.getInt(PREFS_ZOOM_LEVEL, 15));
        mMapView.getController().setZoom(mMapView.getMaxZoomLevel());
        mMapView.scrollTo(prefs.getInt(PREFS_SCROLL_X, 0), prefs.getInt(PREFS_SCROLL_Y, 0));
    }

    private Drawable getMarkerDrawable(MARKERS_TYPE type) {
        Drawable result;
        Bitmap original, scaledBitmap;

        original = MainActivity.getBitmapFromSVG(this, R.raw.portal, getResources().getColor(MARKERS_COLOR[type.ordinal()]));
        int size = (int) (24 * scaledDensity);

        scaledBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        float ratioX = size / (float) original.getWidth();
        float ratioY = size / (float) original.getHeight();

        Matrix matrix = new Matrix();
        matrix.setScale(ratioX, ratioY);

        Canvas cnv = new Canvas(scaledBitmap);
        cnv.setMatrix(matrix);
        cnv.drawBitmap(original, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));

        result = new BitmapDrawable(mAppContext.getResources(), scaledBitmap);

        return result;
    }

    private Drawable overlayMeetcode(Drawable drawable, int meetcode) {
        Bitmap bm = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setColor(Color.WHITE);
        paint.setTextSize(scaledDensity * 16);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAntiAlias(true);

        Canvas canvas = new Canvas(bm);
        drawable.setBounds(0, 0, bm.getWidth(), bm.getHeight());
        drawable.draw(canvas);

        int yCenter = (int) ((canvas.getHeight() / 2) - ((paint.descent() + paint.ascent()) / 2)) ;
        canvas.drawText(meetcode + "", bm.getWidth() / 2, yCenter, paint);

        return new BitmapDrawable(getResources(), bm);
    }

    protected void LoadPortalsToOverlay() {
        ArrayList<OverlayItem> overlayPortals = new ArrayList<>();
        ArrayList<OverlayItem> overlayTransparentPortals = new ArrayList<>();

        scaledDensity = getBaseContext().getResources().getDisplayMetrics().scaledDensity;

        Drawable markerPortal = getMarkerDrawable(mIsPortalIn ? MARKERS_TYPE.ENTRANCE: MARKERS_TYPE.EXIT);
        Drawable markerTransparentPortal = markerPortal.getConstantState().newDrawable().mutate();
        Drawable markerInvalidPortal = getMarkerDrawable(MARKERS_TYPE.INVALID);
        Drawable markerTransparentInvalidPortal = markerInvalidPortal.getConstantState().newDrawable().mutate();
        Drawable markerCheckedPortal = getMarkerDrawable(MARKERS_TYPE.CHECKED);

        markerTransparentPortal.setAlpha(127);
        markerTransparentInvalidPortal.setAlpha(127);

        stationList = new ArrayList<>(MetroApp.getGraph().GetStations().values());

        double minLat = Double.MAX_VALUE, minLong = Double.MAX_VALUE, maxLat = Double.MIN_VALUE, maxLong = Double.MIN_VALUE;

        int stationListSize = stationList.size();
        int i = 0;
        boolean isForSelectedStation = false;

        while (i < stationListSize || isForSelectedStation) {
            StationItem station;

            if (!isForSelectedStation) {
                station = stationList.get(i);
            } else {
                station = MetroApp.getGraph().GetStation(mStationID);
            }

            boolean isSelectedStation = isForSelectedStation || (station.GetId() == mStationID);

            if (isSelectedStation && !isForSelectedStation) {
                ++i;
                isForSelectedStation = !isForSelectedStation && (i == stationListSize);
                continue;
            }

            List<PortalItem> portalList = station.GetPortals(mIsPortalIn);

            if (isSelectedStation) {
                if (portalList.size() == 0) {
                    minLat = maxLat = station.GetLatitude();
                    minLong = maxLong = station.GetLongitude();
                    Toast.makeText(this, getString(R.string.sNoPortals), Toast.LENGTH_SHORT).show();
                }
            }

            for (PortalItem portal : portalList) {
                String portalName = portal.GetReadableMeetCode();
                portalName = portalName.equals("") ? ": " + portal.GetName() : " " + portalName + ": " + portal.GetName();

                OverlayItem itemPortal = new OverlayItem(station.GetId() + "", portal.GetId() + "",
                        String.format(getString(R.string.sStationPortalName), station.GetName(), getString(mIsPortalIn ? R.string.sEntranceName : R.string.sExitName), portalName),
                        new GeoPoint(portal.GetLatitude(), portal.GetLongitude()));

                boolean isInvalidPortal = false;

                if (mnType > 1) {
                    boolean bSmallWidth = portal.GetDetails()[0] < mnMaxWidth;
                    boolean bCanRoll = portal.GetDetails()[7] == 0
                            || portal.GetDetails()[5] <= mnWheelWidth
                            && (portal.GetDetails()[6] == 0
                                || mnWheelWidth <= portal.GetDetails()[6]);
                    if (m_bHaveLimits && (bSmallWidth || !bCanRoll)) {
                        isInvalidPortal = true;
                    }
                }

                Drawable marker;

                if (isSelectedStation) {
                    if (portal.GetId() == mPortalID)
                        itemPortal.setMarker(overlayMeetcode(markerCheckedPortal, portal.GetMeetCode()));
                    else {
                        marker = isInvalidPortal ? markerInvalidPortal : markerPortal;
                        itemPortal.setMarker(overlayMeetcode(marker, portal.GetMeetCode()));
                    }

                    double portalLat = portal.GetLatitude();
                    double portalLong = portal.GetLongitude();

                    maxLat = Math.max(portalLat, maxLat);
                    minLat = Math.min(portalLat, minLat);
                    maxLong = Math.max(portalLong, maxLong);
                    minLong = Math.min(portalLong, minLong);

                    overlayPortals.add(itemPortal);

                } else {
                    marker = isInvalidPortal ? markerTransparentInvalidPortal : markerTransparentPortal;
                    itemPortal.setMarker(overlayMeetcode(marker, portal.GetMeetCode()));
                    overlayTransparentPortals.add(itemPortal);
                }

                itemPortal.setMarkerHotspot(OverlayItem.HotspotPlace.CENTER);
            }

            ++i;
            isForSelectedStation = !isForSelectedStation && (i == stationListSize);
        }

        GeoPoint stationCenter = new GeoPoint((maxLat + minLat) / 2, (maxLong + minLong) / 2);
        mMapView.setMapCenter(stationCenter);

        double padding = 1.2;
        spanLat = (int) (Math.abs(maxLat * 1E6 - minLat * 1E6) * padding);
        spanLong = (int) (Math.abs(maxLong * 1E6 - minLong * 1E6) * padding);

        ArrayList<OverlayItem> overlayItems = overlayTransparentPortals;

        for (int j = 0; j < 2; ++j) {
            ItemizedIconOverlay<OverlayItem> mPointsOverlay = new ItemizedIconOverlay<>(overlayItems,
                    new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

                        public boolean onItemSingleTapUp(final int index,
                                                         final OverlayItem item) {
                            if (!mNeedResult) {
                                Toast.makeText(mAppContext, item.getSnippet(), Toast.LENGTH_LONG).show();
                                return true;
                            }

                            ((MetroApp) getApplication()).addEvent(Constants.SCREEN_MAP + " " + getDirection(), Constants.PORTAL, Constants.SCREEN_MAP);

                            StationItem selectedStation = MetroApp.getGraph()
                                    .GetStation(Integer.parseInt(item.getUid()));

                            if (selectedStation == null) {
                                Toast.makeText(mAppContext, R.string.sNoOrEmptyData,
                                        Toast.LENGTH_LONG).show();
                                return true;
                            }

                            PortalItem selectedPortal = selectedStation
                                    .GetPortal(Integer.parseInt(item.getTitle()));

                            if (selectedPortal == null) {
                                Toast.makeText(mAppContext, R.string.sNoOrEmptyData,
                                        Toast.LENGTH_LONG).show();
                                return true;
                            }

                            Intent outIntent = new Intent();
                            outIntent.putExtra(BUNDLE_STATIONID_KEY, selectedPortal.GetStationId());
                            outIntent.putExtra(BUNDLE_PORTALID_KEY, selectedPortal.GetId());
                            setResult(RESULT_OK, outIntent);
                            finish();

                            return true; // We 'handled' this event.
                        }

                        public boolean onItemLongPress(final int index,
                                                       final OverlayItem item) {
                            Toast.makeText(mAppContext, item.getSnippet(),
                                    Toast.LENGTH_LONG).show();
                            return true; // We 'handled' this event.
                        }
                    }
                    , mResourceProxy);

            mMapView.getOverlays().add(mPointsOverlay);

            overlayItems = overlayPortals;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean was = m_bHaveLimits;
        m_bHaveLimits = LimitationsActivity.hasLimitations(this);
        boolean limitationsChanged = LimitationsActivity.getWheelWidth(this) != mnWheelWidth ||
                LimitationsActivity.getMaxWidth(this) != mnMaxWidth;

        if (was != m_bHaveLimits || limitationsChanged) {
            mnWheelWidth = LimitationsActivity.getWheelWidth(this);
            mnMaxWidth = LimitationsActivity.getMaxWidth(this);
            mMapView.getOverlays().remove(1);
            LoadPortalsToOverlay();
        }

/*  // commented because of bug https://github.com/osmdroid/osmdroid/issues/49
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mAppContext);
        final String tileSourceName = prefs.getString(PREFS_TILE_SOURCE,
                TileSourceFactory.DEFAULT_TILE_SOURCE.name());
        try {
            final ITileSource tileSource =
                    TileSourceFactory.getTileSource(tileSourceName);
            mMapView.setTileSource(tileSource);
        } catch (final IllegalArgumentException e) {
            mMapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        }
*/

        // For bug https://github.com/osmdroid/osmdroid/issues/49
        // "Tiles are too small on high dpi devices"
        // It is from sources of TileSourceFactory
        final int newScale = (int) (256 * scaledDensity);
        OnlineTileSourceBase mapSource = new XYTileSource(
                "Mapnik",
                ResourceProxy.string.mapnik,
                0,
                18,
                newScale,
                ".png",
                new String[]{
                        "http://a.tile.openstreetmap.org/",
                        "http://b.tile.openstreetmap.org/",
                        "http://c.tile.openstreetmap.org/"});
        mMapView.setTileSource(mapSource);


//        if (prefs.getBoolean(PREFS_SHOW_LOCATION, true)) {
            mLocationOverlay.enableMyLocation();
//        }
//        if (prefs.getBoolean(PREFS_SHOW_COMPASS, true)) {
//            mLocationOverlay.enableCompass();
//        }
    }

    @Override
    public void onPause() {
        final SharedPreferences.Editor edit =
                PreferenceManager.getDefaultSharedPreferences(mAppContext).edit();

/*  // commented because of bug https://github.com/osmdroid/osmdroid/issues/49
        edit.putString(PREFS_TILE_SOURCE, mMapView.getTileProvider().getTileSource().name());
*/
        edit.putInt(PREFS_SCROLL_X, mMapView.getScrollX());
        edit.putInt(PREFS_SCROLL_Y, mMapView.getScrollY());
        edit.putInt(PREFS_ZOOM_LEVEL, mMapView.getZoomLevel());
//        edit.putBoolean(PREFS_SHOW_LOCATION, mLocationOverlay.isMyLocationEnabled());
//        edit.putBoolean(PREFS_SHOW_COMPASS, mLocationOverlay.isCompassEnabled());

        edit.apply();

        mLocationOverlay.disableMyLocation();
//        mLocationOverlay.disableCompass();

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putDouble(PREFS_MAP_LATITUDE, mMapView.getMapCenter().getLatitude());
        outState.putDouble(PREFS_MAP_LONGITUDE, mMapView.getMapCenter().getLongitude());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        double nLat = savedInstanceState.getDouble(PREFS_MAP_LATITUDE, 0);
        double nLong = savedInstanceState.getDouble(PREFS_MAP_LONGITUDE, 0);
        mMapView.setRestoredMapCenter(new GeoPoint(nLat, nLong));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater infl = getMenuInflater();
        infl.inflate(R.menu.menu_station_map, menu);
        tintIcons(menu, this);
//        menu.findItem(R.id.btn_layout).setEnabled(isCrossReference).setVisible(isCrossReference);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                ((MetroApp) getApplication()).addEvent(Constants.SCREEN_MAP + " " + getDirection(), Constants.BACK, Constants.SCREEN_MAP);

                finish();
                return true;
            case R.id.btn_layout:
                ((MetroApp) getApplication()).addEvent(Constants.SCREEN_MAP + " " + getDirection(), Constants.BTN_LAYOUT, Constants.ACTION_BAR);

                if (mIsRootActivity) {
                    Intent intentView = new Intent(this, StationImageActivity.class);
                    intentView.putExtras(bundle);
                    intentView.putExtra(PARAM_ROOT_ACTIVITY, false);
                    startActivityForResult(intentView, SUBSCREEN_PORTAL_RESULT);
                } else
                    finish();

                return true;
            case R.id.btn_location_found:
                ((MetroApp) getApplication()).addEvent(Constants.SCREEN_MAP + " " + getDirection(), "Find nearest station", Constants.ACTION_BAR);

                final Context context = this;
                if (isProviderDisabled(context, false)) {
                    showLocationInfoDialog(context, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (isProviderDisabled(context, true))
                                Toast.makeText(context, R.string.sLocationFail, Toast.LENGTH_LONG).show();
                            else
                                onLocationFoundClick();
                        }
                    });
                } else
                    onLocationFoundClick();

                return true;
            case R.id.btn_limitations:
                ((MetroApp) getApplication()).addEvent(Constants.SCREEN_MAP + " " + getDirection(), Constants.LIMITATIONS, Constants.MENU);
                startActivity(new Intent(this, LimitationsActivity.class));
//                startActivityForResult(new Intent(this, LimitationsActivity.class), PREF_RESULT);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SUBSCREEN_PORTAL_RESULT:
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK, data);
                    finish();
                }
                break;
            default:
                break;
        }
    }

    private String getDirection() {
        return mIsPortalIn ? Constants.FROM : Constants.TO;
    }

    @Override
    public void onBackPressed() {
        ((MetroApp) getApplication()).addEvent(Constants.SCREEN_MAP + " " + getDirection(), Constants.BACK, Constants.SCREEN_MAP);

        super.onBackPressed();
    }

    public void onLocationFoundClick() {
        Location myLocation = gpsMyLocationProvider.getLastKnownLocation();

        if (null == myLocation) return;

        if (mMapView.getScreenRect(null).height() > 0) {

            StationItem nearestStation = null;
            float minDistance = Float.MAX_VALUE;

            for (StationItem station : stationList) {

                List<PortalItem> portalList = station.GetPortals(mIsPortalIn);
                float[] distanceToPortal = new float[1];

                for (PortalItem portal : portalList) {

                    Location.distanceBetween(myLocation.getLatitude(), myLocation.getLongitude(),
                            portal.GetLatitude(), portal.GetLongitude(), distanceToPortal);

                    if (distanceToPortal[0] < minDistance) {
                        minDistance = distanceToPortal[0];
                        nearestStation = station;
                    }
                }
            }

            if (null != nearestStation) {

                List<PortalItem> portalList = nearestStation.GetPortals(mIsPortalIn);

                float[] distanceToPortal = new float[1];
                float maxDistance = 0;
                PortalItem farPortal = null;

                for (PortalItem portal : portalList) {

                    Location.distanceBetween(myLocation.getLatitude(), myLocation.getLongitude(),
                            portal.GetLatitude(), portal.GetLongitude(), distanceToPortal);

                    if (distanceToPortal[0] > maxDistance) {
                        maxDistance = distanceToPortal[0];
                        farPortal = portal;
                    }
                }

                if (null != farPortal) {

                    GeoPoint myGeoPoint = new GeoPoint(myLocation);

                    int north = myGeoPoint.destinationPoint(maxDistance, 0).getLatitudeE6();
                    int south = myGeoPoint.destinationPoint(maxDistance, 180).getLatitudeE6();
                    int east = myGeoPoint.destinationPoint(maxDistance, 90).getLongitudeE6();
                    int west = myGeoPoint.destinationPoint(maxDistance, 270).getLongitudeE6();

                    mMapView.zoomToBoundingBox(new BoundingBoxE6(north, east, south, west));

                    Toast.makeText(mAppContext,
                            String.format(getString(R.string.sNearestStation),
                                    nearestStation.GetName()),
                            Toast.LENGTH_LONG).show();
                }
            }
        }

        mMapView.getController().animateTo(new GeoPoint(myLocation));
    }
}
