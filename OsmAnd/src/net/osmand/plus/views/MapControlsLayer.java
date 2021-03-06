package net.osmand.plus.views;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.List;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.osmand.core.android.MapRendererContext;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.OsmandSettings.CommonPreference;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.views.controls.MapRouteInfoControl;
import net.osmand.plus.views.controls.MapRoutePreferencesControl;
import net.osmand.plus.views.corenative.NativeCoreContext;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MapControlsLayer extends OsmandMapLayer {

	private static final int TIMEOUT_TO_SHOW_BUTTONS = 7000;

	public MapHudButton createHudButton(View iv, int resId) {
		MapHudButton mc = new MapHudButton();
		mc.iv = iv;
		mc.resId = resId;
		return mc;
	}

	private List<MapHudButton> controls = new ArrayList<MapControlsLayer.MapHudButton>();
	private final MapActivity mapActivity;
	private int shadowColor = -1;
	// private RulerControl rulerControl;
	// private List<MapControls> allControls = new ArrayList<MapControls>();

	private SeekBar transparencyBar;
	private LinearLayout transparencyBarLayout;
	private static CommonPreference<Integer> settingsToTransparency;
	private OsmandSettings settings;

	private MapRoutePreferencesControl optionsRouteControlDialog;
	private MapRouteInfoControl mapRouteInfoControlDialog;
	private View routePreparationLayout;
	private MapHudButton backToLocationControl;
	private MapHudButton menuControl;
	private MapHudButton optionsRouteControl;
	private MapHudButton routeGoControl;
	private MapHudButton compassHud;
	private float cachedRotate = 0;
	private static long startCounter;
	private Runnable delayStart;
	private Handler showUIHandler;
	private ImageView appModeIcon;
	private TextView zoomText;
	private OsmandMapTileView mapView;
	private OsmandApplication app;
	private View mapAppModeShadow;
	private MapHudButton routePlanningBtn;
	private long touchEvent;

	public MapControlsLayer(MapActivity activity) {
		this.mapActivity = activity;
		app = activity.getMyApplication();
		settings = activity.getMyApplication().getSettings();
		mapView = mapActivity.getMapView();
	}

	@Override
	public boolean drawInScreenPixels() {
		return true;
	}

	@Override
	public void initLayer(final OsmandMapTileView view) {
		showUIHandler = new Handler();
		initTopControls();
		initTransparencyBar();
		initZooms();
		initControls();
		initRouteControls();
		updateControls(view.getCurrentRotatedTileBox(), null);
	}

	private class CompassDrawable extends Drawable {

		private Drawable original;

		public CompassDrawable(Drawable original) {
			this.original = original;
		}

		@Override
		public void draw(Canvas canvas) {
			canvas.save();
			canvas.rotate(cachedRotate, getIntrinsicWidth() / 2, getIntrinsicHeight() / 2);
			original.draw(canvas);
			canvas.restore();
		}

		@Override
		public int getMinimumHeight() {
			return original.getMinimumHeight();
		}

		@Override
		public int getMinimumWidth() {
			return original.getMinimumWidth();
		}

		@Override
		public int getIntrinsicHeight() {
			return original.getIntrinsicHeight();
		}

		@Override
		public int getIntrinsicWidth() {
			return original.getIntrinsicWidth();
		}

		@Override
		public void setChangingConfigurations(int configs) {
			super.setChangingConfigurations(configs);
			original.setChangingConfigurations(configs);
		}

		@Override
		public void setBounds(int left, int top, int right, int bottom) {
			super.setBounds(left, top, right, bottom);
			original.setBounds(left, top, right, bottom);
		}

		@Override
		public void setAlpha(int alpha) {
			original.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
			original.setColorFilter(cf);
		}

		@Override
		public int getOpacity() {
			return original.getOpacity();
		}
	}

	private void initTopControls() {
		View configureMap = mapActivity.findViewById(R.id.map_layers_button);
		controls.add(createHudButton((ImageView) configureMap, R.drawable.ic_action_layers_dark).setBg(
				R.drawable.btn_inset_circle, R.drawable.btn_inset_circle_night));
		configureMap.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				mapActivity.getMapActions().prepareConfigureMap();
				mapActivity.getDashboard().setDashboardVisibility(true);
			}
		});

		View compass = mapActivity.findViewById(R.id.map_compass_button);
		compassHud = createHudButton((ImageView) compass, R.drawable.map_compass).setIconColorId(0).setBg(
				R.drawable.btn_inset_circle, R.drawable.btn_inset_circle_night);
		compassHud.compass = true;
		controls.add(compassHud);
		compass.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				mapActivity.getMapViewTrackingUtilities().switchRotateMapMode();
			}
		});

	}

	private void initRouteControls() {
		routePreparationLayout = mapActivity.findViewById(R.id.map_route_preparation_layout);
		View cancelRouteButton = mapActivity.findViewById(R.id.map_cancel_route_button);
		controls.add(createHudButton((ImageView) cancelRouteButton, R.drawable.ic_action_remove_dark).setBg(
				R.drawable.btn_flat, R.drawable.btn_flat_night));

		cancelRouteButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				if (mapActivity.getRoutingHelper().isFollowingMode()) {
					mapActivity.getMapActions().stopNavigationActionConfirm(mapActivity.getMapView());
				} else {
					mapActivity.getMapActions().stopNavigationWithoutConfirm();
				}
			}
		});
		mapRouteInfoControlDialog = new MapRouteInfoControl(mapActivity.getMapLayers().getContextMenuLayer(),
				mapActivity);

		View waypointsButton = mapActivity.findViewById(R.id.map_waypoints_route_button);
		controls.add(createHudButton((ImageView) waypointsButton, R.drawable.ic_action_flage_dark).setBg(
				R.drawable.btn_flat, R.drawable.btn_flat_night));
		waypointsButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				mapRouteInfoControlDialog.showHideDialog();
			}
		});

		View optionsRouteButton = mapActivity.findViewById(R.id.map_options_route_button);
		optionsRouteControl = createHudButton((ImageView) optionsRouteButton,
				settings.getApplicationMode().getSmallIcon(true)).setBg(R.drawable.btn_flat, R.drawable.btn_flat_night);
		optionsRouteControlDialog = new MapRoutePreferencesControl(mapActivity);
		controls.add(optionsRouteControl);
		optionsRouteButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				optionsRouteControlDialog.showAndHideDialog();
			}
		});

		TextView routeGoButton = (TextView) mapActivity.findViewById(R.id.map_go_route_button);
		routeGoControl = createHudButton(routeGoButton, R.drawable.ic_destination_arrow_white).setBg(
				R.drawable.btn_flat, R.drawable.btn_flat_night);
		controls.add(routeGoControl);
		routeGoButton.setText(mapActivity.getString(R.string.shared_string_go).toUpperCase());
		routeGoButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				RoutingHelper routingHelper = mapActivity.getMyApplication().getRoutingHelper();
				if (!routingHelper.isFollowingMode() && !routingHelper.isRoutePlanningMode()) {
					mapActivity.getMapActions().enterRoutePlanningMode(null, null, false);
				} else {
					startNavigation();
				}
			}
		});
	}

	public void showDialog() {
		mapRouteInfoControlDialog.setShowDialog();
	}

	private void initControls() {
		View backToLocation = mapActivity.findViewById(R.id.map_my_location_button);
		backToLocationControl = createHudButton((ImageView) backToLocation, R.drawable.ic_action_get_my_location)
				.setBg(R.drawable.btn_circle_blue);
		controls.add(backToLocationControl);

		backToLocation.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mapActivity.getMapViewTrackingUtilities().backToLocationImpl();
			}
		});

		View backToMenuButton = mapActivity.findViewById(R.id.map_menu_button);
		menuControl = createHudButton((ImageView) backToMenuButton, R.drawable.ic_navigation_drawer).setBg(
				R.drawable.btn_round, R.drawable.btn_round_night);
		controls.add(menuControl);
		backToMenuButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// double lat = activity.getMapView().getLatitude();
				// double lon = activity.getMapView().getLongitude();
				// MainMenuActivity.backToMainMenuDialog(activity, new LatLon(lat, lon));
				notifyClicked();
//				if (mapActivity.getMyApplication().getSettings().USE_DASHBOARD_INSTEAD_OF_DRAWER.get()) {
					mapActivity.getDashboard().setDashboardVisibility(true);
//				} else {
//					mapActivity.getMapActions().onDrawerBack();
//					mapActivity.getMapActions().toggleDrawer();
//				}
			}
		});
		mapAppModeShadow = mapActivity.findViewById(R.id.map_app_mode_shadow);
		mapAppModeShadow.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				onApplicationModePress(v);
			}
		});
		appModeIcon = (ImageView) mapActivity.findViewById(R.id.map_app_mode_icon);
		zoomText = (TextView) mapActivity.findViewById(R.id.map_app_mode_text);

		View routePlanButton = mapActivity.findViewById(R.id.map_route_info_button);
		routePlanningBtn = createHudButton((ImageView) routePlanButton, R.drawable.ic_action_gdirections_dark).setBg(
				R.drawable.btn_round, R.drawable.btn_round_night);
		controls.add(routePlanningBtn);
		routePlanButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				RoutingHelper routingHelper = mapActivity.getRoutingHelper();
				if (!routingHelper.isFollowingMode() && !routingHelper.isRoutePlanningMode()) {
					mapActivity.getMapActions().enterRoutePlanningMode(null, null, false);
				} else {
					mapActivity.getRoutingHelper().setRoutePlanningMode(true);
					mapActivity.getMapViewTrackingUtilities().switchToRoutePlanningMode();
					mapActivity.refreshMap();
				}
			}
		});

	}

	private void initZooms() {
		final OsmandMapTileView view = mapActivity.getMapView();
		View zoomInButton = mapActivity.findViewById(R.id.map_zoom_in_button);
		controls.add(createHudButton((ImageView) zoomInButton, R.drawable.ic_action_zoom_in).setRoundTransparent());
		zoomInButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				if (view.isZooming()) {
					mapActivity.changeZoom(2, System.currentTimeMillis());
				} else {
					mapActivity.changeZoom(1, System.currentTimeMillis());
				}

			}
		});
		final View.OnLongClickListener listener = MapControlsLayer.getOnClickMagnifierListener(view);
		zoomInButton.setOnLongClickListener(listener);
		View zoomOutButton = mapActivity.findViewById(R.id.map_zoom_out_button);
		controls.add(createHudButton((ImageView) zoomOutButton, R.drawable.ic_action_zoom_out).setRoundTransparent());
		zoomOutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				notifyClicked();
				mapActivity.changeZoom(-1, System.currentTimeMillis());
			}
		});
		zoomOutButton.setOnLongClickListener(listener);
	}

	public void startNavigation() {
		stopCounter();
		OsmandApplication app = mapActivity.getMyApplication();
		RoutingHelper routingHelper = app.getRoutingHelper();
		if (routingHelper.isFollowingMode()) {
			routingHelper.setRoutePlanningMode(false);
			mapActivity.getMapViewTrackingUtilities().switchToRoutePlanningMode();
		} else {
			if (!app.getTargetPointsHelper().checkPointToNavigateShort()) {
				mapRouteInfoControlDialog.showDialog();
			} else {
				mapActivity.getMapViewTrackingUtilities().backToLocationImpl();
				app.getSettings().FOLLOW_THE_ROUTE.set(true);
				routingHelper.setFollowingMode(true);
				routingHelper.setRoutePlanningMode(false);
				mapActivity.getMapViewTrackingUtilities().switchToRoutePlanningMode();
				routingHelper.setCurrentLocation(app.getLocationProvider().getLastKnownLocation(), false);
				app.getRoutingHelper().notifyIfRouteIsCalculated();
			}
		}
	}

	private void stopCounter() {
		startCounter = 0;

	}

	public void startCounter() {
		OsmandSettings settings = mapActivity.getMyApplication().getSettings();
		int del = settings.DELAY_TO_START_NAVIGATION.get();
		if (del <= 0) {
			return;
		}
		if (startCounter <= 0) {
			startCounter = System.currentTimeMillis() + del * 1000;
			delayStart = new Runnable() {
				@Override
				public void run() {
					if (startCounter > 0) {
						if (System.currentTimeMillis() > startCounter) {
							startCounter = 0;
							startNavigation();
						} else {
							mapActivity.refreshMap();
							showUIHandler.postDelayed(delayStart, 1000);
						}
					}
				}
			};
			delayStart.run();
		}

	}

	protected void notifyClicked() {
		stopCounter();
	}

	@Override
	public void destroyLayer() {
		controls.clear();
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tileBox, DrawSettings nightMode) {
		updateControls(tileBox, nightMode);
	}

	private void updateControls(RotatedTileBox tileBox, DrawSettings nightMode) {
		boolean isNight = nightMode != null && nightMode.isNightMode();
		int shadw = isNight ? Color.TRANSPARENT : Color.WHITE;
		int textColor = isNight ? mapActivity.getResources().getColor(R.color.widgettext_night) : Color.BLACK;
		if (shadowColor != shadw) {
			shadowColor = shadw;
			// TODO
			// updatextColor(textColor, shadw, rulerControl, zoomControls, mapMenuControls);
		}
		// default buttons
		boolean routePlanningMode = false;
		RoutingHelper rh = mapActivity.getRoutingHelper();
		if (rh.isRoutePlanningMode()) {
			routePlanningMode = true;
		} else if ((rh.isRouteCalculated() || rh.isRouteBeingCalculated()) && !rh.isFollowingMode()) {
			routePlanningMode = true;
		}
		boolean routeFollowingMode = !routePlanningMode && rh.isFollowingMode();
		boolean showRouteCalculationControls = routePlanningMode;

//		boolean showDefaultButtons = !routePlanningMode
//				&& (!routeFollowingMode || settings.SHOW_ZOOM_BUTTONS_NAVIGATION.get());
		boolean showButtons = routePlanningMode || !routeFollowingMode || (System.currentTimeMillis() - touchEvent < TIMEOUT_TO_SHOW_BUTTONS);  
		updateMyLocation(rh);
		routePlanningBtn.setIconResId(routeFollowingMode ?	R.drawable.ic_action_info_dark : R.drawable.ic_action_gdirections_dark	);
		routePlanningBtn.updateVisibility(showButtons && !routePlanningMode);

		menuControl.setIconResId(settings.USE_DASHBOARD_INSTEAD_OF_DRAWER.get() ? R.drawable.ic_dashboard_dark
				: R.drawable.ic_navigation_drawer);
		menuControl.updateVisibility(showButtons);

		if(routeFollowingMode || routePlanningMode) {
			mapAppModeShadow.setVisibility(View.GONE);
		} else {
			mapAppModeShadow.setVisibility(View.VISIBLE);
			if (!mapView.isZooming() || !OsmandPlugin.isDevelopment()) {
				zoomText.setVisibility(View.GONE);
				appModeIcon.setVisibility(View.VISIBLE);
				appModeIcon.setImageResource(settings.getApplicationMode().getSmallIcon(isNight));
			} else {
				zoomText.setVisibility(View.VISIBLE);
				appModeIcon.setVisibility(View.GONE);
				zoomText.setText(getZoomLevel(tileBox));
			}
		}
		optionsRouteControl.setIconResId(settings.getApplicationMode().getSmallIcon(true));
		int vis = showRouteCalculationControls ? View.VISIBLE : View.GONE;
		if (showRouteCalculationControls) {
			((TextView) routeGoControl.iv).setTextColor(textColor);
			final String text;
			if (startCounter > 0) {
				int get = (int) ((startCounter - System.currentTimeMillis()) / 1000l);
				text = mapActivity.getString(R.string.shared_string_go) + " (" + get + ")";
			} else {
				text = mapActivity.getString(R.string.shared_string_go);
			}
			((TextView) routeGoControl.iv).setText(text);
		}

		if (routePreparationLayout.getVisibility() != vis) {
			routePreparationLayout.setVisibility(vis);
			mapRouteInfoControlDialog.setVisible(showRouteCalculationControls);
			if (showRouteCalculationControls) {
				if (!mapActivity.getRoutingHelper().isFollowingMode()
						&& !mapActivity.getRoutingHelper().isPauseNavigation()) {
					startCounter();
				}
			} else {
				stopCounter();
			}
		}

		updateCompass(isNight);

		for (MapHudButton mc : controls) {
			mc.update(mapActivity.getMyApplication(), nightMode == null ? false : nightMode.isNightMode());
		}
	}

	private void updateCompass(boolean isNight) {
		float mapRotate = mapActivity.getMapView().getRotate();
		if (mapRotate != cachedRotate) {
			cachedRotate = mapRotate;
			// Aply animation to image view
			compassHud.iv.invalidate();
		}
		if (settings.ROTATE_MAP.get() == OsmandSettings.ROTATE_MAP_NONE) {
			compassHud.setIconResId(isNight ? R.drawable.map_compass_niu_white : R.drawable.map_compass_niu);
		} else if (settings.ROTATE_MAP.get() == OsmandSettings.ROTATE_MAP_BEARING) {
			compassHud.setIconResId(isNight ? R.drawable.map_compass_bearing_white : R.drawable.map_compass_bearing);
		} else {
			compassHud.setIconResId(isNight ? R.drawable.map_compass_white : R.drawable.map_compass);
		}
	}

	private void updateMyLocation(RoutingHelper rh) {
//		final Drawable backToLoc = map.getResources().getDrawable(R.drawable.back_to_loc);
//		final Drawable backToLocWhite = map.getResources().getDrawable(R.drawable.back_to_loc_white);
//		final Drawable backToLocDisabled = map.getResources().getDrawable(R.drawable.la_backtoloc_disabled);
//		final Drawable backToLocDisabledWhite = map.getResources().getDrawable(R.drawable.la_backtoloc_disabled_white);
//		final Drawable backToLocTracked = map.getResources().getDrawable(R.drawable.back_to_loc_tracked);
//		final Drawable backToLocTrackedWhite = map.getResources().getDrawable(R.drawable.back_to_loc_tracked_white);
		boolean enabled = mapActivity.getMyApplication().getLocationProvider().getLastKnownLocation() != null;
		boolean tracked = mapActivity.getMapViewTrackingUtilities().isMapLinkedToLocation();
		if (!enabled) {
			backToLocationControl.setIconColorId(R.color.icon_color_light);
		} else if (tracked) {
			backToLocationControl.setIconColorId(R.color.color_distance);
		} else {
			backToLocationControl.setIconColorId(R.color.color_white);
		}
		boolean visible = !(tracked && rh.isFollowingMode());
		backToLocationControl.updateVisibility(visible);
	}


	public boolean onSingleTap(PointF point, RotatedTileBox tileBox) {
		if (mapRouteInfoControlDialog.onSingleTap(point, tileBox)) {
			return true;
		}
		stopCounter();
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event, RotatedTileBox tileBox) {
		stopCounter();
		touchEvent = System.currentTimeMillis();
		RoutingHelper rh = mapActivity.getRoutingHelper();
		if (rh.isFollowingMode()) {
			mapActivity.refreshMap();
		}
		return false;
	}

	// /////////////// Transparency bar /////////////////////////
	private void initTransparencyBar() {
		transparencyBarLayout = (LinearLayout) mapActivity.findViewById(R.id.map_transparency_layout);
		transparencyBar = (SeekBar) mapActivity.findViewById(R.id.map_transparency_seekbar);
		transparencyBar.setMax(255);
		if (settingsToTransparency != null) {
			transparencyBar.setProgress(settingsToTransparency.get());
			transparencyBarLayout.setVisibility(View.VISIBLE);
		} else {
			transparencyBarLayout.setVisibility(View.GONE);
		}
		transparencyBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (settingsToTransparency != null) {
					settingsToTransparency.set(progress);
					mapActivity.getMapView().refreshMap();
				}
			}
		});
		ImageButton imageButton = (ImageButton) mapActivity.findViewById(R.id.map_transparency_hide);
		imageButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				transparencyBarLayout.setVisibility(View.GONE);
				hideTransparencyBar(settingsToTransparency);
			}
		});
	}

	public void showTransparencyBar(CommonPreference<Integer> transparenPreference) {
		MapControlsLayer.settingsToTransparency = transparenPreference;
		transparencyBarLayout.setVisibility(View.VISIBLE);
		transparencyBar.setProgress(transparenPreference.get());
	}

	public void hideTransparencyBar(CommonPreference<Integer> transparentPreference) {
		if (settingsToTransparency == transparentPreference) {
			transparencyBarLayout.setVisibility(View.GONE);
			settingsToTransparency = null;
		}
	}

	private class MapHudButton {
		View iv;
		int bgDark;
		int bgLight;
		int resId;
		int resLight = R.color.icon_color_light;
		int resDark = 0;

		boolean nightMode = false;
		boolean f = true;
		boolean compass;
		
		public MapHudButton setRoundTransparent() {
			setBg(R.drawable.btn_circle_trans);
			return this;
		}

		public MapHudButton setBg(int dayBg, int nightBg) {
			bgDark = nightBg;
			bgLight = dayBg;
			f = true;
			return this;
		}
		
		public boolean updateVisibility(boolean visible) {
			if (visible != (iv.getVisibility() == View.VISIBLE)) {
				if (visible) {
					iv.setVisibility(View.VISIBLE);
				} else {
					iv.setVisibility(View.GONE);
				}
				iv.invalidate();
				return true;
			}
			return false;
		}

		public MapHudButton setBg(int bg) {
			bgDark = bg;
			bgLight = bg;
			f = true;
			return this;
		}

		public boolean setIconResId(int resId) {
			if (this.resId == resId) {
				return false;
			}
			this.resId = resId;
			f = true;
			return true;
		}

		public MapHudButton setIconColorId(int clr) {
			if (resLight == clr && resDark == clr) {
				return this;
			}
			resLight = clr;
			resDark = clr;
			f = true;
			return this;
		}

		public void update(OsmandApplication ctx, boolean night) {
			if (nightMode == night && !f) {
				return;
			}
			f = false;
			nightMode = night;
			if (bgDark != 0 && bgLight != 0) {
				iv.setBackgroundDrawable(ctx.getResources().getDrawable(night ? bgDark : bgLight));
			}
			if (iv instanceof ImageView) {
				if (compass) {
					((ImageView) iv).setImageDrawable(new CompassDrawable(ctx.getIconsCache().getIcon(resId,
							nightMode ? resDark : resLight)));
				} else {
					((ImageView) iv).setImageDrawable(ctx.getIconsCache()
							.getIcon(resId, nightMode ? resDark : resLight));
				}
			} else if (iv instanceof TextView) {
				((TextView) iv).setCompoundDrawablesWithIntrinsicBounds(
						ctx.getIconsCache().getIcon(resId, nightMode ? resDark : resLight), null, null, null);
			}
		}

	}

	private void onApplicationModePress(View v) {
		final QuickAction mQuickAction = new QuickAction(v);
		mQuickAction.setOnAnchorOnTop(true);
		List<ApplicationMode> vls = ApplicationMode.values(mapActivity.getMyApplication().getSettings());
		final ApplicationMode[] modes = vls.toArray(new ApplicationMode[vls.size()]);
		int[] icons = new int[vls.size()];
		int[] values = new int[vls.size()];
		for (int k = 0; k < modes.length; k++) {
			icons[k] = modes[k].getSmallIcon(false);
			values[k] = modes[k].getStringResource();
		}
		for (int i = 0; i < modes.length; i++) {
			final ActionItem action = new ActionItem();
			action.setTitle(mapActivity.getResources().getString(values[i]));
			action.setIcon(mapActivity.getResources().getDrawable(icons[i]));
			final int j = i;
			action.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mapActivity.getMyApplication().getSettings().APPLICATION_MODE.set(modes[j]);
					mQuickAction.dismiss();
				}
			});
			mQuickAction.addActionItem(action);
		}
		mQuickAction.setAnimStyle(QuickAction.ANIM_AUTO);
		mQuickAction.show();
	}

	private String getZoomLevel(RotatedTileBox tb) {
		String zoomText = tb.getZoom() + "";
		double frac = tb.getMapDensity();
		if (frac != 0) {
			int ifrac = (int) (frac * 10);
			zoomText += " ";
			zoomText += Math.abs(ifrac) / 10;
			if (ifrac % 10 != 0) {
				zoomText += "." + Math.abs(ifrac) % 10;
			}
		}
		return zoomText;
	}

	public static View.OnLongClickListener getOnClickMagnifierListener(final OsmandMapTileView view) {
		final View.OnLongClickListener listener = new View.OnLongClickListener() {

			@Override
			public boolean onLongClick(View notUseCouldBeNull) {
				final OsmandSettings.OsmandPreference<Float> mapDensity = view.getSettings().MAP_DENSITY;
				final AlertDialog.Builder bld = new AlertDialog.Builder(view.getContext());
				int p = (int) (mapDensity.get() * 100);
				final TIntArrayList tlist = new TIntArrayList(new int[] { 33, 50, 75, 100, 150, 200, 300, 400 });
				final List<String> values = new ArrayList<String>();
				int i = -1;
				for (int k = 0; k <= tlist.size(); k++) {
					final boolean end = k == tlist.size();
					if (i == -1) {
						if ((end || p < tlist.get(k))) {
							values.add(p + "%");
							i = k;
						} else if (p == tlist.get(k)) {
							i = k;
						}

					}
					if (k < tlist.size()) {
						values.add(tlist.get(k) + "%");
					}
				}
				if (values.size() != tlist.size()) {
					tlist.insert(i, p);
				}

				bld.setTitle(R.string.map_magnifier);
				bld.setSingleChoiceItems(values.toArray(new String[values.size()]), i,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								int p = tlist.get(which);
								mapDensity.set(p / 100.0f);
								view.setComplexZoom(view.getZoom(), view.getSettingsMapDensity());
								MapRendererContext mapContext = NativeCoreContext.getMapRendererContext();
								if (mapContext != null) {
									mapContext.updateMapSettings();
								}
								dialog.dismiss();
							}
						});
				bld.show();
				return true;
			}
		};
		return listener;
	}
}
