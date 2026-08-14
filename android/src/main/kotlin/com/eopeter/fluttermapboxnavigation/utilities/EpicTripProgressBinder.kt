package com.eopeter.fluttermapboxnavigation.utilities

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.eopeter.fluttermapboxnavigation.R
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.base.lifecycle.UIBinder
import com.mapbox.navigation.ui.base.lifecycle.UIComponent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Replaces `MapboxTripProgressView` in the info panel with the Epic Rides layout:
 * an exit button on the left, the ETA centred, and a matching-width spacer on the right
 * so the ETA sits on the true centre line.
 *
 * Install via `navigationView.customizeViewBinders { infoPanelTripProgressBinder = ... }`.
 * Note that once this binder is installed the `tripProgressStyle` attributes in styles.xml
 * no longer apply — this layout owns its own typography and colours.
 */
class EpicTripProgressBinder(
    private val isDark: Boolean,
    private val isImperial: Boolean,
    private val onExit: () -> Unit
) : UIBinder {

    override fun bind(viewGroup: ViewGroup): MapboxNavigationObserver {
        val context = viewGroup.context
        viewGroup.removeAllViews()
        val view = LayoutInflater.from(context)
            .inflate(R.layout.epic_trip_progress, viewGroup, false)
        viewGroup.addView(view)

        val exitButton = view.findViewById<View>(R.id.epicExitButton)
        val exitIcon = view.findViewById<ImageView>(R.id.epicExitIcon)
        val etaValue = view.findViewById<TextView>(R.id.epicEtaValue)
        val etaUnit = view.findViewById<TextView>(R.id.epicEtaUnit)
        val etaSecondary = view.findViewById<TextView>(R.id.epicEtaSecondary)

        val primary = ContextCompat.getColor(
            context,
            if (isDark) R.color.epic_eta_primary_dark else R.color.epic_eta_primary_light
        )
        val unit = ContextCompat.getColor(
            context,
            if (isDark) R.color.epic_eta_unit_dark else R.color.epic_eta_unit_light
        )
        val secondary = ContextCompat.getColor(
            context,
            if (isDark) R.color.epic_eta_secondary_dark else R.color.epic_eta_secondary_light
        )
        val icon = ContextCompat.getColor(
            context,
            if (isDark) R.color.epic_eta_icon_dark else R.color.epic_eta_icon_light
        )

        etaValue.setTextColor(primary)
        etaUnit.setTextColor(unit)
        etaSecondary.setTextColor(secondary)
        exitIcon.setColorFilter(icon)
        exitButton.setBackgroundResource(
            if (isDark) R.drawable.epic_circle_button_dark else R.drawable.epic_circle_button_light
        )

        exitButton.setOnClickListener { onExit.invoke() }

        return object : UIComponent() {

            private val progressObserver = RouteProgressObserver { routeProgress ->
                try {
                    val seconds = routeProgress.durationRemaining
                    val meters = routeProgress.distanceRemaining.toDouble()

                    etaValue.text = etaValueText(seconds)
                    etaUnit.text = etaUnitText(seconds)
                    etaSecondary.text = context.getString(
                        R.string.epic_eta_secondary_format,
                        formatDistance(meters),
                        formatArrival(seconds)
                    )
                } catch (e: Exception) {
                    // A malformed progress update must never take down the panel.
                }
            }

            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                super.onAttached(mapboxNavigation)
                mapboxNavigation.registerRouteProgressObserver(progressObserver)
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterRouteProgressObserver(progressObserver)
                super.onDetached(mapboxNavigation)
            }
        }
    }

    private fun etaValueText(seconds: Double): String = when {
        seconds < 60 -> "< 1"
        seconds < 3600 -> (seconds / 60).roundToInt().coerceAtLeast(1).toString()
        else -> {
            val totalMinutes = (seconds / 60).roundToInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            if (minutes == 0) "$hours" else "$hours:${minutes.toString().padStart(2, '0')}"
        }
    }

    private fun etaUnitText(seconds: Double): String =
        if (seconds >= 3600) "hr" else "min"

    private fun formatDistance(meters: Double): String {
        return if (isImperial) {
            val feet = meters * 3.28084
            if (feet < 528) {
                // Round to the nearest 10 ft so the label doesn't flicker every update.
                "${(feet / 10).roundToInt() * 10} ft"
            } else {
                String.format(Locale.getDefault(), "%.1f mi", feet / 5280)
            }
        } else {
            if (meters < 1000) {
                "${(meters / 10).roundToInt() * 10} m"
            } else {
                String.format(Locale.getDefault(), "%.1f km", meters / 1000)
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun formatArrival(secondsRemaining: Double): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, secondsRemaining.roundToInt())
        return SimpleDateFormat("h:mm a", Locale.getDefault())
            .format(calendar.time)
            .lowercase(Locale.getDefault())
    }
}
