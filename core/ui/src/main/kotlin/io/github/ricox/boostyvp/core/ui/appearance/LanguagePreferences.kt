package io.github.ricox.boostyvp.core.ui.appearance

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import io.github.ricox.boostyvp.localization.Language

object LanguagePreferences {
    const val RECREATE_EXTRA = "boostyvp_language_restart"
    fun selected(context: Context) = context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE)
        .getString("app_language", "system").takeIf { it in listOf("system", "ru", "en") } ?: "system"
    fun wrap(context: Context): Context {
        val system = Resources.getSystem().configuration
        @Suppress("DEPRECATION")
        Language.systemLocale = if (Build.VERSION.SDK_INT >= 24) system.locales[0] else system.locale
        Language.selected = selected(context)
        val config = Configuration(context.resources.configuration).apply { setLocale(Language.locale) }
        java.util.Locale.setDefault(Language.locale)
        return context.createConfigurationContext(config)
    }
    fun change(context: Context, id: String) {
        require(id in listOf("system", "ru", "en"))
        context.getSharedPreferences("interface_preferences", Context.MODE_PRIVATE).edit().putString("app_language", id).apply()
        Language.selected = id
        var owner: Context = context
        while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
        (owner as? Activity)?.let { activity ->
            activity.intent.putExtra(RECREATE_EXTRA, true)
            activity.recreate()
        }
    }
}
