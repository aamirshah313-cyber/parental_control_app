package com.guardianlink.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Google Maps is preferred without embedding a billable Maps SDK or API key. */
object MapNavigator {
    fun openCoordinates(context: Context, latitude: Double, longitude: Double) {
        val query = URLEncoder.encode("$latitude,$longitude", StandardCharsets.UTF_8.name())
        val googleUrl = "https://www.google.com/maps/search/?api=1&query=$query"
        val googleApp = Intent(Intent.ACTION_VIEW, Uri.parse(googleUrl)).setPackage("com.google.android.apps.maps")
        if (googleApp.resolveActivity(context.packageManager) != null) context.startActivity(googleApp)
        else openWeb(context, googleUrl)
    }

    fun searchPlace(context: Context, query: String) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val googleUrl = "https://www.google.com/maps/search/?api=1&query=$encoded"
        val googleApp = Intent(Intent.ACTION_VIEW, Uri.parse(googleUrl)).setPackage("com.google.android.apps.maps")
        if (googleApp.resolveActivity(context.packageManager) != null) context.startActivity(googleApp)
        else openWeb(context, googleUrl)
    }

    private fun openWeb(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
