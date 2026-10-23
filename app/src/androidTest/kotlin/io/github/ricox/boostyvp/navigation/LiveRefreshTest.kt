package io.github.ricox.boostyvp.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ricox.boostyvp.core.auth.EncryptedTokenStore
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.network.BoostyTokenRefresher
import io.github.ricox.boostyvp.core.network.di.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in: refreshes the TV's existing account, persists rotation, never logs credentials. */
@RunWith(AndroidJUnit4::class)
class LiveRefreshTest {
    @Test fun realRefreshPersistsAndAuthenticatesTheNextRequest() = runBlocking {
        assumeTrue("Opt in with liveRefresh=true after signing into the TV app",
            InstrumentationRegistry.getArguments().getString("liveRefresh") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedTokenStore(context, Dispatchers.IO)
        val before = store.peek()
        assumeTrue("Sign into the TV app before running this check", before != null)
        val refreshClient = NetworkModule.provideRefreshOkHttpClient()
        val json = NetworkModule.provideJson()
        val api = NetworkModule.provideRefreshApi(refreshClient, json)
        val sessions = SessionManager(store, BoostyTokenRefresher(api))
        val client = NetworkModule.provideOkHttpClient(sessions)
        try {
            val refreshed = sessions.refreshIfNeeded(before!!.accessToken)
            val persisted = EncryptedTokenStore(context, Dispatchers.IO).peek()
            // Boolean assertions keep real tokens out of any failure report.
            assertTrue("Refreshed access token was not persisted", persisted?.accessToken == refreshed.accessToken)
            assertTrue("Rotated refresh token was not persisted", persisted?.refreshToken == refreshed.refreshToken)
            assertTrue("Device ID changed", persisted?.deviceId == before.deviceId)
            assertTrue("Refreshed access token is already expired", refreshed.expiresAt > System.currentTimeMillis() / 1000)
            val authenticatedApi = NetworkModule.provideBoostyApi(NetworkModule.provideRetrofit(client, json))
            authenticatedApi.getSubscriptions(limit = 1)
            println("Live refresh OK: pair persisted, device preserved, authenticated subscriptions request succeeded.")
        } finally {
            client.dispatcher.executorService.shutdownNow()
            refreshClient.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            refreshClient.connectionPool.evictAll()
        }
    }
}
