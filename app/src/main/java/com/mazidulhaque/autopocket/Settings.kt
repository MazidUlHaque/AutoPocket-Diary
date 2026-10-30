package com.mazidulhaque.autopocket

import android.content.Context
import com.mazidulhaque.autopocket.ui.ThemeMode

/** Small user preferences kept on the phone. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        set(v) { prefs.edit().putString("theme", v.name).apply() }

    /** Whether the home-screen widget shows amounts, or only "•••" (anyone can see the home screen). */
    var widgetShowsAmounts: Boolean
        get() = prefs.getBoolean("widget_amounts", true)
        set(v) { prefs.edit().putBoolean("widget_amounts", v).apply() }
}
