package com.eopeter.fluttermapboxnavigation.models.views

import android.app.Activity
import android.content.Context
import android.view.View
import com.eopeter.fluttermapboxnavigation.TurnByTurn
import com.eopeter.fluttermapboxnavigation.FlutterMapboxNavigationPlugin
import com.eopeter.fluttermapboxnavigation.databinding.NavigationActivityBinding
import com.eopeter.fluttermapboxnavigation.models.MapBoxEvents
import com.eopeter.fluttermapboxnavigation.utilities.PluginUtilities
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import org.json.JSONObject

class EmbeddedNavigationMapView(
    context: Context,
    activity: Activity,
    binding: NavigationActivityBinding,
    binaryMessenger: BinaryMessenger,
    vId: Int,
    args: Any?,
    accessToken: String
) : PlatformView, TurnByTurn(context, activity, binding, accessToken) {
    private val viewId: Int = vId
    private val messenger: BinaryMessenger = binaryMessenger
    private val arguments = args as Map<*, *>

    override fun initFlutterChannelHandlers() {
        methodChannel = MethodChannel(messenger, "flutter_mapbox_navigation/${viewId}")
        eventChannel = EventChannel(messenger, "flutter_mapbox_navigation/${viewId}/events")
        super.initFlutterChannelHandlers()
    }

    open fun initialize() {
        initFlutterChannelHandlers()
        initNavigation()

        if ((this.arguments?.get("longPressDestinationEnabled") as? Boolean) == false) {
            this.binding.navigationView.customizeViewOptions {
                enableMapLongClickIntercept = false;
            }
        }

        if ((this.arguments?.get("enableOnMapTapCallback") as? Boolean) == true) {
            this.binding.navigationView.registerMapObserver(onMapClick)
        }
    }

    override fun getView(): View {
        return binding.root
    }

    override fun dispose() {
        try {
            if ((this.arguments?.get("enableOnMapTapCallback") as? Boolean) == true) {
                this.binding.navigationView.unregisterMapObserver(onMapClick)
            }
        } catch (_: Exception) {}

        // Unregister all navigation observers (location, route progress, etc.)
        try {
            unregisterObservers()
        } catch (_: Exception) {}

        // Stop the active trip session to halt simulated/real location updates
        // and prevent background coroutines from firing after the view is destroyed.
        try {
            MapboxNavigationApp.current()?.stopTripSession()
        } catch (_: Exception) {}

        // Clear the event sink so no further events are sent to the Flutter side
        // after the platform view is disposed.
        try {
            FlutterMapboxNavigationPlugin.eventSink = null
        } catch (_: Exception) {}

        // Null out the method/event channels to prevent late method calls
        try {
            methodChannel?.setMethodCallHandler(null)
            eventChannel?.setStreamHandler(null)
            methodChannel = null
            eventChannel = null
        } catch (_: Exception) {}
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
            PluginUtilities.sendEvent(MapBoxEvents.ON_MAP_TAP, JSONObject(waypoint).toString())
            return false
        }
    }

}
