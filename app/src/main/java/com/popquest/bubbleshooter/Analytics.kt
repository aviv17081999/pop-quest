package com.popquest.bubbleshooter

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import org.json.JSONObject

/** Firebase Analytics. Does nothing until google-services.json is added (README step 6). */
class Analytics(ctx: Context) {
    private val fa: FirebaseAnalytics? = runCatching {
        if (FirebaseApp.getApps(ctx).isEmpty()) null else FirebaseAnalytics.getInstance(ctx)
    }.getOrNull()

    fun log(name: String, json: String) {
        val analytics = fa ?: return
        val b = Bundle()
        runCatching {
            val o = JSONObject(json)
            for (k in o.keys()) {
                when (val v = o.get(k)) {
                    is Boolean -> b.putLong(k, if (v) 1 else 0)
                    is Int -> b.putLong(k, v.toLong())
                    is Long -> b.putLong(k, v)
                    is Number -> b.putDouble(k, v.toDouble())
                    else -> b.putString(k, v.toString())
                }
            }
        }
        analytics.logEvent(name, b)
    }
}
