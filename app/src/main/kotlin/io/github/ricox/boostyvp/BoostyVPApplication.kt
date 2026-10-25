package io.github.ricox.boostyvp

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import io.github.ricox.boostyvp.integration.CatalogIntegration
import javax.inject.Inject

@HiltAndroidApp
class BoostyVPApplication : Application(), SingletonImageLoader.Factory {
    // Coil 2 respected HTTP cache directives by default. Preserve that policy
    // after the Coil 3 migration, including private/no-store image responses.
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(cacheStrategy = { CacheControlCacheStrategy() })) }
        .build()

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(io.github.ricox.boostyvp.core.ui.appearance.LanguagePreferences.wrap(base))
    }
    @Inject lateinit var integration: CatalogIntegration
    override fun onCreate() { super.onCreate(); integration.start() }
}
