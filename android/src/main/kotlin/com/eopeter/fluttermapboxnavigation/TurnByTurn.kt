package com.eopeter.fluttermapboxnavigation

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.eopeter.fluttermapboxnavigation.databinding.NavigationActivityBinding
import com.eopeter.fluttermapboxnavigation.models.MapBoxEvents
import com.eopeter.fluttermapboxnavigation.models.MapBoxRouteProgressEvent
import com.eopeter.fluttermapboxnavigation.models.Waypoint
import com.eopeter.fluttermapboxnavigation.models.WaypointSet
import com.eopeter.fluttermapboxnavigation.utilities.CustomInfoPanelEndNavButtonBinder
import com.eopeter.fluttermapboxnavigation.utilities.EpicTripProgressBinder
import com.eopeter.fluttermapboxnavigation.utilities.PluginUtilities
import com.google.gson.Gson
import com.mapbox.maps.Style
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.*
import com.mapbox.navigation.ui.base.lifecycle.UIBinder
import com.mapbox.navigation.ui.base.lifecycle.UIComponent
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
import com.mapbox.navigation.ui.tripprogress.model.TripProgressViewOptions
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class SingleResultCompleted(private val result: MethodChannel.Result?) {
    private val completed = AtomicBoolean(false)

    fun success(resultData: Any? = true) {
        if (completed.compareAndSet(false, true)) {
            result?.success(resultData)
        }
    }

    fun error(errorCode: String, errorMessage: String?, errorDetails: Any? = null) {
        if (completed.compareAndSet(false, true)) {
            result?.error(errorCode, errorMessage, errorDetails)
        }
    }

    fun notImplemented() {
        if (completed.compareAndSet(false, true)) {
            result?.notImplemented()
        }
    }
}

open class TurnByTurn(
    ctx: Context,
    act: Activity,
    bind: NavigationActivityBinding,
    accessToken: String
) : MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler,
    Application.ActivityLifecycleCallbacks {

    val isDisposed = AtomicBoolean(false)
    val sessionGeneration = AtomicLong(0L)
    private var eventSink: EventChannel.EventSink? = null

    fun sendEvent(event: MapBoxRouteProgressEvent) {
        if (isDisposed.get()) return
        try {
            val jsonString = PluginUtilities.formatEventJson(event)
            eventSink?.success(jsonString)
        } catch (e: Exception) {
            Log.e("TurnByTurn", "[MapboxLifecycle] Error sending progress event: ${e.message}")
        }
    }

    fun sendEvent(event: MapBoxEvents, data: String = "") {
        if (isDisposed.get()) return
        try {
            val jsonString = PluginUtilities.formatEventJson(event, data)
            eventSink?.success(jsonString)
        } catch (e: Exception) {
            Log.e("TurnByTurn", "[MapboxLifecycle] Error sending event $event: ${e.message}")
        }
    }

    open fun initFlutterChannelHandlers() {
        this.methodChannel?.setMethodCallHandler(this)
        this.eventChannel?.setStreamHandler(this)
    }

    open fun initNavigation() {
        val locale = PluginUtilities.getLocaleFromCode(this.navigationLanguage)
        val unitType = if (this.navigationVoiceUnits == DirectionsCriteria.IMPERIAL) UnitType.IMPERIAL else UnitType.METRIC
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(this.context)
            .locale(locale)
            .unitType(unitType)
            .build()

        val navigationOptions = NavigationOptions.Builder(this.context)
            .accessToken(this.token)
            .distanceFormatterOptions(distanceFormatterOptions)
            .build()

        if (MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.disable()
        }
        MapboxNavigationApp
            .setup(navigationOptions)
            .attach(this.activity as LifecycleOwner)

        // initialize navigation trip observers
        this.registerObservers()

        // Register back press handler once per navigation session.
        // This intercepts the Android back button/gesture so Mapbox's internal
        // back handling (which returns to FreeDrive screen) is bypassed.
        val act = this.activity
        if (act is androidx.activity.ComponentActivity) {
            act.onBackPressedDispatcher.addCallback(act, object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!isDisposed.get()) {
                        finishNavigation()
                    }
                }
            })
        }
    }

    override fun onMethodCall(methodCall: MethodCall, result: MethodChannel.Result) {
        val safeResult = SingleResultCompleted(result)
        if (isDisposed.get() && methodCall.method != "shutdownNavigation") {
            safeResult.error("DISPOSED", "TurnByTurn instance is already disposed", null)
            return
        }
        when (methodCall.method) {
            "getPlatformVersion" -> {
                safeResult.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "enableOfflineRouting" -> {
                safeResult.error("TODO", "Not Implemented in Android", "will implement soon")
            }
            "buildRoute" -> {
                this.buildRoute(methodCall, safeResult)
            }
            "clearRoute" -> {
                this.clearRoute(methodCall, safeResult)
            }
            "startFreeDrive" -> {
                FlutterMapboxNavigationPlugin.enableFreeDriveMode = true
                this.startFreeDrive()
                safeResult.success(true)
            }
            "startNavigation" -> {
                FlutterMapboxNavigationPlugin.enableFreeDriveMode = false
                this.startNavigation(methodCall, safeResult)
            }
            "finishNavigation" -> {
                this.finishNavigation(methodCall, safeResult)
            }
            "shutdownNavigation" -> {
                this.shutdownNavigation(result)
            }
            "getDistanceRemaining" -> {
                safeResult.success(this.distanceRemaining)
            }
            "getDurationRemaining" -> {
                safeResult.success(this.durationRemaining)
            }
            else -> safeResult.notImplemented()
        }
    }

    private fun buildRoute(methodCall: MethodCall, result: SingleResultCompleted) {
        if (isDisposed.get()) {
            result.error("DISPOSED", "TurnByTurn disposed", null)
            return
        }
        this.isNavigationCanceled = false
        val currentGen = sessionGeneration.incrementAndGet()

        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) this.setOptions(arguments)
        this.addedWaypoints.clear()
        val points = arguments?.get("wayPoints") as? HashMap<*, *>
        if (points != null) {
            for (item in points) {
                val point = item.value as HashMap<*, *>
                val latitude = point["Latitude"] as Double
                val longitude = point["Longitude"] as Double
                val isSilent = point["IsSilent"] as Boolean
                this.addedWaypoints.add(Waypoint(Point.fromLngLat(longitude, latitude), isSilent))
            }
        }
        this.getRoute(this.context, currentGen)
        result.success(true)
    }

    private fun getRoute(context: Context, currentGen: Long) {
        val app = MapboxNavigationApp.current()
        if (app == null) {
            Log.e("TurnByTurn", "[MapboxLifecycle] MapboxNavigationApp.current() is null during getRoute")
            sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
            return
        }
        app.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions(navigationMode)
                .applyLanguageAndVoiceUnitOptions(context)
                .coordinatesList(this.addedWaypoints.coordinatesList())
                .waypointIndicesList(this.addedWaypoints.waypointsIndices())
                .waypointNamesList(this.addedWaypoints.waypointsNames())
                .language(navigationLanguage)
                .alternatives(alternatives)
                .steps(true)
                .voiceUnits(navigationVoiceUnits)
                .bannerInstructions(bannerInstructionsEnabled)
                .voiceInstructions(voiceInstructionsEnabled)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    if (isDisposed.get() || sessionGeneration.get() != currentGen) {
                        return
                    }
                    this@TurnByTurn.currentRoutes = routes
                    sendEvent(
                        MapBoxEvents.ROUTE_BUILT,
                        Gson().toJson(routes.map { it.directionsRoute.toJson() })
                    )
                    this@TurnByTurn.binding.navigationView.api.routeReplayEnabled(
                        this@TurnByTurn.simulateRoute
                    )
                    val navView = this@TurnByTurn.binding.navigationView
                    if (navView.width > 0 && navView.height > 0) {
                        navView.api.startActiveGuidance(routes)
                    } else {
                        navView.post {
                            if (!isDisposed.get() && sessionGeneration.get() == currentGen) {
                                navView.api.startActiveGuidance(routes)
                            }
                        }
                    }
                    this@TurnByTurn.binding.navigationView.customizeViewBinders {
                        this.infoPanelEndNavigationButtonBinder = CustomInfoPanelEndNavButtonBinder(this@TurnByTurn.activity) {
                            finishNavigation()
                        }
                        this.infoPanelStartNavigationButtonBinder = UIBinder { viewGroup ->
                            viewGroup.removeAllViews()
                            object : UIComponent() {}
                        }
                    }

                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    if (isDisposed.get() || sessionGeneration.get() != currentGen) return
                    Log.w("TurnByTurn", "[MapboxLifecycle] route request failed: $reasons")
                    sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
                }

                override fun onCanceled(
                    routeOptions: RouteOptions,
                    routerOrigin: RouterOrigin
                ) {
                    if (isDisposed.get() || sessionGeneration.get() != currentGen) return
                    sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            }
        )
    }

    private fun clearRoute(methodCall: MethodCall, result: SingleResultCompleted) {
        if (isDisposed.get()) {
            result.success(true)
            return
        }
        this.currentRoutes = null
        MapboxNavigationApp.current()?.stopTripSession()
        sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
        result.success(true)
    }

    private fun startFreeDrive() {
        this.binding.navigationView.api.startFreeDrive()
    }

    private fun startNavigation(methodCall: MethodCall, result: SingleResultCompleted) {
        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) {
            this.setOptions(arguments)
        }

        val success = this.startNavigation()
        result.success(success)
    }

    private fun finishNavigation(methodCall: MethodCall, result: SingleResultCompleted) {
        this.finishNavigation()
        result.success(true)
    }

    @SuppressLint("MissingPermission")
    private fun startNavigation(): Boolean {
        if (this.isDisposed.get() || this.currentRoutes == null) {
            sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
            return false
        }
        val navView = this.binding.navigationView
        val routes = this.currentRoutes!!
        if (navView.width > 0 && navView.height > 0) {
            navView.api.startActiveGuidance(routes)
        } else {
            navView.post {
                if (!isDisposed.get() && currentRoutes != null) {
                    navView.api.startActiveGuidance(routes)
                }
            }
        }
        sendEvent(MapBoxEvents.NAVIGATION_RUNNING)

        return true
    }

    private fun finishNavigation(isOffRouted: Boolean = false) {
        MapboxNavigationApp.current()?.stopTripSession()
        this.isNavigationCanceled = true
        sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
    }

    fun shutdownNavigation(result: MethodChannel.Result? = null) {
        val safeResult = SingleResultCompleted(result)
        if (!isDisposed.compareAndSet(false, true)) {
            safeResult.success(true)
            return
        }
        sessionGeneration.incrementAndGet()
        try {
            MapboxNavigationApp.current()?.stopTripSession()
            unregisterObservers()
        } catch (e: Exception) {
            Log.e("TurnByTurn", "[MapboxLifecycle] Error during shutdownNavigation: ${e.message}")
        } finally {
            this.currentRoutes = null
            this.eventSink = null
            safeResult.success(true)
        }
    }

    open fun setOptions(arguments: Map<*, *>) {
        val navMode = arguments["mode"] as? String
        if (navMode != null) {
            when (navMode) {
                "walking" -> this.navigationMode = DirectionsCriteria.PROFILE_WALKING
                "cycling" -> this.navigationMode = DirectionsCriteria.PROFILE_CYCLING
                "driving" -> this.navigationMode = DirectionsCriteria.PROFILE_DRIVING
            }
        }

        val simulated = arguments["simulateRoute"] as? Boolean
        if (simulated != null) {
            this.simulateRoute = simulated
        }

        val language = arguments["language"] as? String
        if (language != null) {
            this.navigationLanguage = language
        }

        val units = arguments["units"] as? String

        if (units != null) {
            if (units == "imperial") {
                this.navigationVoiceUnits = DirectionsCriteria.IMPERIAL
            } else if (units == "metric") {
                this.navigationVoiceUnits = DirectionsCriteria.METRIC
            }
        }

        val dayStyle = arguments["mapStyleUrlDay"] as? String
        val nightStyle = arguments["mapStyleUrlNight"] as? String

        if (dayStyle != null) this.mapStyleUrlDay = dayStyle
        if (nightStyle != null) this.mapStyleUrlNight = nightStyle

        if (this.mapStyleUrlDay == null && this.mapStyleUrlNight != null) {
            this.mapStyleUrlDay = this.mapStyleUrlNight
        }
        if (this.mapStyleUrlNight == null && this.mapStyleUrlDay != null) {
            this.mapStyleUrlNight = this.mapStyleUrlDay
        }

        if (this.mapStyleUrlDay == null) this.mapStyleUrlDay = Style.MAPBOX_STREETS
        if (this.mapStyleUrlNight == null) this.mapStyleUrlNight = Style.DARK

        this@TurnByTurn.binding.navigationView.customizeViewOptions {
            mapStyleUriDay = this@TurnByTurn.mapStyleUrlDay
            mapStyleUriNight = this@TurnByTurn.mapStyleUrlNight
            enableMapLongClickIntercept = false
            
            val ctx = this@TurnByTurn.activity
            if (ctx != null) {
                routeLineOptions = MapboxRouteLineOptions.Builder(ctx)
                    .withVanishingRouteLineEnabled(true)
                    .withRouteLineResources(


                        RouteLineResources.Builder()
                            .routeLineColorResources(
                                RouteLineColorResources.Builder()
                                    .routeDefaultColor(ContextCompat.getColor(ctx, R.color.epic_accent))
                                    .routeCasingColor(ContextCompat.getColor(ctx, R.color.epic_border_dark))
                                    .routeUnknownCongestionColor(ContextCompat.getColor(ctx, R.color.epic_accent))
                                    .routeLowCongestionColor(ContextCompat.getColor(ctx, R.color.epic_accent))
                                    .build()
                            )
                            .build()
                    )
                    .build()

                
                routeArrowOptions = com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions.Builder(ctx)
                    .withAboveLayerId(com.mapbox.navigation.ui.maps.route.RouteLayerConstants.TOP_LEVEL_ROUTE_LINE_LAYER_ID)
                    .withArrowColor(android.graphics.Color.WHITE)
                    .withArrowCasingColor(ContextCompat.getColor(ctx, R.color.epic_accent))
                    .build()
            }
        }


        // Detect dark mode: explicitly passed from Flutter, or fallback to map style heuristic
        val passedIsDark = arguments["isDarkTheme"] as? Boolean
        val isDark = passedIsDark ?: ((this.mapStyleUrlDay == this.mapStyleUrlNight &&
                      this.mapStyleUrlDay != Style.MAPBOX_STREETS) ||
                     this.mapStyleUrlDay?.lowercase()?.contains("dark") == true)

        val panelBgDrawable = if (isDark) R.drawable.epic_info_panel_bg_dark else R.drawable.epic_info_panel_bg_light
        val progressStyle = if (isDark) R.style.EpicTripProgressStyleDark else R.style.EpicTripProgressStyleLight

        val context = this@TurnByTurn.activity
        if (context != null) {
            this@TurnByTurn.binding.navigationView.customizeViewStyles {
                infoPanelBackground = panelBgDrawable
                tripProgressStyle = progressStyle
                maneuverViewOptions = ManeuverViewOptions.Builder()
                    .maneuverBackgroundColor(
                        if (isDark) R.color.epic_maneuver_bg_dark else R.color.epic_maneuver_bg_light   
                    )
                    .subManeuverBackgroundColor(
                        if (isDark) R.color.epic_upcoming else R.color.epic_maneuver_bg_light
                    )
                    .upcomingManeuverBackgroundColor(
                        if (isDark) R.color.epic_upcoming else R.color.epic_maneuver_bg_light
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
                    .turnIconManeuver(
                         if (isDark) R.style.EpicManeuverIconStyleDark else R.style.EpicManeuverIconStyleLight
                    )
                    .laneGuidanceTurnIconManeuver(
                         if (isDark) R.style.EpicManeuverIconStyleDark else R.style.EpicManeuverIconStyleLight
                    )
                    .stepDistanceTextAppearance(if (isDark) R.style.EpicManeuverTextAppearanceDark else R.style.EpicManeuverTextAppearanceLight)
                    .build()
                    
                destinationMarkerAnnotationOptions = PointAnnotationOptions()
                    .withIconColor("#61CB08")
                cameraModeButtonStyle = if (isDark) R.style.EpicCameraButtonStyleDark else R.style.EpicCameraButtonStyleLight
                audioGuidanceButtonStyle = if (isDark) R.style.EpicAudioButtonStyleDark else R.style.EpicAudioButtonStyleLight
                recenterButtonStyle = if (isDark) R.style.EpicRecenterButtonStyleDark else R.style.EpicRecenterButtonStyleLight

                // These three were never set, so they kept the SDK defaults — a grey
                // "Arrived" headline that vanishes against the dark info panel, and a
                // white road-name pill with dark text floating over the dark map.
                arrivalTextAppearance =
                    if (isDark) R.style.EpicArrivalTextDark else R.style.EpicArrivalTextLight
                poiNameTextAppearance =
                    if (isDark) R.style.EpicPoiNameTextDark else R.style.EpicPoiNameTextLight
                roadNameTextAppearance =
                    if (isDark) R.style.EpicRoadNameTextDark else R.style.EpicRoadNameTextLight
                roadNameBackground =
                    if (isDark) R.drawable.epic_road_name_bg_dark else R.drawable.epic_road_name_bg_light
            }
        }
        // Swap MapboxTripProgressView for the Epic layout: exit button left, ETA centred.
        this@TurnByTurn.binding.navigationView.customizeViewBinders {
            infoPanelTripProgressBinder = EpicTripProgressBinder(
                isDark = isDark,
                isImperial = this@TurnByTurn.navigationVoiceUnits == DirectionsCriteria.IMPERIAL,
                onExit = { this@TurnByTurn.finishNavigation() }
            )
        }

        tintDragHandle(this@TurnByTurn.binding.navigationView, isDark)
        tintFloatingActionButtons(this@TurnByTurn.binding.navigationView, isDark)

        this.initialLatitude = arguments["initialLatitude"] as? Double
        this.initialLongitude = arguments["initialLongitude"] as? Double

        val zm = arguments["zoom"] as? Double
        if (zm != null) {
            this.zoom = zm
        }

        // NOTE: `arrivalRadius` only drives the plugin's own ON_ARRIVAL event below (see
        // routeProgressObserver). Navigation SDK v2 exposes no way to move the SDK's own
        // arrival threshold — ArrivalOptions/arrivalInMeters existed in v1 and was removed —
        // so Mapbox's "Arrived" info panel will still appear later, at its own detection point.
        val arrivalRadius = (arguments["arrivalRadius"] as? Number)?.toDouble()
        if (arrivalRadius != null && arrivalRadius > 0) {
            this.customArrivalRadius = arrivalRadius
            this.hasTriggeredArrival = false
        }

        val br = arguments["bearing"] as? Double
        if (br != null) {
            this.bearing = br
        }

        val tt = arguments["tilt"] as? Double
        if (tt != null) {
            this.tilt = tt
        }

        val optim = arguments["isOptimized"] as? Boolean
        if (optim != null) {
            this.isOptimized = optim
        }

        val anim = arguments["animateBuildRoute"] as? Boolean
        if (anim != null) {
            this.animateBuildRoute = anim
        }

        val altRoute = arguments["alternatives"] as? Boolean
        if (altRoute != null) {
            this.alternatives = altRoute
        }

        val voiceEnabled = arguments["voiceInstructionsEnabled"] as? Boolean
        if (voiceEnabled != null) {
            this.voiceInstructionsEnabled = voiceEnabled
        }

        val bannerEnabled = arguments["bannerInstructionsEnabled"] as? Boolean
        if (bannerEnabled != null) {
            this.bannerInstructionsEnabled = bannerEnabled
        }

        val longPress = arguments["longPressDestinationEnabled"] as? Boolean
        if (longPress != null) {
            this.longPressDestinationEnabled = longPress
        }

        val onMapTap = arguments["enableOnMapTapCallback"] as? Boolean
        if (onMapTap != null) {
            this.enableOnMapTapCallback = onMapTap
        }
    }

    open fun registerObservers() {
        MapboxNavigationApp.current()?.registerBannerInstructionsObserver(this.bannerInstructionObserver)
        MapboxNavigationApp.current()?.registerVoiceInstructionsObserver(this.voiceInstructionObserver)
        MapboxNavigationApp.current()?.registerOffRouteObserver(this.offRouteObserver)
        MapboxNavigationApp.current()?.registerRoutesObserver(this.routesObserver)
        MapboxNavigationApp.current()?.registerLocationObserver(this.locationObserver)
        MapboxNavigationApp.current()?.registerRouteProgressObserver(this.routeProgressObserver)
        MapboxNavigationApp.current()?.registerArrivalObserver(this.arrivalObserver)
    }

    open fun unregisterObservers() {
        MapboxNavigationApp.current()?.unregisterBannerInstructionsObserver(this.bannerInstructionObserver)
        MapboxNavigationApp.current()?.unregisterVoiceInstructionsObserver(this.voiceInstructionObserver)
        MapboxNavigationApp.current()?.unregisterOffRouteObserver(this.offRouteObserver)
        MapboxNavigationApp.current()?.unregisterRoutesObserver(this.routesObserver)
        MapboxNavigationApp.current()?.unregisterLocationObserver(this.locationObserver)
        MapboxNavigationApp.current()?.unregisterRouteProgressObserver(this.routeProgressObserver)
        MapboxNavigationApp.current()?.unregisterArrivalObserver(this.arrivalObserver)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        if (!isDisposed.get()) {
            this.eventSink = events
        }
    }

    override fun onCancel(arguments: Any?) {
        this.eventSink = null
    }

    private val context: Context = ctx
    val activity: Activity = act
    private val token: String = accessToken
    open var methodChannel: MethodChannel? = null
    open var eventChannel: EventChannel? = null
    private var lastLocation: Location? = null

    private val addedWaypoints = WaypointSet()

    private var initialLatitude: Double? = null
    private var initialLongitude: Double? = null

    private var navigationMode = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    var simulateRoute = false
    private var mapStyleUrlDay: String? = null
    private var mapStyleUrlNight: String? = null
    private var isDarkThemeOverride: Boolean? = null
    private var navigationLanguage = "en"
    private var navigationVoiceUnits = DirectionsCriteria.IMPERIAL
    private var zoom = 15.0
    private var bearing = 0.0
    private var tilt = 0.0
    private var distanceRemaining: Float? = null
    private var durationRemaining: Double? = null

    private var alternatives = true

    var allowsUTurnAtWayPoints = false
    var enableRefresh = false
    private var voiceInstructionsEnabled = true
    private var bannerInstructionsEnabled = true
    private var longPressDestinationEnabled = true
    private var enableOnMapTapCallback = false
    private var animateBuildRoute = true
    private var isOptimized = false

    private var currentRoutes: List<NavigationRoute>? = null
    private var isNavigationCanceled = false

    open val binding: NavigationActivityBinding = bind

    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            if (isDisposed.get()) return
            this@TurnByTurn.lastLocation = locationMatcherResult.enhancedLocation
        }

        override fun onNewRawLocation(rawLocation: Location) {}
    }

    private val bannerInstructionObserver = BannerInstructionsObserver { bannerInstructions ->
        if (isDisposed.get()) return@BannerInstructionsObserver
        sendEvent(MapBoxEvents.BANNER_INSTRUCTION, bannerInstructions.primary().text())
    }

    private val voiceInstructionObserver = VoiceInstructionsObserver { voiceInstructions ->
        if (isDisposed.get()) return@VoiceInstructionsObserver
        sendEvent(MapBoxEvents.SPEECH_ANNOUNCEMENT, voiceInstructions.announcement().toString())
    }

    private val offRouteObserver = OffRouteObserver { offRoute ->
        if (isDisposed.get()) return@OffRouteObserver
        if (offRoute) {
            sendEvent(MapBoxEvents.USER_OFF_ROUTE)
        }
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (isDisposed.get()) return@RoutesObserver
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            sendEvent(MapBoxEvents.REROUTE_ALONG)
        }
    }

    private var customArrivalRadius: Double? = null
    private var hasTriggeredArrival = false

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        if (isDisposed.get() || this.isNavigationCanceled) return@RouteProgressObserver
        try {
            this.distanceRemaining = routeProgress.distanceRemaining
            this.durationRemaining = routeProgress.durationRemaining
            val progressEvent = MapBoxRouteProgressEvent(routeProgress)
            sendEvent(progressEvent)

            val radius = customArrivalRadius
            if (radius != null && radius > 0 && !hasTriggeredArrival) {
                if (routeProgress.distanceRemaining <= radius) {
                    hasTriggeredArrival = true
                    sendEvent(MapBoxEvents.ON_ARRIVAL)
                }
            }
        } catch (_: java.lang.Exception) {}
    }

    private val arrivalObserver: ArrivalObserver = object : ArrivalObserver {
        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            if (isDisposed.get()) return
            if (!hasTriggeredArrival) {
                hasTriggeredArrival = true
                sendEvent(MapBoxEvents.ON_ARRIVAL)
            }
        }

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {}

        override fun onWaypointArrival(routeProgress: RouteProgress) {}
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    private fun tintDragHandle(viewGroup: android.view.View, isDark: Boolean) {
        val color = if (isDark) {
            ContextCompat.getColor(viewGroup.context, R.color.epic_text_primary_dark)
        } else {
            ContextCompat.getColor(viewGroup.context, R.color.epic_maneuver_icon_light)
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

    /**
     * Tints the three floating action buttons (camera mode, audio guidance, recenter).
     *
     * This used to recolour **every** `ImageView` and `TextView` in the whole navigation
     * view — the button name check only gated the background, not the icon and text tint.
     * That repainted the maneuver arrow, the street name, the step distance, the trip
     * progress row and the speed limit in the accent colour, overriding every style in
     * styles.xml. Views created after the last delayed pass (the upcoming-maneuver rows)
     * escaped it, which is why only part of the screen looked wrong.
     *
     * The traversal now stops at each action button and only tints that button's subtree.
     */
    private fun tintFloatingActionButtons(viewGroup: android.view.View, isDark: Boolean) {
        val iconColor = if (isDark) {
            ContextCompat.getColor(viewGroup.context, R.color.epic_text_primary_dark)
        } else {
            ContextCompat.getColor(viewGroup.context, R.color.epic_maneuver_bg_dark)
        }
        val bgColor = if (isDark) {
            ContextCompat.getColor(viewGroup.context, R.color.epic_surface_dark)
        } else {
            ContextCompat.getColor(viewGroup.context, R.color.epic_maneuver_bg_light)
        }

        val iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        val bgTintList = android.content.res.ColorStateList.valueOf(bgColor)

        fun tintButtonSubtree(v: android.view.View) {
            if (v is android.widget.ImageView) {
                try {
                    v.imageTintList = iconTintList
                    v.drawable?.mutate()?.setTint(iconColor)
                    v.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)
                } catch (e: Exception) {}
            }
            if (v is android.widget.TextView) {
                try {
                    v.setTextColor(iconColor)
                } catch (e: Exception) {}
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    tintButtonSubtree(v.getChildAt(i))
                }
            }
        }

        fun traverseAndApply(v: android.view.View) {
            val name = try { v.resources.getResourceEntryName(v.id) } catch (e: Exception) { "" }
            // Deliberately narrow. Generic terms like "button"/"action" also match info-panel
            // containers, which is how this escaped its intended scope in the first place.
            val isActionButton = name.contains("camera", ignoreCase = true) ||
                                 name.contains("audio", ignoreCase = true) ||
                                 name.contains("recenter", ignoreCase = true) ||
                                 name.contains("compass", ignoreCase = true) ||
                                 name.contains("sound", ignoreCase = true) ||
                                 name.contains("mute", ignoreCase = true) ||
                                 name.contains("volume", ignoreCase = true)

            if (isActionButton) {
                try {
                    v.backgroundTintList = bgTintList
                    v.background?.mutate()?.setTint(bgColor)
                } catch (e: Exception) {}
                tintButtonSubtree(v)
                return
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
