package com.maps;

import java.util.Arrays;
import java.util.Locale;

import com.skobbler.ngx.SKCoordinate;
import com.skobbler.ngx.SKMaps;
import com.skobbler.ngx.map.SKAnimationSettings;
import com.skobbler.ngx.map.SKAnnotation;
import com.skobbler.ngx.map.SKCoordinateRegion;
import com.skobbler.ngx.map.SKMapCustomPOI;
import com.skobbler.ngx.map.SKMapPOI;
import com.skobbler.ngx.map.SKMapSettings.SKMapFollowerMode;
import com.skobbler.ngx.map.SKMapSurfaceListener;
import com.skobbler.ngx.map.SKMapSurfaceView;
import com.skobbler.ngx.map.SKMapViewHolder;
import com.skobbler.ngx.map.SKPOICluster;
import com.skobbler.ngx.map.SKScreenPoint;
import com.skobbler.ngx.navigation.SKAdvisorSettings;
import com.skobbler.ngx.navigation.SKNavigationListener;
import com.skobbler.ngx.navigation.SKNavigationManager;
import com.skobbler.ngx.navigation.SKNavigationSettings;
import com.skobbler.ngx.navigation.SKNavigationSettings.SKNavigationType;
import com.skobbler.ngx.navigation.SKNavigationState;
import com.skobbler.ngx.navigation.SKNavigationState.SKStreetType;
import com.skobbler.ngx.positioner.SKCurrentPositionListener;
import com.skobbler.ngx.positioner.SKCurrentPositionProvider;
import com.skobbler.ngx.positioner.SKPosition;
import com.skobbler.ngx.reversegeocode.SKReverseGeocoderManager;
import com.skobbler.ngx.routing.SKRouteInfo;
import com.skobbler.ngx.routing.SKRouteJsonAnswer;
import com.skobbler.ngx.routing.SKRouteListener;
import com.skobbler.ngx.routing.SKRouteManager;
import com.skobbler.ngx.routing.SKRouteSettings;
import com.skobbler.ngx.routing.SKRouteSettings.SKRouteMode;
import com.skobbler.ngx.sdktools.navigationui.SKToolsAdvicePlayer;
import com.skobbler.ngx.sdktools.navigationui.SKToolsNavigationListener;
import com.skobbler.ngx.sdktools.navigationui.SKToolsNavigationManager;
import com.skobbler.ngx.search.SKSearchResult;
import com.skobbler.ngx.util.SKLogging;
import com.skobbler.ngx.versioning.SKVersioningManager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements SKCurrentPositionListener,
SKNavigationListener, SKRouteListener, SKMapSurfaceListener, SKToolsNavigationListener{

	private DemoApplication app;
	private DrawerLayout drawerLayout;
	private SKCurrentPositionProvider currentPositionProvider;
	private SKPosition currentPosition;
	private MapOption currentMapOption = MapOption.MAP_DISPLAY;
	private boolean navigationInProgress;
	private Button bottomButton;
	private TextToSpeech textToSpeechEngine;
	private boolean shouldCacheTheNextRoute;
	private Integer cachedRouteId;
	private RelativeLayout navigationUI;
	private SKCoordinate startPoint;
	private SKCoordinate destinationPoint;
	private SKToolsNavigationManager navigationManager;
	private boolean skToolsNavigationInProgress;
	private boolean isStartPointBtnPressed = false, isEndPointBtnPressed = false;
	private boolean skToolsRouteCalculated;
	public static boolean compassAvailable;
	private static final byte GREEN_PIN_ICON_ID = 0;
	private static final byte RED_PIN_ICON_ID = 1;
	private static final String TAG = "MainActivity";
	public ToggleButton toggleButton;
	public static boolean roundTrip;

	/*
	 * Surface view for displaying the map
	*/
	private SKMapSurfaceView mapView;

	/**
	 * the view that holds the map view
	 */
	private SKMapViewHolder mapHolder;   
	private Button positionMeButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		app = (DemoApplication) getApplication();
		mapHolder = (SKMapViewHolder) findViewById(R.id.view_group_map);
		mapHolder.setMapSurfaceListener(this);
		positionMeButton = (Button)findViewById(R.id.position_me_button);
		bottomButton = (Button) findViewById(R.id.bottom_button);

		currentPositionProvider = new SKCurrentPositionProvider(this);
		currentPositionProvider.setCurrentPositionListener(this);
		currentPositionProvider.requestLocationUpdates(DemoUtils.hasGpsModule(this), DemoUtils.hasNetworkModule(this), false);

		navigationUI = (RelativeLayout) findViewById(R.id.navigation_ui_layout);
		navigationUI.setVisibility(View.GONE);
	}

	@Override
	protected void onPause() {
		
		super.onPause();
		mapHolder.onPause();
	}

	@Override
	public void onSurfaceCreated(SKMapViewHolder skMapViewHolder) {
		
		View chessBackground = findViewById(R.id.chess_board_background);
		chessBackground.setVisibility(View.GONE);

		mapView = mapHolder.getMapSurfaceView();
		applySettingsOnMapView();
		if(SplashActivity.newMapVersionDetected != 0){
			
			showUpdateDialog(SplashActivity.newMapVersionDetected);
		}

		if (!navigationInProgress) {
			mapView.getMapSettings().setFollowerMode(SKMapFollowerMode.NONE);
		}

		if (currentPosition != null) {
			mapView.reportNewGPSPosition(currentPosition);
		}
	}

	/**
	 * Customize the map view
	 */
	private void applySettingsOnMapView() {

		mapView.getMapSettings().setMapRotationEnabled(true);
		mapView.getMapSettings().setMapZoomingEnabled(true);
		mapView.getMapSettings().setMapPanningEnabled(true);
		mapView.getMapSettings().setZoomWithAnchorEnabled(true);
		mapView.getMapSettings().setInertiaRotatingEnabled(true);
		mapView.getMapSettings().setInertiaZoomingEnabled(true);
		mapView.getMapSettings().setInertiaPanningEnabled(true);
	}
	
	private void startOrientationSensorInPedestrian() {
		
		compassAvailable = getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS);
	}

	private void showUpdateDialog(final int newVersion){

		final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
		alertDialog.setMessage("New map version available");
		alertDialog.setCancelable(true);
		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.update_label),
				new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int id) {
				SKVersioningManager manager = SKVersioningManager.getInstance();
				boolean updated = manager.updateMapsVersion(newVersion);
				if (updated) {
					app.getAppPrefs().saveBooleanPreference(ApplicationPreferences.MAP_RESOURCES_UPDATE_NEEDED, true);
					SplashActivity.newMapVersionDetected = 0;
					Toast.makeText(MainActivity.this,
							"The map has been updated to version " + newVersion, Toast.LENGTH_SHORT)
					.show();
				} else {
					Toast.makeText(MainActivity.this, "An error occurred in updating the map ",
							Toast.LENGTH_SHORT).show();
				}
			}
		});
		alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel_label),
				new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int id) {
				alertDialog.cancel();
			}
		});
		alertDialog.show();
	}

	public enum MapOption {

		MAP_DISPLAY, MAP_STYLES, ANNOTATIONS, MAP_DOWNLOADS, TRACKS,
		ROUTING_AND_NAVIGATION, POI_TRACKING, NAVI_UI, MAP_SECTION
	}

	private enum MapAdvices {
		TEXT_TO_SPEECH, AUDIO_FILES
	}

	public static MenuDrawerItem create(MapOption mapOption, String label, int itemType) {

		MenuDrawerItem menuDrawerItem = new MenuDrawerItem(mapOption);
		menuDrawerItem.setLabel(label);
		menuDrawerItem.setItemType(itemType);
		return menuDrawerItem;

	}   

	@Override
	protected void onDestroy() {
		super.onDestroy();
		currentPositionProvider.stopLocationUpdates();
		SKMaps.getInstance().destroySKMaps();
		if (textToSpeechEngine != null) {
			textToSpeechEngine.stop();
			textToSpeechEngine.shutdown();
		}
	}

	@SuppressLint("ResourceAsColor")
	public void onClick(View v) {

		switch (v.getId()) {

		case R.id.position_me_button:

			if (mapView != null && currentPosition != null) {
				mapView.centerMapOnCurrentPositionSmooth(17, 500);
			} else {
				Toast.makeText(this, getResources().getString(R.string.no_position_available), Toast.LENGTH_SHORT)
				.show();
			}
			break;
		case R.id.calculate_routes_button:
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setHomeButtonEnabled(false);
			launchRouteCalculation(startPoint, destinationPoint);

			new AlertDialog.Builder(this)
			.setMessage("Choose the advice type")
			.setCancelable(false)
			.setPositiveButton("Scout audio", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					bottomButton.setText(getResources().getString(R.string.stop_navigation));
					setAdvicesAndStartNavigation(MapAdvices.AUDIO_FILES);
				}
			})
			.setNegativeButton("Text to speech", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					if (textToSpeechEngine == null) {

						Toast.makeText(MainActivity.this, "Initializing TTS engine",
								Toast.LENGTH_LONG).show();
						textToSpeechEngine = new TextToSpeech(MainActivity.this,
								new TextToSpeech.OnInitListener() {
							@Override
							public void onInit(int status) {
								if (status == TextToSpeech.SUCCESS) {
									int result = textToSpeechEngine.setLanguage(Locale.ENGLISH);
									if (result == TextToSpeech.LANG_MISSING_DATA || result ==
											TextToSpeech.LANG_NOT_SUPPORTED) {
										Toast.makeText(MainActivity.this,
												"This Language is not supported",
												Toast.LENGTH_LONG).show();
									}
								} else {
									Toast.makeText(MainActivity.this, getString(R.string.text_to_speech_engine_not_initialized),
											Toast.LENGTH_SHORT).show();
								}
								bottomButton.setText(getResources().getString(R.string
										.stop_navigation));
								setAdvicesAndStartNavigation(MapAdvices.TEXT_TO_SPEECH);
							}
						});
					} else {
						bottomButton.setText(getResources().getString(R.string.stop_navigation));
						setAdvicesAndStartNavigation(MapAdvices.TEXT_TO_SPEECH);
					}

				}
			})
			.show();
			bottomButton.setText(getResources().getString(R.string.stop_navigation));
			break;			

		case R.id.bottom_button:
			navigationUI.setVisibility(View.VISIBLE);
			currentMapOption = MapOption.NAVI_UI;
			initializeNavigationUI(true);

			break;
		case R.id.navigation_ui_back_button:
			Button backButton = (Button) findViewById(R.id.navigation_ui_back_button);
			LinearLayout naviButtons = (LinearLayout) findViewById(R.id.navigation_ui_buttons);
			if (backButton.getText().equals(">")) {
				naviButtons.setVisibility(View.VISIBLE);
				backButton.setText("<");
			} else {
				naviButtons.setVisibility(View.GONE);
				backButton.setText(">");
			}
			break;
		}

	}

	/**
	 * Launches a single route calculation
	 */
	private void launchRouteCalculation(SKCoordinate startPoint, SKCoordinate destinationPoint) {

		clearRouteFromCache();
		// get a route object and populate it with the desired properties
		SKRouteSettings route = new SKRouteSettings();
		// set start and destination points
		route.setStartCoordinate(startPoint);
		route.setDestinationCoordinate(destinationPoint);
		// set the number of routes to be calculated
		route.setNoOfRoutes(1);//SKRouteSettings [startCoordinate=[33.7158721,73.0677668], destinationCoordinate=[-122.44827,37.738761], routeMode=CAR_FASTEST, alternativeRouteModes=null, routeConnectionMode=HYBRID, downloadRouteCorridor=true, routeCorridorWidthInMeters=2000, waitForCorridorDownload=false, destinationIsPoint=true, tollRoadsAvoided=false, highWaysAvoided=false, avoidFerries=false, noOfRoutes=1, countryCodesReturned=false, extendedPointsReturned=false, viaPoints=null, useRoadSlopes=false, exposeRoute=true, filterAlternatives=false, requestAdvices=true, bicycleWalk= false, bicycleCarryAvoided= false]
		// set the route mode
		route.setRouteMode(SKRouteMode.CAR_SHORTEST);
		// set whether the route should be shown on the map after it's computed
		route.setRouteExposed(true);
		// set the route listener to be notified of route calculation
		// events
		SKRouteManager.getInstance().setRouteListener(this);
		// pass the route to the calculation routine
		SKRouteManager.getInstance().calculateRoute(route);
	}

	/**
	 * Initializes navigation UI menu
	 *
	 * @param showStartingAndDestinationAnnotations
	 */
	private void initializeNavigationUI(boolean showStartingAndDestinationAnnotations) {

		//final ToggleButton selectStartPointBtn = (ToggleButton) findViewById(R.id.select_start_point_button);
		final ToggleButton selectEndPointBtn = (ToggleButton) findViewById(R.id.select_end_point_button);
		startOrientationSensorInPedestrian();

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String prefNavigationType = sharedPreferences.getString(PreferenceTypes.K_NAVIGATION_TYPE,
				"1");

		if (showStartingAndDestinationAnnotations) {
			
			startPoint = new SKCoordinate(currentPosition.getLongitude(), currentPosition.getLatitude());
			SKAnnotation annotation = new SKAnnotation(GREEN_PIN_ICON_ID);
			annotation
			.setAnnotationType(SKAnnotation.SK_ANNOTATION_TYPE_GREEN);
			annotation.setLocation(startPoint);
			mapView.addAnnotation(annotation,
					SKAnimationSettings.ANIMATION_NONE);

			destinationPoint = new SKCoordinate(73.50995268098114,33.398685455322266);
			annotation = new SKAnnotation(RED_PIN_ICON_ID);
			annotation
			.setAnnotationType(SKAnnotation.SK_ANNOTATION_TYPE_RED);
			annotation.setLocation(destinationPoint);
			mapView.addAnnotation(annotation,
					SKAnimationSettings.ANIMATION_NONE);

		}
		mapView.setZoom(11);
		mapView.centerMapOnPosition(startPoint);

		selectEndPointBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					isEndPointBtnPressed = true;
					isStartPointBtnPressed = false;
					Toast.makeText(MainActivity.this, getString(R.string.long_tap_for_position),
							Toast.LENGTH_LONG).show();
				} else {
					isEndPointBtnPressed = false;
				}
			}
		});
		navigationUI.setVisibility(View.VISIBLE);
	}

	@Override
	public void onCurrentPositionUpdate(SKPosition currentPosition) {
		this.currentPosition = currentPosition;
		if (mapView != null) {
			mapView.reportNewGPSPosition(this.currentPosition);
		}
	}

	private void launchNavigation() {
		
		if (TrackElementsActivity.selectedTrackElement != null) {
			
			mapView.clearTrackElement(TrackElementsActivity.selectedTrackElement);
		}
		// get navigation settings object
		SKNavigationSettings navigationSettings = new SKNavigationSettings();
		// set the desired navigation settings
		navigationSettings.setNavigationType(SKNavigationType.REAL);//For Real Navigation
		navigationSettings.setPositionerVerticalAlignment(-0.25f);
		navigationSettings.setShowRealGPSPositions(false);
		// get the navigation manager object
		SKNavigationManager navigationManager = SKNavigationManager.getInstance();
		navigationManager.setMapView(mapView);
		// set listener for navigation events
		navigationManager.setNavigationListener(this);

		// start navigating using the settings
		navigationManager.startNavigation(navigationSettings);
		navigationInProgress = true;
	}

	/**
	 * Setting the audio advices
	 */
	private void setAdvicesAndStartNavigation(MapAdvices currentMapAdvices) {

		final SKAdvisorSettings advisorSettings = new SKAdvisorSettings();
		advisorSettings.setLanguage(SKAdvisorSettings.SKAdvisorLanguage.LANGUAGE_EN);
		advisorSettings.setAdvisorConfigPath(app.getMapResourcesDirPath() + "/Advisor");
		advisorSettings.setResourcePath(app.getMapResourcesDirPath() + "/Advisor/Languages");
		advisorSettings.setAdvisorVoice("en");
		switch (currentMapAdvices) {
		case AUDIO_FILES:
			advisorSettings.setAdvisorType(SKAdvisorSettings.SKAdvisorType.AUDIO_FILES);
			break;
		case TEXT_TO_SPEECH:
			advisorSettings.setAdvisorType(SKAdvisorSettings.SKAdvisorType.TEXT_TO_SPEECH);
			break;
		}
		SKRouteManager.getInstance().setAudioAdvisorSettings(advisorSettings);
		launchNavigation();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mapHolder.onResume();

		if (currentMapOption == MapOption.NAVI_UI) {

			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			String prefNavigationType = sharedPreferences.getString(PreferenceTypes.K_NAVIGATION_TYPE,"1");
		}
	}

	/**
	 * Clears the map
	 */
	private void clearMap() {
		switch (currentMapOption) {
		case MAP_DISPLAY:
			break;
		case TRACKS:
			if (navigationInProgress) {
				// stop the navigation
				stopNavigation();
			}
			bottomButton.setVisibility(View.GONE);
			if (TrackElementsActivity.selectedTrackElement != null) {
				mapView.clearTrackElement(TrackElementsActivity.selectedTrackElement);
				SKRouteManager.getInstance().clearCurrentRoute();
			}
			TrackElementsActivity.selectedTrackElement = null;
			break;
		case ROUTING_AND_NAVIGATION:
			bottomButton.setVisibility(View.GONE);
			SKRouteManager.getInstance().clearCurrentRoute();
			mapView.deleteAllAnnotationsAndCustomPOIs();
			if (navigationInProgress) {
				// stop navigation if ongoing
				stopNavigation();
			}
			break;
		default:
			break;
		}
		currentMapOption = MapOption.MAP_DISPLAY;
		positionMeButton.setVisibility(View.VISIBLE);
	}

	/**
	 * Stops the navigation
	 */
	private void stopNavigation() {
		navigationInProgress = false;
		///routeIds.clear();
		if (textToSpeechEngine != null && !textToSpeechEngine.isSpeaking()) {
			textToSpeechEngine.stop();
		}
		if (currentMapOption.equals(MapOption.TRACKS) && TrackElementsActivity.selectedTrackElement !=
				null) {
			SKRouteManager.getInstance().clearCurrentRoute();
			mapView.drawTrackElement(TrackElementsActivity.selectedTrackElement);
			mapView.fitTrackElementInView(TrackElementsActivity.selectedTrackElement, false);

			SKRouteManager.getInstance().setRouteListener(this);
			SKRouteManager.getInstance().createRouteFromTrackElement(
					TrackElementsActivity.selectedTrackElement, SKRouteMode.BICYCLE_FASTEST, true, true,
					false);
		}
		SKNavigationManager.getInstance().stopNavigation();

	}




	@Override
	public void onDestinationReached() {
		Toast.makeText(MainActivity.this, "Destination reached", Toast.LENGTH_SHORT).show();
		// clear the map when reaching destination
		clearMap();
	}

	@Override
	public void onFreeDriveUpdated(String arg0, String arg1, SKStreetType arg2, double arg3, double arg4) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onReRoutingStarted() {
		// TODO Auto-generated method stub

	}     

	@Override
	public void onSignalNewAdviceWithAudioFiles(String[] audioFiles, boolean specialSoundFile) {

		// a new navigation advice was received
		SKLogging.writeLog(TAG, " onSignalNewAdviceWithAudioFiles " + Arrays.asList(audioFiles), Log.DEBUG);
		SKToolsAdvicePlayer.getInstance().playAdvice(audioFiles, SKToolsAdvicePlayer.PRIORITY_NAVIGATION);
	}

	@Override
	public void onSignalNewAdviceWithInstruction(String instruction) {

		SKLogging.writeLog(TAG, " onSignalNewAdviceWithInstruction " + instruction, Log.DEBUG);
		textToSpeechEngine.speak(instruction, TextToSpeech.QUEUE_ADD, null);
	}

	@Override
	public void onSpeedExceededWithAudioFiles(String[] arg0, boolean arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSpeedExceededWithInstruction(String arg0, boolean arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTunnelEvent(boolean arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUpdateNavigationState(SKNavigationState arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onViaPointReached(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onVisualAdviceChanged(boolean arg0, boolean arg1, SKNavigationState arg2) {
		// TODO Auto-generated method stub

	}

	// route computation callbacks ...
	@Override
	public void onAllRoutesCompleted() {
		/*if (shouldCacheTheNextRoute) {
            shouldCacheTheNextRoute = false;
            SKRouteManager.getInstance().saveRouteToCache(cachedRouteId);
        }*/
		SKRouteManager.getInstance().zoomToRoute(1, 1, 8, 8, 8, 8);
		/*if (currentMapOption == MapOption.POI_TRACKING) {
            // start the POI tracker
            poiTrackingManager.startPOITrackerWithRadius(10000, 0.5);
            // set warning rules for trackable POIs
            poiTrackingManager.addWarningRulesforPoiType(SKTrackablePOIType.SPEEDCAM);
            // launch navigation
            launchNavigation();
        }*/
	}

	@Override
	public void onOnlineRouteComputationHanging(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRouteCalculationCompleted(SKRouteInfo routeInfo) {

		if (currentMapOption == MapOption.ROUTING_AND_NAVIGATION || currentMapOption == MapOption.POI_TRACKING
				|| currentMapOption == MapOption.NAVI_UI) {
			// select the current route (on which navigation will run)
			SKRouteManager.getInstance().setCurrentRouteByUniqueId(routeInfo.getRouteID());
			// zoom to the current route
			SKRouteManager.getInstance().zoomToRoute(1, 1, 8, 8, 8, 8);

			if (currentMapOption == MapOption.ROUTING_AND_NAVIGATION) {
				bottomButton.setText(getResources().getString(R.string.start_navigation));
			}
		} else if (currentMapOption == MapOption.TRACKS) {
			SKRouteManager.getInstance().zoomToRoute(1, 1, 8, 8, 8, 8);
			bottomButton.setVisibility(View.VISIBLE);
			bottomButton.setText(getResources().getString(R.string.start_navigation));
		}
	}

	@Override
	public void onRouteCalculationFailed(SKRoutingErrorCode arg0) {
		shouldCacheTheNextRoute = false;
		Toast.makeText(MainActivity.this, getResources().getString(R.string.route_calculation_failed),
				Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onServerLikeRouteCalculationCompleted(SKRouteJsonAnswer arg0) {
		// TODO Auto-generated method stub

	}//--0-0-0-0-0-00000000000000------------------

	@Override
	public void onActionPan() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onActionZoom() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAnnotationSelected(SKAnnotation arg0) {


		/*if (navigationUI.getVisibility() == View.VISIBLE) {
            return;
        }
        // show the popup at the proper position when selecting an
        // annotation
        int annotationHeight = 0;
        float annotationOffset = annotation.getOffset().getY();
        switch (annotation.getUniqueID()) {
            case 10:
                annotationHeight = annotation.getImageSize();
                popupTitleView.setText("Annotation using texture ID");
                popupDescriptionView.setText(" Red pin");
                break;
            case 13:
                annotationHeight = annotation.getImageSize();
                popupTitleView.setText("Annotation using absolute \n image path");
                popupDescriptionView.setText(null);
                break;
            case 14:
                annotationHeight = annotation.getAnnotationView().getHeight();
                popupTitleView.setText("Annotation using  \n drawable resource ID ");
                popupDescriptionView.setText(null);
                break;
            case 15:
                annotationHeight = customView.getHeight();
                popupTitleView.setText("Annotation using custom view");
                popupDescriptionView.setText(null);
                break;
        }
        mapPopup.setVerticalOffset(-annotationOffset + annotationHeight / 2);
        mapPopup.showAtLocation(annotation.getLocation(), true);*/
	}

	@Override
	public void onBoundingBoxImageRendered(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCompassSelected() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCurrentPositionSelected() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCustomPOISelected(SKMapCustomPOI arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onDoubleTap(SKScreenPoint point) {

		// zoom in on a position when double tapping
		mapView.zoomInAt(point);
	}

	@Override
	public void onGLInitializationError(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onInternationalisationCalled(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onInternetConnectionNeeded() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLongPress(SKScreenPoint point) {

		SKCoordinate poiCoordinates = mapView.pointToCoordinate(point);
		final SKSearchResult place = SKReverseGeocoderManager
				.getInstance().reverseGeocodePosition(poiCoordinates);

		//boolean selectPoint = isStartPointBtnPressed || isEndPointBtnPressed || isViaPointSelected;
		boolean selectPoint = isEndPointBtnPressed;
		if (poiCoordinates != null && place != null && selectPoint) {
			SKAnnotation annotation = new SKAnnotation(GREEN_PIN_ICON_ID);
			if (isStartPointBtnPressed) {
				annotation.setUniqueID(GREEN_PIN_ICON_ID);
				annotation
				.setAnnotationType(SKAnnotation.SK_ANNOTATION_TYPE_GREEN);
				startPoint = currentPosition.getCoordinate();//[73.0678288,33.7158802]
			} else if (isEndPointBtnPressed) {
				annotation.setUniqueID(RED_PIN_ICON_ID);
				annotation
				.setAnnotationType(SKAnnotation.SK_ANNOTATION_TYPE_RED);
				destinationPoint = place.getLocation();//[73.0739974975586,33.72007369995117]
			} /*else if (isViaPointSelected) {
                annotation.setUniqueID(VIA_POINT_ICON_ID);
                annotation.setAnnotationType(SKAnnotation.SK_ANNOTATION_TYPE_MARKER);
                viaPoint = new SKViaPoint(VIA_POINT_ICON_ID, place.getLocation());
                findViewById(R.id.clear_via_point_button).setVisibility(View.VISIBLE);
            }*/

			annotation.setLocation(place.getLocation());
			annotation.setMininumZoomLevel(5);
			mapView.addAnnotation(annotation,
					SKAnimationSettings.ANIMATION_NONE);
		}
	}

	@Override
	public void onMapActionDown(SKScreenPoint arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMapActionUp(SKScreenPoint arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMapPOISelected(SKMapPOI arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMapRegionChangeEnded(SKCoordinateRegion arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMapRegionChangeStarted(SKCoordinateRegion arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMapRegionChanged(SKCoordinateRegion arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onObjectSelected(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onPOIClusterSelected(SKPOICluster arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRotateMap() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSingleTap(SKScreenPoint arg0) {
		// TODO Auto-generated method stub

	}


	/**
	 * Check if there is a cached route at the moment
	 *
	 * @return true if we have a cached route or false otherwise
	 */
	private boolean isRouteCached() {
		return cachedRouteId != null;
	}

	/**
	 * Loads a route from the route cache
	 */
	public void loadRouteFromCache() {
		SKRouteManager.getInstance().loadRouteFromCache(cachedRouteId);
	}

	/**
	 * Cleares the route cache and the correspondent id
	 */
	public void clearRouteFromCache() {
		SKRouteManager.getInstance().clearAllRoutesFromCache();
		cachedRouteId = null;
	}//*/*////////////////////////////********************************************

	@Override
	public void onNavigationEnded() {

		skToolsRouteCalculated = false;
		skToolsNavigationInProgress = false;
		drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setHomeButtonEnabled(true);
		initializeNavigationUI(false);
	}

	@Override
	public void onNavigationStarted() {

		skToolsNavigationInProgress = true;
		if (navigationUI.getVisibility() == View.VISIBLE) {
			navigationUI.setVisibility(View.GONE);
		}
	}

	@Override
	public void onRouteCalculationCanceled() {

		skToolsRouteCalculated = false;
		skToolsNavigationInProgress = false;
		drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		initializeNavigationUI(false);
	}

	@Override
	public void onRouteCalculationCompleted() {

	}

	@Override
	public void onRouteCalculationStarted() {

		skToolsRouteCalculated = true;
	}//**************************************************//
}
