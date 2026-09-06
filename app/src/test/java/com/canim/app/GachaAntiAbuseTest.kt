package com.canim.app

import android.content.Context
import com.canim.app.data.local.GachaCreditManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GachaAntiAbuseTest {

    private lateinit var context: Context
    private lateinit var manager: GachaCreditManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("canim_gacha_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        manager = GachaCreditManager(context)
    }

    @Test
    fun testInitialProgressIncreaseAwardsCredits() {
        val initialCredits = manager.getCredits()
        val mediaId = "anime_101"

        val awarded = manager.recordProgressAndAwardCredits(mediaId, 5)
        assertEquals(5, awarded)
        assertEquals(initialCredits + 5, manager.getCredits())
        assertEquals(5, manager.getHighestProgress(mediaId))
    }

    @Test
    fun testAbusiveDecreaseAndIncreaseDoesNotAwardDuplicateCredits() {
        val mediaId = "anime_202"

        val firstAward = manager.recordProgressAndAwardCredits(mediaId, 10)
        assertEquals(10, firstAward)
        val creditsAfterFirst = manager.getCredits()

        val decreaseAward = manager.recordProgressAndAwardCredits(mediaId, 5)
        assertEquals(0, decreaseAward)
        assertEquals(creditsAfterFirst, manager.getCredits())
        assertEquals(10, manager.getHighestProgress(mediaId))

        val reIncreaseAward = manager.recordProgressAndAwardCredits(mediaId, 10)
        assertEquals(0, reIncreaseAward)
        assertEquals(creditsAfterFirst, manager.getCredits())

        manager.recordProgressAndAwardCredits(mediaId, 1)
        val cycleAward = manager.recordProgressAndAwardCredits(mediaId, 9)
        assertEquals(0, cycleAward)
        assertEquals(creditsAfterFirst, manager.getCredits())

        val genuineAward = manager.recordProgressAndAwardCredits(mediaId, 12)
        assertEquals(2, genuineAward)
        assertEquals(creditsAfterFirst + 2, manager.getCredits())
        assertEquals(12, manager.getHighestProgress(mediaId))
    }

    @Test
    fun testBaselineInitializationDoesNotAwardCredits() {
        val mediaId = "anime_completed_303"
        val initialCredits = manager.getCredits()

        manager.initBaselineProgress(mediaId, 24)
        assertEquals(initialCredits, manager.getCredits())
        assertEquals(24, manager.getHighestProgress(mediaId))

        val awardAfterLowering = manager.recordProgressAndAwardCredits(mediaId, 20)
        assertEquals(0, awardAfterLowering)
        val awardAfterRestoring = manager.recordProgressAndAwardCredits(mediaId, 24)
        assertEquals(0, awardAfterRestoring)
        assertEquals(initialCredits, manager.getCredits())
    }

    @Test
    fun testConsistentAcrossAllMediaStatuses() {
        val statuses = listOf("watching_1", "completed_2", "on_hold_3", "dropped_4", "plan_to_watch_5")

        for (id in statuses) {
            val awarded1 = manager.recordProgressAndAwardCredits(id, 3)
            assertEquals(3, awarded1)

            val awardedLower = manager.recordProgressAndAwardCredits(id, 1)
            assertEquals(0, awardedLower)

            val awardedBack = manager.recordProgressAndAwardCredits(id, 3)
            assertEquals(0, awardedBack)
        }
    }
}
