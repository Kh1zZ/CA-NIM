package com.canim.app.data.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Compact latency statistics for an operation or host.
 */
data class LatencyStats(
    val count: Long,
    val totalMs: Long,
    val minMs: Long,
    val maxMs: Long
) {
    val avgMs: Double
        get() = if (count > 0) totalMs.toDouble() / count else 0.0
}

/**
 * Immutable point-in-time snapshot of in-memory observability metrics.
 */
data class MetricsSnapshot(
    val totalRequests: Long,
    val requestsByHost: Map<String, Long>,
    val requestsByOperation: Map<String, Long>,
    val cacheHits: Long,
    val cacheMisses: Long,
    val cacheHitRate: Double,
    val deduplicationCount: Long,
    val retriesByHost: Map<String, Long>,
    val rateLimitsByHost: Map<String, Long>,
    val timeoutsByHost: Map<String, Long>,
    val http5xxByHost: Map<String, Long>,
    val latencyByHost: Map<String, LatencyStats>,
    val latencyByOperation: Map<String, LatencyStats>,
    val cacheHitsByType: Map<String, Long> = emptyMap(),
    val cacheMissesByType: Map<String, Long> = emptyMap(),
    val deduplicationsByHost: Map<String, Long> = emptyMap(),
    val throttledRequestsByHost: Map<String, Long> = emptyMap(),
    val cooldownEventsByHost: Map<String, Long> = emptyMap(),
    val cooldownDurationMsByHost: Map<String, Long> = emptyMap()
) {
    val aniListRequests: Long get() = requestsByHost["anilist"] ?: 0L
    val malRequests: Long get() = requestsByHost["myanimelist"] ?: 0L
    val gitHubRequests: Long get() = requestsByHost["github"] ?: 0L

    val totalErrors: Long get() = http5xxByHost.values.sum()
    val totalRateLimits: Long get() = rateLimitsByHost.values.sum()
    val totalTimeouts: Long get() = timeoutsByHost.values.sum()
    val totalRetries: Long get() = retriesByHost.values.sum()
    val totalThrottled: Long get() = throttledRequestsByHost.values.sum()
    val totalCooldownEvents: Long get() = cooldownEventsByHost.values.sum()
    val totalCooldownDurationMs: Long get() = cooldownDurationMsByHost.values.sum()

    val aniListAvgLatencyMs: Double? get() = latencyByHost["anilist"]?.takeIf { it.count > 0 }?.avgMs
    val malAvgLatencyMs: Double? get() = latencyByHost["myanimelist"]?.takeIf { it.count > 0 }?.avgMs

    /**
     * Formats the metrics snapshot into a sanitized, human-readable summary string
     * suitable for diagnostics display and tests.
     */
    fun toFormattedSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("=== CA'NIM System Observability Snapshot ===")
        sb.appendLine("Total HTTP/GraphQL Requests: $totalRequests")
        if (requestsByHost.isNotEmpty()) {
            sb.appendLine("Requests by Host: " + requestsByHost.entries.joinToString(", ") { "${it.key}=${it.value}" })
        }
        if (requestsByOperation.isNotEmpty()) {
            sb.appendLine("Requests by Operation: " + requestsByOperation.entries.joinToString(", ") { "${it.key}=${it.value}" })
        }
        val totalCache = cacheHits + cacheMisses
        val hitRatePct = if (totalCache > 0) String.format("%.1f%%", cacheHitRate * 100) else "0.0%"
        sb.appendLine("Cache: Hits=$cacheHits, Misses=$cacheMisses, HitRate=$hitRatePct")
        sb.appendLine("In-flight Deduplications: $deduplicationCount")

        val totalRetries = retriesByHost.values.sum()
        sb.appendLine("Retries: $totalRetries" + if (retriesByHost.isNotEmpty()) " (${retriesByHost.entries.joinToString { "${it.key}=${it.value}" }})" else "")

        val total429 = rateLimitsByHost.values.sum()
        sb.appendLine("Rate Limits (429): $total429" + if (rateLimitsByHost.isNotEmpty()) " (${rateLimitsByHost.entries.joinToString { "${it.key}=${it.value}" }})" else "")

        val totalTimeouts = timeoutsByHost.values.sum()
        sb.appendLine("Timeouts: $totalTimeouts" + if (timeoutsByHost.isNotEmpty()) " (${timeoutsByHost.entries.joinToString { "${it.key}=${it.value}" }})" else "")

        val total5xx = http5xxByHost.values.sum()
        sb.appendLine("Server Errors (5xx): $total5xx" + if (http5xxByHost.isNotEmpty()) " (${http5xxByHost.entries.joinToString { "${it.key}=${it.value}" }})" else "")

        if (latencyByHost.isNotEmpty()) {
            sb.appendLine("Latency Summary by Host:")
            latencyByHost.forEach { (host, stats) ->
                sb.appendLine("  • $host: count=${stats.count}, avg=${String.format("%.1f", stats.avgMs)}ms, min=${stats.minMs}ms, max=${stats.maxMs}ms")
            }
        }
        return sb.toString().trimEnd()
    }
}

/**
 * Thread-safe accumulator for latency metrics.
 */
internal class LatencyAccumulator {
    private val count = AtomicLong(0)
    private val totalMs = AtomicLong(0)
    private val minMs = AtomicLong(Long.MAX_VALUE)
    private val maxMs = AtomicLong(0)

    fun record(durationMs: Long) {
        val safeDuration = maxOf(0L, durationMs)
        count.incrementAndGet()
        totalMs.addAndGet(safeDuration)

        // Lock-free CAS loop for min
        while (true) {
            val currentMin = minMs.get()
            if (safeDuration >= currentMin) break
            if (minMs.compareAndSet(currentMin, safeDuration)) break
        }

        // Lock-free CAS loop for max
        while (true) {
            val currentMax = maxMs.get()
            if (safeDuration <= currentMax) break
            if (maxMs.compareAndSet(currentMax, safeDuration)) break
        }
    }

    fun toStats(): LatencyStats {
        val cnt = count.get()
        if (cnt == 0L) {
            return LatencyStats(0L, 0L, 0L, 0L)
        }
        val currentMin = minMs.get()
        val currentMax = maxMs.get()
        return LatencyStats(
            count = cnt,
            totalMs = totalMs.get(),
            minMs = if (currentMin == Long.MAX_VALUE) 0L else currentMin,
            maxMs = currentMax
        )
    }

    fun reset() {
        count.set(0)
        totalMs.set(0)
        minMs.set(Long.MAX_VALUE)
        maxMs.set(0)
    }
}

/**
 * Centralized, lightweight, thread-safe in-memory observability system.
 *
 * Guaranteed zero external analytics dependencies and zero PII storage.
 * All operations and host names are sanitized against raw queries, tokens, and PII.
 */
object AppMetrics {

    private val totalRequests = AtomicLong(0)
    private val requestsByHost = ConcurrentHashMap<String, AtomicLong>()
    private val requestsByOperation = ConcurrentHashMap<String, AtomicLong>()

    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val deduplications = AtomicLong(0)

    private val cacheHitsByType = ConcurrentHashMap<String, AtomicLong>()
    private val cacheMissesByType = ConcurrentHashMap<String, AtomicLong>()

    private val retriesByHost = ConcurrentHashMap<String, AtomicLong>()
    private val retriesByOperation = ConcurrentHashMap<String, AtomicLong>()
    private val rateLimitsByHost = ConcurrentHashMap<String, AtomicLong>()
    private val rateLimitsByOperation = ConcurrentHashMap<String, AtomicLong>()
    private val timeoutsByHost = ConcurrentHashMap<String, AtomicLong>()
    private val timeoutsByOperation = ConcurrentHashMap<String, AtomicLong>()
    private val http5xxByHost = ConcurrentHashMap<String, AtomicLong>()
    private val http5xxByOperation = ConcurrentHashMap<String, AtomicLong>()

    private val latencyByHost = ConcurrentHashMap<String, LatencyAccumulator>()
    private val latencyByOperation = ConcurrentHashMap<String, LatencyAccumulator>()

    /**
     * Sanitizes a metric label (host, operation, cache key) to ensure it does not
     * contain raw queries, tokens, or personal data.
     */
    fun sanitizeLabel(raw: String): String {
        if (raw.isBlank()) return "unknown"
        // Strip URL schemes, query strings, and path parameters
        var label = raw.substringBefore('?').substringBefore('#')
        if (label.contains("://")) {
            label = label.substringAfter("://")
        }
        label = label.substringBefore('/')
        // Keep only alphanumeric characters, underscores, dots, and hyphens
        label = label.replace(Regex("[^a-zA-Z0-9_.-]"), "")
        return if (label.length > 50) label.take(50) else label.ifBlank { "unknown" }
    }

    fun recordRequest(host: String, operation: String = "general") {
        totalRequests.incrementAndGet()
        val safeHost = sanitizeLabel(host)
        val safeOp = sanitizeLabel(operation)
        requestsByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.incrementAndGet()
        requestsByOperation.computeIfAbsent(safeOp) { AtomicLong(0) }.incrementAndGet()
    }

    fun recordLatency(host: String, operation: String, durationMs: Long) {
        val safeHost = sanitizeLabel(host)
        val safeOp = sanitizeLabel(operation)
        latencyByHost.computeIfAbsent(safeHost) { LatencyAccumulator() }.record(durationMs)
        latencyByOperation.computeIfAbsent(safeOp) { LatencyAccumulator() }.record(durationMs)
    }

    fun recordCacheHit(cacheType: String = "metadata") {
        cacheHits.incrementAndGet()
        cacheHitsByType.computeIfAbsent(sanitizeLabel(cacheType)) { AtomicLong(0) }.incrementAndGet()
    }

    fun recordCacheMiss(cacheType: String = "metadata") {
        cacheMisses.incrementAndGet()
        cacheMissesByType.computeIfAbsent(sanitizeLabel(cacheType)) { AtomicLong(0) }.incrementAndGet()
    }

    private val deduplicationsByHost = ConcurrentHashMap<String, AtomicLong>()
    private val deduplicationsByOperation = ConcurrentHashMap<String, AtomicLong>()

    fun recordDeduplication(host: String = "general", operation: String = "general") {
        deduplications.incrementAndGet()
        val safeHost = sanitizeLabel(host)
        val safeOp = sanitizeLabel(operation)
        deduplicationsByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.incrementAndGet()
        deduplicationsByOperation.computeIfAbsent(safeOp) { AtomicLong(0) }.incrementAndGet()
    }

    fun recordRetry(host: String, operation: String = "general") {
        val safeHost = sanitizeLabel(host)
        val safeOp = sanitizeLabel(operation)
        retriesByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.incrementAndGet()
        retriesByOperation.computeIfAbsent(safeOp) { AtomicLong(0) }.incrementAndGet()
    }

    fun recordRateLimit(host: String, operation: String = "general") {
        val safeHost = sanitizeLabel(host)
        val safeOp = sanitizeLabel(operation)
        rateLimitsByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.incrementAndGet()
        rateLimitsByOperation.computeIfAbsent(safeOp) { AtomicLong(0) }.incrementAndGet()
    }

    fun recordTimeout(host: String, operation: String = "general") {
        val safeHost = sanitizeLabel(host)
        val safeOp = sanitizeLabel(operation)
        timeoutsByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.incrementAndGet()
        timeoutsByOperation.computeIfAbsent(safeOp) { AtomicLong(0) }.incrementAndGet()
    }

    private val http5xxByStatusCode = ConcurrentHashMap<Int, AtomicLong>()

    fun recordHttp5xx(host: String, operation: String = "general", statusCode: Int = 500) {
        val safeHost = sanitizeLabel(host)
        val safeOp = sanitizeLabel(operation)
        http5xxByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.incrementAndGet()
        http5xxByOperation.computeIfAbsent(safeOp) { AtomicLong(0) }.incrementAndGet()
        http5xxByStatusCode.computeIfAbsent(statusCode) { AtomicLong(0) }.incrementAndGet()
    }

    private val throttledRequestsByHost = ConcurrentHashMap<String, AtomicLong>()
    private val cooldownEventsByHost = ConcurrentHashMap<String, AtomicLong>()
    private val cooldownDurationMsByHost = ConcurrentHashMap<String, AtomicLong>()

    fun recordLimiterThrottled(host: String) {
        val safeHost = sanitizeLabel(host)
        throttledRequestsByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.incrementAndGet()
    }

    fun recordCooldownEvent(host: String, durationMs: Long) {
        val safeHost = sanitizeLabel(host)
        cooldownEventsByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.incrementAndGet()
        cooldownDurationMsByHost.computeIfAbsent(safeHost) { AtomicLong(0) }.addAndGet(maxOf(0L, durationMs))
    }

    /**
     * Obtains an immutable snapshot of current metrics.
     */
    fun getSnapshot(): MetricsSnapshot {
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val totalCache = hits + misses
        val hitRate = if (totalCache > 0) hits.toDouble() / totalCache else 0.0

        return MetricsSnapshot(
            totalRequests = totalRequests.get(),
            requestsByHost = requestsByHost.mapValues { it.value.get() },
            requestsByOperation = requestsByOperation.mapValues { it.value.get() },
            cacheHits = hits,
            cacheMisses = misses,
            cacheHitRate = hitRate,
            deduplicationCount = deduplications.get(),
            retriesByHost = retriesByHost.mapValues { it.value.get() },
            rateLimitsByHost = rateLimitsByHost.mapValues { it.value.get() },
            timeoutsByHost = timeoutsByHost.mapValues { it.value.get() },
            http5xxByHost = http5xxByHost.mapValues { it.value.get() },
            latencyByHost = latencyByHost.mapValues { it.value.toStats() },
            latencyByOperation = latencyByOperation.mapValues { it.value.toStats() },
            cacheHitsByType = cacheHitsByType.mapValues { it.value.get() },
            cacheMissesByType = cacheMissesByType.mapValues { it.value.get() },
            deduplicationsByHost = deduplicationsByHost.mapValues { it.value.get() },
            throttledRequestsByHost = throttledRequestsByHost.mapValues { it.value.get() },
            cooldownEventsByHost = cooldownEventsByHost.mapValues { it.value.get() },
            cooldownDurationMsByHost = cooldownDurationMsByHost.mapValues { it.value.get() }
        )
    }

    /**
     * Returns a formatted debug string safe for settings screen or logging.
     */
    fun getDebugSummary(): String = getSnapshot().toFormattedSummary()

    /**
     * Resets all in-memory metrics to zero.
     */
    fun reset() {
        totalRequests.set(0)
        requestsByHost.clear()
        requestsByOperation.clear()
        cacheHits.set(0)
        cacheMisses.set(0)
        cacheHitsByType.clear()
        cacheMissesByType.clear()
        deduplications.set(0)
        deduplicationsByHost.clear()
        deduplicationsByOperation.clear()
        retriesByHost.clear()
        retriesByOperation.clear()
        rateLimitsByHost.clear()
        rateLimitsByOperation.clear()
        timeoutsByHost.clear()
        timeoutsByOperation.clear()
        http5xxByHost.clear()
        http5xxByOperation.clear()
        http5xxByStatusCode.clear()
        throttledRequestsByHost.clear()
        cooldownEventsByHost.clear()
        cooldownDurationMsByHost.clear()
        latencyByHost.clear()
        latencyByOperation.clear()
    }
}
