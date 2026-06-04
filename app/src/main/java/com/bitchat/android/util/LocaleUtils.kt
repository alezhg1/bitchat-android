package com.bitchat.android.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtils {
    private val appLocale: Locale = Locale.forLanguageTag("ru")

    fun wrap(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(appLocale)
        return context.createConfigurationContext(config)
    }
}
