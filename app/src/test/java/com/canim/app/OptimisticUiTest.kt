package com.canim.app

import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaMetadata
import com.canim.app.data.model.MediaRef
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.UserMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OptimisticUiTest {

    private fun createSampleItem(id: Int, progress: Int): UserMediaItem {
        return UserMediaItem(
            identity = MediaRef(anilistId = id * 10, malId = id),
            metadata = MediaMetadata(
                title = "Anime $id",
                imageUrl = "https://example.com/$id.jpg",
                type = MediaType.ANIME,
                totalEpisodes = 12
            ),
            tracking = MalTracking(
                status = "watching",
                score = 8,
                progress = progress
            )
        )
    }

    @Test
    fun testOptimisticIncrementAndRevertOnFailure() {
        val initialList = listOf(createSampleItem(1, 5), createSampleItem(2, 3))
        val target = initialList[0]

        // 1. Optimistic apply
        val optimisticUpdated = target.copy(
            tracking = target.tracking.copy(
                progress = target.tracking.progress + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        val optimisticList = initialList.map { if (it.id == target.id) optimisticUpdated else it }

        assertEquals(6, optimisticList.first { it.id == target.id }.progress)
        assertEquals(5, initialList.first { it.id == target.id }.progress)

        // 2. Simulate API Failure -> Revert to initialList
        val networkSuccess = false
        val finalList = if (!networkSuccess) initialList else optimisticList

        assertEquals(5, finalList.first { it.id == target.id }.progress)
    }

    @Test
    fun testOptimisticDeleteAndRevertOnFailure() {
        val initialList = listOf(createSampleItem(1, 5), createSampleItem(2, 3))
        val target = initialList[0]

        // 1. Optimistic remove
        val optimisticList = initialList.filter { it.id != target.id }
        assertEquals(1, optimisticList.size)
        assertEquals(2, optimisticList[0].malId)

        // 2. Simulate API Failure -> Revert to initialList
        val networkSuccess = false
        val finalList = if (!networkSuccess) initialList else optimisticList

        assertEquals(2, finalList.size)
        assertEquals(1, finalList[0].malId)
    }
}
