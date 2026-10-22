package ru.ardcode.boostytv.feature.player

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.viewinterop.AndroidView

/** Owns the Compose/native focus hand-off for both the real player and its tests. */
@Composable
internal fun TvPlayerHost(
    factory: (Context) -> TvPlayerView,
    modifier: Modifier = Modifier,
    update: (TvPlayerView) -> Unit = {},
    onRelease: (TvPlayerView) -> Unit = {},
) {
    val reference = remember { PlayerViewReference() }
    val focusRequester = remember { FocusRequester() }

    Box(modifier
        .onPreviewKeyEvent {
            reference.view?.dispatchKeyEventFromHost(it.nativeKeyEvent) {
                focusRequester.requestFocus()
            } ?: false
        }
        .focusable(),
    ) {
        AndroidView(
            factory = { context -> factory(context).also { reference.view = it } },
            update = update,
            onRelease = {
                if (reference.view === it) reference.view = null
                onRelease(it)
            },
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
        )
    }

    LaunchedEffect(Unit) {
        // Let the dialog/AndroidView finish attaching before requesting native focus.
        withFrameNanos { }
        focusRequester.requestFocus()
        reference.view?.requestFocus()
    }
}

private class PlayerViewReference(var view: TvPlayerView? = null)
