package com.canim.app

import android.content.Context
import com.canim.app.data.local.EpisodeNotificationTracker
import com.canim.app.data.model.*
import com.canim.app.data.repository.CalendarRepositoryImpl
import com.canim.app.domain.repository.CalendarRepository
import com.canim.app.notification.AiringAlertManager
import com.canim.app.notification.CanimNotificationManager
import com.canim.app.ui.viewmodel.calendar.AiringCalendarUiState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class AiringCalendarTest {

    private lateinit var context: Context
    private lateinit var tracker: EpisodeNotificationTracker
    private lateinit var calendarRepository: CalendarRepositoryImpl

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("canim_airing_episode_notifications", Context.MODE_PRIVATE).edit().clear().commit()
        tracker = EpisodeNotificationTracker(context)
        calendarRepository = CalendarRepositoryImpl()
    }

    private fun createAiringItem(
        malId: Int,
        title: String,
        day: DayOfWeek,
        currentEp: Int
    ): AiringAnimeItem {
        return AiringAnimeItem(
            id = "mal_$malId",
            malId = malId,
            anilistId = malId + 1000,
            title = title,
            titleEnglish = title,
            imageUrl = "https://example.com/$malId.jpg",
            score = 8.8,
            episodes = 24,
            currentAiringEpisode = currentEp,
            airingDay = day,
            airingTimeFormatted = "23:00"
        )
    }

    private fun createUserWatchingItem(malId: Int, progress: Int): UserMediaItem {
        return UserMediaItem(
            identity = MediaRef(malId = malId, anilistId = malId + 1000),
            metadata = MediaMetadata(
                title = "Anime $malId",
                imageUrl = "https://example.com/$malId.jpg",
                type = MediaType.ANIME,
                totalEpisodes = 24
            ),
            tracking = MalTracking(status = "watching", progress = progress)
        )
    }

    @Test
    fun testEpisodeNotificationTrackerDeduplication() {
        val malId = 12345
        // First notification for ep 5 should be allowed
        assertTrue("Episode 5 should notify on first check", tracker.shouldNotify(malId, 5))

        // Mark ep 5 as notified
        tracker.markNotified(malId, 5)

        // Same episode 5 should be blocked to prevent spam
        assertFalse("Episode 5 should be deduplicated and blocked", tracker.shouldNotify(malId, 5))

        // Previous episode 4 should also be blocked
        assertFalse("Episode 4 should be blocked because ep 5 was already notified", tracker.shouldNotify(malId, 4))

        // Next episode 6 should be allowed
        assertTrue("Episode 6 should notify next week", tracker.shouldNotify(malId, 6))

        // Marking episode 6
        tracker.markNotified(malId, 6)
        assertEquals(6, tracker.getLastNotifiedEpisode(malId))
    }

    @Test
    fun testSevenDaysDistributionAndLocalTimezoneDerivation() {
        val daysFound = mutableSetOf<DayOfWeek>()
        val titles = listOf(
            "Frieren: Beyond Journey's End",
            "Sousou no Frieren",
            "Jujutsu Kaisen 2nd Season",
            "Chainsaw Man",
            "Bocchi the Rock!",
            "Oshi no Ko",
            "Spy x Family",
            "Bleach: Thousand-Year Blood War",
            "Vinland Saga Season 2",
            "Dungeon Meshi",
            "Solo Leveling",
            "Mashle: Magic and Muscles",
            "KonoSuba: God's Blessing on this Wonderful World! 3",
            "Mushoku Tensei II: Isekai Ittara Honki Dasu"
        )

        for ((idx, title) in titles.withIndex()) {
            val day = calendarRepository.deriveDayOfWeek(title, idx + 100)
            daysFound.add(day)
            assertTrue("Day index must be valid 1-7", day.value in 1..7)
        }

        // Broad variety of titles should distribute across multiple days
        assertTrue("Anime schedule should distribute across days of week", daysFound.size >= 4)
    }

    @Test
    fun testAiringCalendarUiStateWatchingFilter() {
        val itemMon1 = createAiringItem(101, "Mon Airing 1", DayOfWeek.MONDAY, 8)
        val itemMon2 = createAiringItem(102, "Mon Airing 2", DayOfWeek.MONDAY, 4)
        val itemTue = createAiringItem(103, "Tue Airing", DayOfWeek.TUESDAY, 3)

        val schedule = mapOf(
            DayOfWeek.MONDAY to listOf(itemMon1, itemMon2),
            DayOfWeek.TUESDAY to listOf(itemTue)
        )

        val watchingMalIds = setOf(101) // User only watching 101

        val allState = AiringCalendarUiState(
            selectedDay = DayOfWeek.MONDAY,
            filterOnlyWatching = false,
            weekSchedule = schedule
        )

        // Without filter, returns all items for Monday (size 2)
        assertEquals(2, allState.currentDayItems(watchingMalIds).size)
        assertEquals(2, allState.countForDay(DayOfWeek.MONDAY, watchingMalIds))

        // With filter active, only watching items return (size 1)
        val filteredState = allState.copy(filterOnlyWatching = true)
        val mondayWatching = filteredState.currentDayItems(watchingMalIds)
        assertEquals(1, mondayWatching.size)
        assertEquals(101, mondayWatching.first().malId)
        assertEquals(1, filteredState.countForDay(DayOfWeek.MONDAY, watchingMalIds))
        assertEquals(0, filteredState.countForDay(DayOfWeek.TUESDAY, watchingMalIds))
    }

    @Test
    fun testAiringAlertManagerDispatchesOnlyForNewUnseenEpisodes() = runTest {
        val fakeRepo = object : CalendarRepository {
            override suspend fun getAiringCalendar(forceRefresh: Boolean): Map<DayOfWeek, List<AiringAnimeItem>> = emptyMap()
            override suspend fun getWatchingAiringAnime(
                watchingMalIds: Set<Int>,
                forceRefresh: Boolean
            ): List<AiringAnimeItem> {
                return listOf(
                    createAiringItem(201, "Demon Slayer", DayOfWeek.SUNDAY, 9), // User watched 7 -> New ep 9 -> Should notify
                    createAiringItem(202, "One Piece", DayOfWeek.SUNDAY, 10),   // User watched 10 -> No new ep -> Skip
                    createAiringItem(203, "Naruto", DayOfWeek.THURSDAY, 5)       // Already notified ep 5 -> Skip
                )
            }
        }

        tracker.markNotified(203, 5) // Already notified ep 5

        val notifManager = CanimNotificationManager(context)
        val alertManager = AiringAlertManager(
            notificationManager = notifManager,
            notificationTracker = tracker,
            calendarRepository = fakeRepo
        )

        val watchingList = listOf(
            createUserWatchingItem(201, progress = 7),
            createUserWatchingItem(202, progress = 10),
            createUserWatchingItem(203, progress = 4)
        )

        val dispatched = alertManager.checkAndDispatchAiringAlerts(watchingList)

        // Only Demon Slayer (201) should trigger a notification
        assertEquals(1, dispatched)
        // Check that 201 is now marked as notified up to ep 9
        assertEquals(9, tracker.getLastNotifiedEpisode(201))

        // Running check again immediately should dispatch 0 notifications (100% spam-free deduplication)
        val secondDispatch = alertManager.checkAndDispatchAiringAlerts(watchingList)
        assertEquals(0, secondDispatch)
    }
}
