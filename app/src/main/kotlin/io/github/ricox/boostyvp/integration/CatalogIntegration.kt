package io.github.ricox.boostyvp.integration

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobParameters
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.repository.CatalogIndex
import io.github.ricox.boostyvp.core.domain.repository.PlaybackQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogIntegration @Inject constructor(@ApplicationContext private val context: Context,
    private val account: CurrentAccount, private val catalog: CatalogIndex, private val queue: PlaybackQueue,
    private val channels: TvChannels) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var lastForegroundRefresh = 0L
    private var refreshJob: Job? = null
    fun start() {
        if (started) return
        started = true
        scope.launch { account.key.collectLatest { key ->
            catalog.activate(key); queue.activate(key)
            lastForegroundRefresh = android.os.SystemClock.elapsedRealtime()
            channels.sync(fetchFresh = key != null)
        } }
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.schedule(JobInfo.Builder(0x425650, ComponentName(context, TvChannelJob::class.java))
            .setPeriodic(60 * 60 * 1000L).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build())
    }
    fun refresh(fresh: Boolean = true) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (fresh && now - lastForegroundRefresh < 60_000) return
        if (fresh) lastForegroundRefresh = now
        refreshJob?.cancel()
        refreshJob = scope.launch { delay(300); channels.sync(fetchFresh = fresh) }
    }
}

@AndroidEntryPoint
class TvChannelJob : JobService() {
    @Inject lateinit var channels: TvChannels
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch { channels.sync(); jobFinished(params, false) }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
