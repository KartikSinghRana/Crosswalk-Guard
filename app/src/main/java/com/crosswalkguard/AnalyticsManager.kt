package com.crosswalkguard

import android.content.Context
import android.content.SharedPreferences

data class UserStats(
    val distractionsPrevented: Int,
    val crosswalksApproached: Int
)

class AnalyticsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordDistraction()         = increment(KEY_DISTRACTIONS)
    fun recordCrosswalkApproached() = increment(KEY_CROSSWALKS)

    fun getStats() = UserStats(
        distractionsPrevented = prefs.getInt(KEY_DISTRACTIONS, 0),
        crosswalksApproached  = prefs.getInt(KEY_CROSSWALKS, 0)
    )

    private fun increment(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    companion object {
        private const val PREFS            = "cg_stats"
        private const val KEY_DISTRACTIONS = "distractions"
        private const val KEY_CROSSWALKS   = "crosswalks"
    }
}
