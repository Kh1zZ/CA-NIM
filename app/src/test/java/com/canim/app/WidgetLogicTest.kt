package com.canim.app

import android.content.Context
import android.widget.RemoteViews
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.model.AiringAnimeItem
import com.canim.app.widget.TodayAiringWidgetProvider
import com.canim.app.widget.WatchingProgressWidgetProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class WidgetLogicTest {

    private lateinit var context: Context
    private lateinit var db: LocalDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        db = LocalDatabase(context)
        dao = LibraryDao(db)
        dao.clearAll()
    }

    private fun createEntry(
        malId: Int,
        title: String,
        status: String,
        progress: Int,
        totalEp: Int
    ): LibraryEntry {
        return LibraryEntry(
            malId = malId,
            mediaType = "ANIME",
            title = title,
            status = status,
            progress = progress,
            totalEpisodes = totalEp,
            localUpdatedAt = System.currentTimeMillis()
        )
    }

    @Test
    fun testWatchingWidgetSelectsActiveWatchingAnime() {
        // Insert one completed and one watching anime
        dao.upsertEntry(createEntry(101, "Completed Anime", "completed", 12, 12))
        dao.upsertEntry(createEntry(102, "Watching Anime", "watching", 4, 24))

        val entries = dao.getAllEntries("ANIME")
        val watchingItem = entries.firstOrNull { it.status.equals("watching", ignoreCase = true) }

        assertNotNull("Watching item should be found", watchingItem)
        assertEquals(102, watchingItem?.malId)
        assertEquals("Watching Anime", watchingItem?.title)
        assertEquals(4, watchingItem?.progress)
        assertEquals(24, watchingItem?.totalEpisodes)
    }

    @Test
    fun testWatchingWidgetEmptyStateWhenNoWatchingAnime() {
        val views = RemoteViews(context.packageName, R.layout.widget_watching_progress)
        WatchingProgressWidgetProvider.bindWatchingItem(context, views, null, 1)

        // Binding null entry should not throw and should handle empty gracefully
        assertNotNull(views)
    }

    @Test
    fun testWatchingWidgetRemoteViewsBinding() {
        val entry = createEntry(201, "Jujutsu Kaisen", "watching", 7, 24)
        val views = RemoteViews(context.packageName, R.layout.widget_watching_progress)

        WatchingProgressWidgetProvider.bindWatchingItem(context, views, entry, 1)
        assertNotNull(views)
    }

    @Test
    fun testTodayAiringWidgetBindingWithItems() {
        val views = RemoteViews(context.packageName, R.layout.widget_today_airing)
        val items = listOf(
            AiringAnimeItem(
                id = "ani_1",
                malId = 301,
                anilistId = 1301,
                title = "Frieren",
                titleEnglish = "Frieren",
                imageUrl = "",
                score = 9.1,
                episodes = 28,
                currentAiringEpisode = 18,
                airingDay = DayOfWeek.FRIDAY,
                airingTimeFormatted = "23:00"
            ),
            AiringAnimeItem(
                id = "ani_2",
                malId = 302,
                anilistId = 1302,
                title = "Solo Leveling",
                titleEnglish = "Solo Leveling",
                imageUrl = "",
                score = 8.5,
                episodes = 12,
                currentAiringEpisode = 5,
                airingDay = DayOfWeek.FRIDAY,
                airingTimeFormatted = "23:30"
            )
        )

        TodayAiringWidgetProvider.bindTodayAiring(context, views, DayOfWeek.FRIDAY, items, 2)
        assertNotNull(views)
    }

    @Test
    fun testTodayAiringWidgetEmptyBinding() {
        val views = RemoteViews(context.packageName, R.layout.widget_today_airing)
        TodayAiringWidgetProvider.bindTodayAiring(context, views, DayOfWeek.MONDAY, emptyList(), 3)
        assertNotNull(views)
    }
}
