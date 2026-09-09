package com.canim.app.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwrCoordinator @Inject constructor() {
    private val swrScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val swrJobs = ConcurrentHashMap<String, Job>()

    private val _events = MutableSharedFlow<CacheRefreshEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<CacheRefreshEvent> = _events.asSharedFlow()

    fun launchSwrJob(key: String, block: suspend CoroutineScope.() -> Unit): Job {
        swrJobs[key]?.cancel()
        val job = swrScope.launch {
            try {
                block()
            } catch (_: Exception) {
                // Background SWR refresh failure is ignored to keep stale cache
            } finally {
                swrJobs.remove(key, coroutineContext[Job])
            }
        }
        swrJobs[key] = job
        return job
    }

    fun getActiveSwrJob(key: String): Job? = swrJobs[key]

    suspend fun emitRefreshEvent(key: String, type: CacheRefreshType) {
        _events.emit(CacheRefreshEvent(key, type))
    }
}
