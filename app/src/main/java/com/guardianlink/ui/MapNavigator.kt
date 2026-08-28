package com.guardianlink.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Map actions never assume Google Maps or another dedicated map app is installed. */
object MapNavigator {
    fun openCoordinates(context: Context, latitude: Double, longitude: Double) {
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude"))
        if (geo.resolveActivity(context.packageManager) != null) context.startActivity(geo)
        else openWeb(context, "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=16/$latitude/$longitude")
    }

    fun searchPlace(context: Context, query: String) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        openWeb(context, "https://www.openstreetmap.org/search?query=$encoded")
    }

    private fun openWeb(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
