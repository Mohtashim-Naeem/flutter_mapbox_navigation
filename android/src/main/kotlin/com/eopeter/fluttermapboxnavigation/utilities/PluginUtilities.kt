package com.eopeter.fluttermapboxnavigation.utilities

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.eopeter.fluttermapboxnavigation.FlutterMapboxNavigationPlugin
import com.eopeter.fluttermapboxnavigation.models.MapBoxEvents
import com.eopeter.fluttermapboxnavigation.models.MapBoxRouteProgressEvent
import io.flutter.plugin.common.MethodCall
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Serializable
import java.util.*

class PluginUtilities {
    companion object {
        @JvmStatic
        fun getResourceFromContext(context: Context?, resName: String): String {
            if (context == null) {
                throw IllegalArgumentException("null context")
            }
            val stringRes = context.resources.getIdentifier(resName, "string", context.packageName)
            if (stringRes == 0) {
                throw IllegalArgumentException(
                    String.format(
                        "The 'R.string.%s' value it's not defined in your project's resources file.",
                        resName
                    )
                )
            }
            return context.getString(stringRes)
        }

        fun formatEventJson(event: MapBoxRouteProgressEvent): String {
            val dataString = event.toJson()
            return "{" +
                    "  \"eventType\": \"${MapBoxEvents.PROGRESS_CHANGE.value}\"," +
                    "  \"data\": $dataString" +
                    "}"
        }

        /**
         * Builds the event envelope sent over the event channel.
         *
         * Two bugs used to live here and both produced malformed JSON that blew up as an
         * unhandled `FormatException` in Dart:
         *
         *  1. Events in [rawJsonEvents] interpolate [data] unquoted because it is expected to
         *     already be JSON. Callers like `sendEvent(MapBoxEvents.USER_OFF_ROUTE)` pass no
         *     data at all, which produced `{"eventType":"user_off_route","data":}`. Off-route
         *     fires constantly with mock/fake locations, so this was easy to hit.
         *  2. The string branch interpolated [data] straight into quotes, so any `"`,
         *     backslash or newline in the payload (route-build error messages, for example)
         *     also produced invalid JSON.
         *
         * [JSONObject.quote] handles the escaping; blank raw payloads become `null`.
         */
        fun formatEventJson(event: MapBoxEvents, data: String = ""): String {
            val rawJsonEvents = setOf(
                MapBoxEvents.MILESTONE_EVENT,
                MapBoxEvents.USER_OFF_ROUTE,
                MapBoxEvents.ROUTE_BUILT,
                MapBoxEvents.ON_MAP_TAP
            )

            val payload = if (rawJsonEvents.contains(event)) {
                if (data.isBlank()) "null" else data
            } else {
                JSONObject.quote(data)
            }

            return "{" +
                    "  \"eventType\": \"${event.value}\"," +
                    "  \"data\": $payload" +
                    "}"
        }

        fun getListOfStringById(key: String, call: MethodCall): ArrayList<String> {
            val logTypesList = arrayListOf<String>()
            call.argument<String>(key)?.let {
                it.split(",").forEach {
                    logTypesList.add(it)
                }
                return logTypesList
            }
            return arrayListOf()
        }

        fun getStringValueById(key: String, call: MethodCall): String {
            call.argument<String>(key)?.let {
                return it
            }
            return ""
        }

        fun getIntValueById(key: String, call: MethodCall): Int? {
            call.argument<Int>(key)?.let {
                return it
            }
            return null
        }

        fun getDoubleValueById(key: String, call: MethodCall): Double? {
            call.argument<Double>(key)?.let {
                return it
            }
            return null
        }

        fun getBoolValueById(key: String, call: MethodCall): Boolean {
            call.argument<Boolean>(key)?.let {
                return it
            }
            return false
        }

        fun getInputStreamValueById(key: String, call: MethodCall): InputStream? {
            call.argument<ByteArray>(key)?.let {
                return ByteArrayInputStream(it)
            }
            return null
        }


        fun getLocaleFromCode(locale: String): Locale {
            val locales: Array<Locale> = Locale.getAvailableLocales()

            val filtered = locales.filter {
                it.language.equals(locale, ignoreCase = true) || it.country.equals(locale, ignoreCase = true)
            }

            return if (filtered.isNotEmpty()) {
                filtered.first()
            } else {
                Locale(locale)
            }
        }

        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nw = connectivityManager.activeNetwork ?: return false
                val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
                return when {
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    // for other device how are able to connect with Ethernet
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                    // for check internet over Bluetooth
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
                    else -> false
                }
            } else {
                val nwInfo = connectivityManager.activeNetworkInfo ?: return false
                return nwInfo.isConnected
            }
        }

        fun <T : Serializable?> getSerializable(
            activity: Activity,
            name: String,
            clazz: Class<T>
        ): T {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                activity.intent.getSerializableExtra(name, clazz)!!
            else
                activity.intent.getSerializableExtra(name) as T
        }
    }

}
