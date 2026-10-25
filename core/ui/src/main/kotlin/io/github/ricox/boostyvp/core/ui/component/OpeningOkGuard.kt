package io.github.ricox.boostyvp.core.ui.component

/** A new window can receive the opener's UP without ever seeing its DOWN. */
internal class OpeningOkGuard {
    private var waitingForRelease = true
    fun consume(down: Boolean, repeatCount: Int): Boolean {
        if (down && repeatCount == 0) {
            waitingForRelease = false
            return false
        }
        if (down) return true
        if (waitingForRelease) {
            waitingForRelease = false
            return true
        }
        return false
    }
}
