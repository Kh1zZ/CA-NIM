package com.canim.app.domain.usecase

import com.canim.app.data.local.GachaCreditManager
import javax.inject.Inject

class ConsumeGachaCreditUseCase @Inject constructor(
    private val gachaCreditManager: GachaCreditManager
) {
    operator fun invoke(): Boolean = gachaCreditManager.consumeCredit()

    fun getCredits(): Int = gachaCreditManager.getCredits()

    fun setCredits(amount: Int) {
        gachaCreditManager.setCredits(amount)
    }

    fun initBaselineProgress(mediaId: String, progress: Int) {
        gachaCreditManager.initBaselineProgress(mediaId, progress)
    }

    fun observeCredits(): kotlinx.coroutines.flow.StateFlow<Int> = gachaCreditManager.creditsFlow

    fun onAnimeAdded(mediaId: String, progress: Int = 0): Int =
        gachaCreditManager.onAnimeAdded(mediaId, progress)

    fun recordProgressAndAwardCredits(mediaId: String, progress: Int): Int =
        gachaCreditManager.recordProgressAndAwardCredits(mediaId, progress)
}
