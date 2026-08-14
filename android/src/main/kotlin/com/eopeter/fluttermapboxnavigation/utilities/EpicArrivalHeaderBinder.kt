package com.eopeter.fluttermapboxnavigation.utilities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.eopeter.fluttermapboxnavigation.R
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.ui.base.lifecycle.UIBinder
import com.mapbox.navigation.ui.base.lifecycle.UIComponent

/**
 * Replaces the SDK's `InfoPanelHeaderArrivalBinder` with the Epic Rides arrival layout.
 *
 * Why this exists: the drop-in info panel renders a *different* header per navigation state.
 * The active-guidance header is `tripProgressLayout + endNavigationButtonLayout`; the arrival
 * header is `arrivedTextContainer + endNavigationButtonLayout` — no trip progress at all.
 *
 * Our close button lives inside `epic_trip_progress.xml`, bound through
 * `infoPanelTripProgressBinder`. So the moment the driver arrived, the panel swapped to the
 * arrival header, the trip progress row was dropped, and the close button went with it —
 * leaving an "Arrived" label the driver could not dismiss.
 *
 * This binder puts the same circular exit button back, in the same position, ahead of the
 * arrival text.
 *
 * Install via `navigationView.customizeViewBinders { infoPanelHeaderArrivalBinder = ... }`.
 */
class EpicArrivalHeaderBinder(
    private val isDark: Boolean,
    private val title: String? = null,
    private val onExit: () -> Unit
) : UIBinder {

    override fun bind(viewGroup: ViewGroup): MapboxNavigationObserver {
        val context = viewGroup.context
        viewGroup.removeAllViews()
        val view = LayoutInflater.from(context)
            .inflate(R.layout.epic_arrival_header, viewGroup, false)
        viewGroup.addView(view)

        val exitButton = view.findViewById<View>(R.id.epicArrivalExitButton)
        val exitIcon = view.findViewById<ImageView>(R.id.epicArrivalExitIcon)
        val titleView = view.findViewById<TextView>(R.id.epicArrivalTitle)

        val primary = ContextCompat.getColor(
            context,
            if (isDark) R.color.epic_eta_primary_dark else R.color.epic_eta_primary_light
        )
        val icon = ContextCompat.getColor(
            context,
            if (isDark) R.color.epic_eta_icon_dark else R.color.epic_eta_icon_light
        )

        titleView.setTextColor(primary)
        titleView.text = title ?: context.getString(R.string.epic_arrived)

        exitIcon.setColorFilter(icon)
        exitButton.setBackgroundResource(
            if (isDark) R.drawable.epic_circle_button_dark else R.drawable.epic_circle_button_light
        )
        exitButton.setOnClickListener { onExit.invoke() }

        // Nothing to observe: the arrival header is static once bound.
        return object : UIComponent() {}
    }
}
