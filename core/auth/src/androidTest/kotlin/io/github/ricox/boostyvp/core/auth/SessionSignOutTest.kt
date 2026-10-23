package io.github.ricox.boostyvp.core.auth

import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ricox.boostyvp.core.model.AuthState
import io.github.ricox.boostyvp.core.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The library's isolated test APK holds only this synthetic session. */
@RunWith(AndroidJUnit4::class)
class SessionSignOutTest {
    @Test
    fun signedOutIsEmittedToTheAlreadyVisibleScreenAndSurvivesReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedTokenStore(context, Dispatchers.IO)
        val manager = SessionManager(store, { error("No network in this test") })
        val session = Session("test-access", "test-refresh", Long.MAX_VALUE, "test-device")
        val states = Channel<AuthState>(Channel.UNLIMITED)
        store.save(session)
        val observer = launch { manager.authState.collect { states.send(it) } }
        try {
            assertEquals(AuthState.SignedIn(session), withTimeout(5_000) { states.receive() })
            manager.signOut()
            assertNull(store.peek())
            // A new collector would read null even with the broken clear().
            // The screen that was already subscribed must receive SignedOut.
            assertEquals(AuthState.SignedOut, withTimeout(5_000) { states.receive() })
            assertNull(EncryptedTokenStore(context, Dispatchers.IO).peek())
        } finally {
            observer.cancel()
            store.clear()
        }
    }
}
