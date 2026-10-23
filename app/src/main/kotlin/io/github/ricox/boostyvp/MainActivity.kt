package io.github.ricox.boostyvp

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoostyVPTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoostyVPNavHost()
                }
            }
        }
    }
}
