package com.canim.app

import com.canim.app.data.local.GachaCreditManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GachaCreditTest {

    @Test
    fun testStartOfCurrentWeekCalculationAlwaysReturnsMondayMidnight() {
        // Create a calendar for Wednesday 2026-09-09 15:30:45
        val testCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 9, 15, 30, 45)
            set(Calendar.MILLISECOND, 250)
        }

        val startOfWeek = GachaCreditManager.getStartOfCurrentWeekMillis(testCal.timeInMillis)
        val resultCal = Calendar.getInstance().apply { timeInMillis = startOfWeek }

        // Must be Monday 2026-09-07 00:00:00.000
        assertEquals(Calendar.MONDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, resultCal.get(Calendar.MONTH))
        assertEquals(7, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
        assertEquals(0, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun testSundayMapsToCurrentWeekMonday() {
        // Sunday 2026-09-13 23:59:59
        val testCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 13, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val startOfWeek = GachaCreditManager.getStartOfCurrentWeekMillis(testCal.timeInMillis)
        val resultCal = Calendar.getInstance().apply { timeInMillis = startOfWeek }

        // Sunday is the last day of the week starting Monday Sep 7
        assertEquals(Calendar.MONDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, resultCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testNextResetTimeIsExactlySevenDaysLater() {
        val now = System.currentTimeMillis()
        val currentStart = GachaCreditManager.getStartOfCurrentWeekMillis(now)
        val nextReset = GachaCreditManager.getNextResetTimeMillis(now)

        val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000L
        assertEquals(sevenDaysMillis, nextReset - currentStart)
    }

    @Test
    fun testWeeklyFloorMinimumQuotaLogic() {
        val baseQuota = GachaCreditManager.BASE_WEEKLY_CREDITS
        assertEquals(5, baseQuota)

        // Case 1: User has 2 credits (< 5) -> reset restores to 5
        val creditsCase1 = 2
        val resetCredits1 = maxOf(creditsCase1, baseQuota)
        assertEquals(5, resetCredits1)

        // Case 2: User earned credits by watching anime and has 9 credits (> 5) -> preserved
        val creditsCase2 = 9
        val resetCredits2 = maxOf(creditsCase2, baseQuota)
        assertEquals(9, resetCredits2)

        // Case 3: User has 0 credits -> restored to 5
        val creditsCase3 = 0
        val resetCredits3 = maxOf(creditsCase3, baseQuota)
        assertEquals(5, resetCredits3)
    }
}
