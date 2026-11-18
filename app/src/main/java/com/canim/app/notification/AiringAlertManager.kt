package com.canim.app.notification

import android.content.Context
import com.canim.app.data.local.EpisodeNotificationTracker
import com.canim.app.data.model.AiringAnimeItem
import com.canim.app.data.model.UserMediaItem
import com.canim.app.domain.repository.CalendarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks watching anime against the airing schedule and dispatches
 * deduplicated new-episode notifications for library anime.
 */
@Singleton
class AiringAlertManager @Inject constructor(
    private val notificationManager: CanimNotificationManager,
    private val notificationTracker: EpisodeNotificationTracker,
    private val calendarRepository: CalendarRepository,
    @ApplicationContext private val context: Context?
) {
    constructor(
        notificationManager: CanimNotificationManager,
        notificationTracker: EpisodeNotificationTracker,
        calendarRepository: CalendarRepository
    ) : this(notificationManager, notificationTracker, calendarRepository, null)
    /**
     * Checks all currently watching anime in the user's library.
     * If an anime has a new episode aired and hasn't been notified yet, dispatches a notification.
     * Returns the count of notifications sent.
     */
    suspend fun checkAndDispatchAiringAlerts(
        watchingAnimeList: List<UserMediaItem>,
        forceRefresh: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        val watchingAnimeWithMalId = watchingAnimeList.filter { it.malId != null && it.status == "watching" }
        if (watchingAnimeWithMalId.isEmpty()) return@withContext 0

        val watchingMalIds = watchingAnimeWithMalId.mapNotNull { it.malId }.toSet()
        val airingItems = calendarRepository.getWatchingAiringAnime(watchingMalIds, forceRefresh)
        scheduleEarliestNextAiring(airingItems)

        var notifiedCount = 0
        for (airing in airingItems) {
            val malId = airing.malId ?: continue
            val currentEp = airing.currentAiringEpisode ?: continue

            // Only notify if current episode is greater than user's watched progress
            val userItem = watchingAnimeWithMalId.firstOrNull { it.malId == malId }
            val userProgress = userItem?.progress ?: 0

            if (currentEp > userProgress && notificationTracker.shouldNotify(malId, currentEp)) {
                notificationManager.showAiringNotification(
                    animeTitle = airing.title,
                    episodeNumber = currentEp,
                    malId = malId
                )
                notificationTracker.markNotified(malId, currentEp)
                notifiedCount++
            }
        }
        notifiedCount
    }

    /**
     * Checks all plan-to-watch anime in the user's library.
     * If an anime has started airing in the current season and hasn't been notified yet,
     * dispatches a notification that the anime has begun airing.
     * Returns the count of notifications sent.
     */
    suspend fun checkAndDispatchPlanToWatchAiringAlerts(
        planToWatchList: List<UserMediaItem>,
        forceRefresh: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        val planWithMalId = planToWatchList.filter { it.malId != null && it.status == "plan_to_watch" }
        if (planWithMalId.isEmpty()) return@withContext 0

        val planMalIds = planWithMalId.mapNotNull { it.malId }.toSet()
        val airingItems = calendarRepository.getWatchingAiringAnime(planMalIds, forceRefresh)
        scheduleEarliestNextAiring(airingItems)

        var notifiedCount = 0
        for (airing in airingItems) {
            val malId = airing.malId ?: continue
            // Only notify when actively airing this season and Episode 1 has actually aired
            val currentEp = airing.currentAiringEpisode ?: continue
            if (airing.nextAiringAtSeconds == null || currentEp < 1) continue

            if (notificationTracker.shouldNotifyAiringStarted(malId)) {
                val animeTitle = airing.title.ifBlank {
                    planWithMalId.firstOrNull { it.malId == malId }?.title ?: "Anime"
                }
                notificationManager.showPlanToWatchStartedAiringNotification(
                    animeTitle = animeTitle,
                    malId = malId
                )
                notificationTracker.markAiringStartedNotified(malId)
                notifiedCount++
            }
        }
        notifiedCount
    }

    private fun scheduleEarliestNextAiring(airingItems: List<AiringAnimeItem>) {
        val ctx = context ?: return
        val nowMs = System.currentTimeMillis()
        val earliestMs = airingItems
            .mapNotNull { it.nextAiringAtSeconds?.times(1000L) }
            .filter { it > nowMs }
            .minOrNull() ?: return
        NotificationScheduler.scheduleNextAiringCheck(ctx, earliestMs)
    }
}
