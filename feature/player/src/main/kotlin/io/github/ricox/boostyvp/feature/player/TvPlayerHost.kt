package io.github.ricox.boostyvp.feature.player

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** A native window keeps Media3's D-pad focus independent of Compose interop. */
@Composable
internal fun TvPlayerHost(
    factory: (Context) -> TvPlayerView,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    update: (TvPlayerView) -> Unit = {},
    onRelease: (TvPlayerView) -> Unit = {},
) {
    val reference = remember { PlayerViewReference() }
    val context = LocalContext.current
    val currentUpdate by rememberUpdatedState(update)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentDismiss by rememberUpdatedState(onDismiss)

    // Retain the navigation dialog underneath, including loading/error state and
    // the previous screen's scroll position. Its Compose owner never owns player keys.
    Box(modifier.fillMaxSize())
    DisposableEffect(context) {
        val dialog = ComponentDialog(context, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        val view = factory(dialog.context)
        dialog.setContentView(view, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.setOnDismissListener { currentDismiss() }
        reference.view = view
        currentUpdate(view)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.post { if (view.isAttachedToWindow) view.requestFocus() }

        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            reference.view = null
            currentRelease(view)
        }
    }
    SideEffect { reference.view?.let(currentUpdate) }
}

private class PlayerViewReference(var view: TvPlayerView? = null)
