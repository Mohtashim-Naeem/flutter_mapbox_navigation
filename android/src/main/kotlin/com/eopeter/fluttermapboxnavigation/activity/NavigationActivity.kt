package com.eopeter.fluttermapboxnavigation.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle

import org.json.JSONObject
import androidx.appcompat.app.AppCompatActivity
import com.eopeter.fluttermapboxnavigation.FlutterMapboxNavigationPlugin
import com.eopeter.fluttermapboxnavigation.R
import com.eopeter.fluttermapboxnavigation.databinding.NavigationActivityBinding
import com.eopeter.fluttermapboxnavigation.models.MapBoxEvents
import com.eopeter.fluttermapboxnavigation.models.MapBoxRouteProgressEvent
import com.eopeter.fluttermapboxnavigation.models.Waypoint
import com.eopeter.fluttermapboxnavigation.models.WaypointSet
import com.eopeter.fluttermapboxnavigation.utilities.CustomInfoPanelEndNavButtonBinder
import com.eopeter.fluttermapboxnavigation.utilities.PluginUtilities
import com.google.gson.Gson
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.ui.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.maneuver.model.ManeuverPrimaryOptions
import com.mapbox.navigation.ui.maneuver.model.ManeuverSecondaryOptions
import com.mapbox.navigation.ui.maneuver.model.ManeuverSubOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.RouteLayerConstants
import android.graphics.Color
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import androidx.core.content.ContextCompat
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.OffRouteObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.navigation.dropin.navigationview.NavigationViewListener
import com.mapbox.navigation.utils.internal.ifNonNull

class NavigationActivity : AppCompatActivity() {
    private var finishBroadcastReceiver: BroadcastReceiver? = null
    private var addWayPointsBroadcastReceiver: BroadcastReceiver? = null
    private var points: MutableList<Waypoint> = mutableListOf()
    private var waypointSet: WaypointSet = WaypointSet()
    private var canResetRoute: Boolean = false
    private var accessToken: String? = null
    private var lastLocation: Location? = null
    private var isNavigationInProgress = false

    private val navigationStateListener = object : NavigationViewListener() {
        override fun onFreeDrive() {

        }

        override fun onDestinationPreview() {

        }

        override fun onRoutePreview() {

        }

        override fun onActiveNavigation() {
            isNavigationInProgress = true
        }

        override fun onArrival() {

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
        binding = NavigationActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.navigationView.addListener(navigationStateListener)
        binding.navigationView.registerMapObserver(onMapClick)
        accessToken =
            PluginUtilities.getResourceFromContext(this.applicationContext, "mapbox_access_token")

        val navigationOptions = NavigationOptions.Builder(this.applicationContext)
            .accessToken(accessToken)
            .build()

        MapboxNavigationApp
            .setup(navigationOptions)
            .attach(this)

        if (FlutterMapboxNavigationPlugin.longPressDestinationEnabled) {
            binding.navigationView.registerMapObserver(onMapLongClick)
            binding.navigationView.customizeViewOptions {
                enableMapLongClickIntercept = false
            }
        }

        if (FlutterMapboxNavigationPlugin.enableOnMapTapCallback) {
            binding.navigationView.registerMapObserver(onMapClick)
        }
        val act = this
        // Add custom view binders
        binding.navigationView.customizeViewBinders {
            infoPanelEndNavigationButtonBinder =
                CustomInfoPanelEndNavButtonBinder(act)
        }

        MapboxNavigationApp.current()?.registerBannerInstructionsObserver(this.bannerInstructionObserver)
        MapboxNavigationApp.current()?.registerVoiceInstructionsObserver(this.voiceInstructionObserver)
        MapboxNavigationApp.current()?.registerOffRouteObserver(this.offRouteObserver)
        MapboxNavigationApp.current()?.registerRoutesObserver(this.routesObserver)
        MapboxNavigationApp.current()?.registerLocationObserver(locationObserver)
        MapboxNavigationApp.current()?.registerRouteProgressObserver(routeProgressObserver)
        MapboxNavigationApp.current()?.registerArrivalObserver(arrivalObserver)

        finishBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                finish()
            }
        }

        addWayPointsBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                //get waypoints
                val stops = intent.getSerializableExtra("waypoints") as? MutableList<Waypoint>
                val nextIndex = 1
                if (stops != null) {
                    //append to points
                    if (points.count() >= nextIndex)
                        points.addAll(nextIndex, stops)
                    else
                        points.addAll(stops)
                }
            }
        }

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            finishBroadcastReceiver,
            IntentFilter(NavigationLauncher.KEY_STOP_NAVIGATION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            addWayPointsBroadcastReceiver,
            IntentFilter(NavigationLauncher.KEY_ADD_WAYPOINTS),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // TODO set the style Uri
        var styleUrlDay = FlutterMapboxNavigationPlugin.mapStyleUrlDay
        var styleUrlNight = FlutterMapboxNavigationPlugin.mapStyleUrlNight

        if (styleUrlDay == null) styleUrlDay = Style.MAPBOX_STREETS
        if (styleUrlNight == null) styleUrlNight = Style.DARK
        val passedIsDark = FlutterMapboxNavigationPlugin.isDarkTheme
        val isDark = passedIsDark ?: ((styleUrlDay == styleUrlNight && styleUrlDay != Style.MAPBOX_STREETS) ||
                     styleUrlDay?.lowercase()?.contains("dark") == true)
        val panelBgDrawable = if (isDark) R.drawable.epic_info_panel_bg_dark else R.drawable.epic_info_panel_bg_light
        val progressStyle = if (isDark) R.style.EpicTripProgressStyleDark else R.style.EpicTripProgressStyleLight
        val maneuverStyle = if (isDark) R.style.EpicManeuverStyleDark else R.style.EpicManeuverStyleLight

        // set map style
        binding.navigationView.customizeViewStyles {
            infoPanelBackground = panelBgDrawable
            tripProgressStyle = progressStyle
            cameraModeButtonStyle = if (isDark) R.style.EpicCameraButtonStyleDark else R.style.EpicCameraButtonStyleLight
            audioGuidanceButtonStyle = if (isDark) R.style.EpicAudioButtonStyleDark else R.style.EpicAudioButtonStyleLight
            recenterButtonStyle = if (isDark) R.style.EpicRecenterButtonStyleDark else R.style.EpicRecenterButtonStyleLight
            maneuverViewOptions = ManeuverViewOptions.Builder()
                .maneuverBackgroundColor(
                    if (isDark) R.color.epic_maneuver_bg_dark else R.color.epic_maneuver_bg_light
                )
                .subManeuverBackgroundColor(
                    if (isDark) R.color.epic_maneuver_bg_dark else R.color.epic_maneuver_bg_light
                )
                .upcomingManeuverBackgroundColor(
                    if (isDark) R.color.epic_maneuver_bg_dark else R.color.epic_maneuver_bg_light
                )
                .primaryManeuverOptions(
                    ManeuverPrimaryOptions.Builder()
                        .textAppearance(if (isDark) R.style.EpicManeuverTextAppearanceDark else R.style.EpicManeuverTextAppearanceLight)
                        .build()
                )
                .secondaryManeuverOptions(
                    ManeuverSecondaryOptions.Builder()
                        .textAppearance(if (isDark) R.style.EpicManeuverTextAppearanceDark else R.style.EpicManeuverTextAppearanceLight)
                        .build()
                )
                .subManeuverOptions(
                    ManeuverSubOptions.Builder()
                        .textAppearance(if (isDark) R.style.EpicManeuverTextAppearanceDark else R.style.EpicManeuverTextAppearanceLight)
                        .build()
                )
                .stepDistanceTextAppearance(if (isDark) R.style.EpicManeuverTextAppearanceDark else R.style.EpicManeuverTextAppearanceLight)
                .turnIconManeuver(if (isDark) R.style.EpicManeuverIconStyleDark else R.style.EpicManeuverIconStyleLight)
                .laneGuidanceTurnIconManeuver(if (isDark) R.style.EpicManeuverIconStyleDark else R.style.EpicManeuverIconStyleLight)
                .build()
                
            destinationMarkerAnnotationOptions = PointAnnotationOptions()
                .withIconColor("#61CB08")
        }
        tintDragHandle(binding.navigationView, isDark)
        tintFloatingActionButtons(binding.navigationView, isDark)

        // set map style and route line color
        binding.navigationView.customizeViewOptions {
            mapStyleUriDay = styleUrlDay
            mapStyleUriNight = styleUrlNight
            
            routeLineOptions = MapboxRouteLineOptions.Builder(this@NavigationActivity)
                .withRouteLineResources(
                    RouteLineResources.Builder()
                        .routeLineColorResources(
                            RouteLineColorResources.Builder()
                                .routeDefaultColor(ContextCompat.getColor(this@NavigationActivity, R.color.epic_accent))
                                .routeCasingColor(ContextCompat.getColor(this@NavigationActivity, R.color.epic_border_dark))
                                .routeUnknownCongestionColor(ContextCompat.getColor(this@NavigationActivity, R.color.epic_accent))
                                .routeLowCongestionColor(ContextCompat.getColor(this@NavigationActivity, R.color.epic_accent))
                                .build()
                        )
                        .build()
                )
                .build()
            
            routeArrowOptions = com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions.Builder(this@NavigationActivity)
                .withAboveLayerId(com.mapbox.navigation.ui.maps.route.RouteLayerConstants.TOP_LEVEL_ROUTE_LINE_LAYER_ID)
                .withArrowColor(android.graphics.Color.WHITE)
                .withArrowCasingColor(ContextCompat.getColor(this@NavigationActivity, R.color.epic_accent))
                .build()
        }

        val arrivalRadius = intent.getDoubleExtra("arrivalRadius", -1.0)
        if (arrivalRadius > 0) {
            this.customArrivalRadius = arrivalRadius
            this.hasTriggeredArrival = false
        }
        if (FlutterMapboxNavigationPlugin.enableFreeDriveMode) {
            binding.navigationView.api.routeReplayEnabled(FlutterMapboxNavigationPlugin.simulateRoute)
            binding.navigationView.api.startFreeDrive()
            return
        }

        val p = intent.getSerializableExtra("waypoints") as? MutableList<Waypoint>
        if (p != null) points = p
        points.map { waypointSet.add(it) }
        requestRoutes(waypointSet)

    }

    override fun onDestroy() {
        super.onDestroy()
        if (FlutterMapboxNavigationPlugin.longPressDestinationEnabled) {
            binding.navigationView.unregisterMapObserver(onMapLongClick)
        }
        if (FlutterMapboxNavigationPlugin.enableOnMapTapCallback) {
            binding.navigationView.unregisterMapObserver(onMapClick)
        }
        binding.navigationView.removeListener(navigationStateListener)

        MapboxNavigationApp.current()?.unregisterBannerInstructionsObserver(this.bannerInstructionObserver)
        MapboxNavigationApp.current()?.unregisterVoiceInstructionsObserver(this.voiceInstructionObserver)
        MapboxNavigationApp.current()?.unregisterOffRouteObserver(this.offRouteObserver)
        MapboxNavigationApp.current()?.unregisterRoutesObserver(this.routesObserver)
        MapboxNavigationApp.current()?.unregisterLocationObserver(locationObserver)
        MapboxNavigationApp.current()?.unregisterRouteProgressObserver(routeProgressObserver)
        MapboxNavigationApp.current()?.unregisterArrivalObserver(arrivalObserver)
    }

    fun tryCancelNavigation() {
        if (isNavigationInProgress) {
            isNavigationInProgress = false
            sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
        }
    }

    private fun requestRoutes(waypointSet: WaypointSet) {
        sendEvent(MapBoxEvents.ROUTE_BUILDING)
        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .coordinatesList(waypointSet.coordinatesList())
                .waypointIndicesList(waypointSet.waypointsIndices())
                .waypointNamesList(waypointSet.waypointsNames())
                .language(FlutterMapboxNavigationPlugin.navigationLanguage)
                .alternatives(FlutterMapboxNavigationPlugin.showAlternateRoutes)
                .voiceUnits(FlutterMapboxNavigationPlugin.navigationVoiceUnits)
                .bannerInstructions(FlutterMapboxNavigationPlugin.bannerInstructionsEnabled)
                .voiceInstructions(FlutterMapboxNavigationPlugin.voiceInstructionsEnabled)
                .steps(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    sendEvent(
                        MapBoxEvents.ROUTE_BUILT,
                        Gson().toJson(routes.map { it.directionsRoute.toJson() })
                    )
                    if (routes.isEmpty()) {
                        sendEvent(MapBoxEvents.ROUTE_BUILD_NO_ROUTES_FOUND)
                        return
                    }
                    binding.navigationView.api.routeReplayEnabled(FlutterMapboxNavigationPlugin.simulateRoute)
                    binding.navigationView.api.startActiveGuidance(routes)
                }
            }
        )
    }


    // MultiWaypoint Navigation
    private fun addWaypoint(destination: Point, name: String?) {
        val originLocation = lastLocation
        val originPoint = originLocation?.let {
            Point.fromLngLat(it.longitude, it.latitude)
        } ?: return

        // we always start a route from the current location
        if (addedWaypoints.isEmpty) {
            addedWaypoints.add(Waypoint(originPoint))
        }

        if (!name.isNullOrBlank()) {
            // When you add named waypoints, the string you use here inside "" would be shown in `Maneuver` and played in `Voice` instructions.
            // In this example waypoint names will be visible in the logcat.
            addedWaypoints.add(Waypoint(name, destination))
        } else {
            // When you add silent waypoints, make sure it is followed by a regular or named waypoint, otherwise silent waypoint is treated as a regular waypoint
            addedWaypoints.add(Waypoint(destination, true))
        }

        // execute a route request
        // it's recommended to use the
        // applyDefaultNavigationOptions and applyLanguageAndVoiceUnitOptions
        // that make sure the route request is optimized
        // to allow for support of all of the Navigation SDK features
        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .coordinatesList(addedWaypoints.coordinatesList())
                .waypointIndicesList(addedWaypoints.waypointsIndices())
                .waypointNamesList(addedWaypoints.waypointsNames())
                .alternatives(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    sendEvent(
                        MapBoxEvents.ROUTE_BUILT,
                        Gson().toJson(routes.map { it.directionsRoute.toJson() })
                    )
                    binding.navigationView.api.routeReplayEnabled(true)
                    binding.navigationView.api.startActiveGuidance(routes)
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            }
        )
    }

    // Resets the current route
    private fun resetCurrentRoute() {
//        if (mapboxNavigation.getRoutes().isNotEmpty()) {
//            mapboxNavigation.setRoutes(emptyList()) // reset route
//            addedWaypoints.clear() // reset stored waypoints
//        }
    }

    private fun setRouteAndStartNavigation(routes: List<DirectionsRoute>) {
        // set routes, where the first route in the list is the primary route that
        // will be used for active guidance
        // mapboxNavigation.setRoutes(routes)
    }

    private fun clearRouteAndStopNavigation() {
        // clear
        // mapboxNavigation.setRoutes(listOf())
    }


    /**
     * Helper class that keeps added waypoints and transforms them to the [RouteOptions] params.
     */
    private val addedWaypoints = WaypointSet()


    /**
     * Bindings to the Navigation Activity.
     */
    private lateinit var binding: NavigationActivityBinding// MapboxActivityTurnByTurnExperienceBinding


    private var customArrivalRadius: Double? = null
    private var hasTriggeredArrival = false

    /**
     * Gets notified with progress along the currently active route.
     */
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        //Notify the client
        val progressEvent = MapBoxRouteProgressEvent(routeProgress)
        FlutterMapboxNavigationPlugin.distanceRemaining = routeProgress.distanceRemaining
        FlutterMapboxNavigationPlugin.durationRemaining = routeProgress.durationRemaining
        sendEvent(progressEvent)

        val radius = customArrivalRadius
        if (radius != null && radius > 0 && !hasTriggeredArrival) {
            if (routeProgress.distanceRemaining <= radius) {
                hasTriggeredArrival = true
                isNavigationInProgress = false
                sendEvent(MapBoxEvents.ON_ARRIVAL)
            }
        }
    }

    private val arrivalObserver: ArrivalObserver = object : ArrivalObserver {
        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            isNavigationInProgress = false
            if (!hasTriggeredArrival) {
                hasTriggeredArrival = true
                sendEvent(MapBoxEvents.ON_ARRIVAL)
            }
        }

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {

        }

        override fun onWaypointArrival(routeProgress: RouteProgress) {

        }
    }

    /**
     * Gets notified with location updates.
     *
     * Exposes raw updates coming directly from the location services
     * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
     */
    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            lastLocation = locationMatcherResult.enhancedLocation
        }

        override fun onNewRawLocation(rawLocation: Location) {
            // no impl
        }
    }

    private val bannerInstructionObserver = BannerInstructionsObserver { bannerInstructions ->
        sendEvent(MapBoxEvents.BANNER_INSTRUCTION, bannerInstructions.primary().text())
    }

    private val voiceInstructionObserver = VoiceInstructionsObserver { voiceInstructions ->
        sendEvent(MapBoxEvents.SPEECH_ANNOUNCEMENT, voiceInstructions.announcement().toString())
    }

    private val offRouteObserver = OffRouteObserver { offRoute ->
        if (offRoute) {
            sendEvent(MapBoxEvents.USER_OFF_ROUTE)
        }
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            sendEvent(MapBoxEvents.REROUTE_ALONG);
        }
    }

    /**
     * Notifies with attach and detach events on [MapView]
     */
    private val onMapLongClick = object : MapViewObserver(), OnMapLongClickListener {

        override fun onAttached(mapView: MapView) {
            mapView.gestures.addOnMapLongClickListener(this)
        }

        override fun onDetached(mapView: MapView) {
            mapView.gestures.removeOnMapLongClickListener(this)
        }

        override fun onMapLongClick(point: Point): Boolean {
            ifNonNull(lastLocation) {
                val waypointSet = WaypointSet()
                waypointSet.add(Waypoint(Point.fromLngLat(it.longitude, it.latitude)))
                waypointSet.add(Waypoint(point))
                requestRoutes(waypointSet)
            }
            return false
        }
    }

    /**
     * Notifies with attach and detach events on [MapView]
     */
    private val onMapClick = object : MapViewObserver(), OnMapClickListener {

        override fun onAttached(mapView: MapView) {
            mapView.gestures.addOnMapClickListener(this)
        }

        override fun onDetached(mapView: MapView) {
            mapView.gestures.removeOnMapClickListener(this)
        }

        override fun onMapClick(point: Point): Boolean {
            var waypoint = mapOf<String, String>(
                Pair("latitude", point.latitude().toString()),
                Pair("longitude", point.longitude().toString())
            )
            sendEvent(MapBoxEvents.ON_MAP_TAP, JSONObject(waypoint).toString())
            return false
        }
    }

    private fun sendEvent(event: MapBoxRouteProgressEvent) {}
    private fun sendEvent(event: MapBoxEvents, data: String = "") {}

    private fun tintDragHandle(viewGroup: android.view.View, isDark: Boolean) {
        val color = if (isDark) {
            androidx.core.content.ContextCompat.getColor(viewGroup.context, R.color.epic_text_primary_dark)
        } else {
            androidx.core.content.ContextCompat.getColor(viewGroup.context, R.color.epic_maneuver_icon_light)
        }
        val handleColorList = android.content.res.ColorStateList.valueOf(color)

        fun applyTintToView(v: android.view.View) {
            try {
                v.backgroundTintList = handleColorList
                v.background?.mutate()?.setTint(color)
                if (v is android.widget.ImageView) {
                    v.imageTintList = handleColorList
                    v.drawable?.mutate()?.setTint(color)
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        fun findAndTint(v: android.view.View) {
            val name = try { v.resources.getResourceEntryName(v.id) } catch (e: Exception) { "" }
            val isHandleByName = name.contains("handle", ignoreCase = true) ||
                                 name.contains("drag", ignoreCase = true) ||
                                 name.contains("grab", ignoreCase = true) ||
                                 name.contains("indicator", ignoreCase = true) ||
                                 name.contains("bar", ignoreCase = true)

            val density = v.resources.displayMetrics.density
            val hDp = v.height / density
            val wDp = v.width / density
            val isHandleByDimension = (hDp in 1.0..14.0 && wDp in 12.0..120.0)

            if (isHandleByName || isHandleByDimension) {
                applyTintToView(v)
            }

            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    findAndTint(v.getChildAt(i))
                }
            }
        }

        val runnable = object : Runnable {
            override fun run() {
                findAndTint(viewGroup)
            }
        }

        viewGroup.post(runnable)
        viewGroup.postDelayed(runnable, 300)
        viewGroup.postDelayed(runnable, 800)
        viewGroup.postDelayed(runnable, 1500)
        viewGroup.postDelayed(runnable, 3000)
    }

    private fun tintFloatingActionButtons(viewGroup: android.view.View, isDark: Boolean) {
        val iconColor = if (isDark) {
            androidx.core.content.ContextCompat.getColor(viewGroup.context, R.color.epic_accent)
        } else {
            android.graphics.Color.parseColor("#0B2700")
        }
        val bgColor = if (isDark) {
            androidx.core.content.ContextCompat.getColor(viewGroup.context, R.color.epic_surface_dark)
        } else {
            androidx.core.content.ContextCompat.getColor(viewGroup.context, R.color.epic_maneuver_bg_light)
        }

        val iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        val bgTintList = android.content.res.ColorStateList.valueOf(bgColor)

        fun traverseAndApply(v: android.view.View) {
            val name = try { v.resources.getResourceEntryName(v.id) } catch (e: Exception) { "" }
            val isActionButton = name.contains("camera", ignoreCase = true) ||
                                 name.contains("audio", ignoreCase = true) ||
                                 name.contains("recenter", ignoreCase = true) ||
                                 name.contains("compass", ignoreCase = true) ||
                                 name.contains("sound", ignoreCase = true) ||
                                 name.contains("mute", ignoreCase = true) ||
                                 name.contains("volume", ignoreCase = true) ||
                                 name.contains("action", ignoreCase = true) ||
                                 name.contains("button", ignoreCase = true) ||
                                 name.contains("fab", ignoreCase = true)

            if (isActionButton) {
                try {
                    v.backgroundTintList = bgTintList
                    v.background?.mutate()?.setTint(bgColor)
                } catch (e: Exception) {}
            }

            if (v is android.widget.ImageView) {
                try {
                    v.imageTintList = iconTintList
                    v.drawable?.mutate()?.setTint(iconColor)
                    v.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)
                    androidx.core.graphics.drawable.DrawableCompat.setTint(
                        androidx.core.graphics.drawable.DrawableCompat.wrap(v.drawable!!).mutate(),
                        iconColor
                    )
                } catch (e: Exception) {}
            }

            if (v is android.widget.TextView) {
                try {
                    v.setTextColor(iconColor)
                } catch (e: Exception) {}
            }

            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    traverseAndApply(v.getChildAt(i))
                }
            }
        }

        val runnable = object : Runnable {
            override fun run() {
                traverseAndApply(viewGroup)
            }
        }

        viewGroup.post(runnable)
        viewGroup.postDelayed(runnable, 300)
        viewGroup.postDelayed(runnable, 800)
        viewGroup.postDelayed(runnable, 1500)
        viewGroup.postDelayed(runnable, 3000)
    }
}
