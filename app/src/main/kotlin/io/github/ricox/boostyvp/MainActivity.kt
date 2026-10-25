package io.github.ricox.boostyvp

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import io.github.ricox.boostyvp.integration.ContentLinks
import io.github.ricox.boostyvp.integration.ContentTarget
import io.github.ricox.boostyvp.integration.CatalogIntegration
import javax.inject.Inject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.navigation.BoostyVPNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(io.github.ricox.boostyvp.core.ui.appearance.LanguagePreferences.wrap(base))
    }
    @Inject lateinit var integration: CatalogIntegration
    private val target = mutableStateOf<ContentTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(io.github.ricox.boostyvp.core.ui.appearance.LanguagePreferences.RECREATE_EXTRA, false) == true) {
            // Recreate normally retains ViewModels; regenerate any already-localized error state.
            viewModelStore.clear()
            intent.removeExtra(io.github.ricox.boostyvp.core.ui.appearance.LanguagePreferences.RECREATE_EXTRA)
        }
        target.value = ContentLinks.read(intent?.data)
        setContent {
            BoostyVPTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoostyVPNavHost(externalTarget = target.value, onExternalHandled = { target.value = null; intent?.data = null })
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); target.value = ContentLinks.read(intent.data) }
    override fun onStart() { super.onStart(); integration.refresh() }
    override fun onStop() { super.onStop(); integration.refresh(fresh = false) }
}
