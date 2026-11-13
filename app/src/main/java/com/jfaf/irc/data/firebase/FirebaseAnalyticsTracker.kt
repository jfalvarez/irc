package com.jfaf.irc.data.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.jfaf.irc.domain.platform.AnalyticsTracker

class FirebaseAnalyticsTracker(
    private val firebaseAnalytics: FirebaseAnalytics
) : AnalyticsTracker {
    override fun logEvent(name: String, params: Map<String, String>) {
        val bundle = if (params.isEmpty()) null
        else Bundle().apply { params.forEach { (k, v) -> putString(k, v) } }
        firebaseAnalytics.logEvent(name, bundle)
    }
}
